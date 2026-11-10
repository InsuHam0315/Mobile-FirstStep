package com.example.firststep.game.composition.identity;

import com.example.firststep.game.auth.*;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

/** Only server reads/transactions; never a cached-profile completion or queued offline write. */
public final class FirestoreProfilePort implements ProfilePort, AutoCloseable {
  private volatile boolean closed;

  public void close() {
    closed = true;
  }

  private final FirebaseFirestore db;
  private final FirebaseAuth auth;
  private static final Set<String> FIELDS =
      new HashSet<>(
          Arrays.asList(
              "schemaVersion",
              "uid",
              "name",
              "studentNumber",
              "department",
              "createdAt",
              "updatedAt"));

  public FirestoreProfilePort(FirebaseFirestore db, FirebaseAuth auth) {
    this.db = db;
    this.auth = auth;
  }

  private boolean owns(String uid) {
    return !closed
        && uid != null
        && !uid.isEmpty()
        && !uid.contains("/")
        && auth.getCurrentUser() != null
        && uid.equals(auth.getCurrentUser().getUid());
  }

  public void load(String uid, ProfileCallback cb) {
    if (!owns(uid)) {
      cb.onResult(null, Source.SERVER, Failure.CANCELED);
      return;
    }
    db.collection("users")
        .document(uid)
        .get(com.google.firebase.firestore.Source.SERVER)
        .addOnCompleteListener(
            task -> {
              if (!owns(uid)) {
                cb.onResult(null, Source.SERVER, Failure.CANCELED);
                return;
              }
              if (!task.isSuccessful()) {
                cb.onResult(null, Source.SERVER, failure(task.getException()));
                return;
              }
              try {
                DocumentSnapshot s = task.getResult();
                if (s.getMetadata().isFromCache() || s.getMetadata().hasPendingWrites()) {
                  cb.onResult(null, Source.SERVER, Failure.UNAVAILABLE);
                  return;
                }
                cb.onResult(parse(s, uid), Source.SERVER, null);
              } catch (RuntimeException e) {
                cb.onResult(null, Source.SERVER, Failure.UNKNOWN);
              }
            });
  }

  public void save(String uid, Profile profile, AuthPort.Completion cb) {
    if (!owns(uid)) {
      cb.onResult(Failure.CANCELED);
      return;
    }
    if (profile == null || !profile.isComplete()) {
      cb.onResult(Failure.UNKNOWN);
      return;
    }
    DocumentReference ref = db.collection("users").document(uid);
    db.runTransaction(
            new TransactionOptions.Builder().setMaxAttempts(1).build(),
            tx -> {
              if (!owns(uid))
                throw new FirebaseFirestoreException(
                    "Session changed", FirebaseFirestoreException.Code.ABORTED);
              DocumentSnapshot old = tx.get(ref);
              Profile existing = parse(old, uid);
              if (existing != null && existing.isComplete()) return null;
              Map<String, Object> data = new HashMap<>();
              data.put("schemaVersion", 1);
              data.put("uid", uid);
              data.put("name", profile.getName());
              data.put("studentNumber", profile.getStudentNumber());
              data.put("department", profile.getDepartment());
              data.put(
                  "createdAt",
                  old.exists() ? old.getTimestamp("createdAt") : FieldValue.serverTimestamp());
              data.put("updatedAt", FieldValue.serverTimestamp());
              tx.set(ref, data);
              return null;
            })
        .addOnCompleteListener(
            task -> {
              if (!owns(uid)) {
                cb.onResult(Failure.CANCELED);
                return;
              }
              cb.onResult(task.isSuccessful() ? null : failure(task.getException()));
            });
  }

  private static String text(Map<String, Object> d, String key) {
    if (!d.containsKey(key)) return "";
    Object value = d.get(key);
    if (!(value instanceof String)) throw new IllegalArgumentException();
    return (String) value;
  }

  private static Profile parse(DocumentSnapshot s, String uid) {
    if (!s.exists()) return null;
    Map<String, Object> d = s.getData();
    if (d == null
        || !FIELDS.containsAll(d.keySet())
        || !uid.equals(d.get("uid"))
        || !Long.valueOf(1).equals(d.get("schemaVersion"))
        || !(d.get("createdAt") instanceof Timestamp)
        || !(d.get("updatedAt") instanceof Timestamp))
      throw new IllegalArgumentException("Invalid profile metadata");
    String name = text(d, "name"),
        student = text(d, "studentNumber"),
        department = text(d, "department");
    Profile p = new Profile(name, student, department);
    if (!name.equals(p.getName())
        || !student.equals(p.getStudentNumber())
        || !department.equals(p.getDepartment()))
      throw new IllegalArgumentException("Untrimmed profile");
    return p;
  }

  private static Failure failure(Exception error) {
    if (error instanceof FirebaseFirestoreException) {
      FirebaseFirestoreException.Code c = ((FirebaseFirestoreException) error).getCode();
      if (c == FirebaseFirestoreException.Code.PERMISSION_DENIED
          || c == FirebaseFirestoreException.Code.UNAUTHENTICATED) return Failure.PERMISSION_DENIED;
      if (c == FirebaseFirestoreException.Code.UNAVAILABLE
          || c == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
          || c == FirebaseFirestoreException.Code.ABORTED) return Failure.UNAVAILABLE;
    }
    return Failure.UNKNOWN;
  }
}
