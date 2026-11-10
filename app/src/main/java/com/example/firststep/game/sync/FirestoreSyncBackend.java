package com.example.firststep.game.sync;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Uses the supplied Firestore instance. Project setup and rules belong to the account host. */
public final class FirestoreSyncBackend implements SyncBackend {
  private final FirebaseFirestore firestore;
  private final DatasetRegistry registry;
  private final SessionOwner owner;

  public FirestoreSyncBackend(FirebaseFirestore firestore, DatasetRegistry registry,
      SessionOwner owner) {
    this.firestore = Objects.requireNonNull(firestore);
    this.registry = Objects.requireNonNull(registry);
    this.owner = Objects.requireNonNull(owner);
  }

  @Override public CompletableFuture<Milestone> read(SyncScope scope) {
    validate(scope);
    Task<DocumentSnapshot> request = owner.whileCurrent(scope,
        () -> document(scope).get(Source.SERVER));
    return adapt(scope, request, FirestoreSyncBackend::decodeServer);
  }

  @Override public CompletableFuture<Milestone> merge(SyncScope scope, Milestone desired) {
    validate(scope);
    Objects.requireNonNull(desired, "desired");
    Task<Milestone> request = owner.whileCurrent(scope, () -> {
      DocumentReference reference = document(scope);
      return firestore.runTransaction(transaction -> {
        // The SDK can rerun this block. Keep the original reference and recheck the session.
        owner.whileCurrent(scope, () -> null);
        Milestone existing = decodeServer(transaction.get(reference));
        Milestone merged = Milestone.merge(existing, desired);
        return owner.whileCurrent(scope, () -> {
          if (!Objects.equals(existing, merged)) {
            transaction.set(reference, MilestoneDocument.encode(merged));
          }
          return merged;
        });
      });
    });
    return adapt(scope, request, value -> value);
  }

  private void validate(SyncScope scope) {
    registry.requireEligible(scope);
    if (!scope.projectId.equals(firestore.getApp().getOptions().getProjectId())) {
      throw new Failure(false);
    }
  }

  private DocumentReference document(SyncScope scope) {
    return firestore.document(MilestoneDocument.path(scope));
  }

  private static Milestone decodeServer(DocumentSnapshot snapshot) {
    if (snapshot.getMetadata().isFromCache() || snapshot.getMetadata().hasPendingWrites()) {
      throw new Failure(true);
    }
    if (!snapshot.exists()) return null;
    try {
      return MilestoneDocument.decode(snapshot.getData());
    } catch (IllegalArgumentException invalid) {
      throw new Failure(false);
    }
  }

  private <T> CompletableFuture<Milestone> adapt(SyncScope scope, Task<T> request,
      Function<T, Milestone> convert) {
    CompletableFuture<Milestone> result = new CompletableFuture<>();
    request.addOnCompleteListener(Runnable::run, task -> {
      try {
        Milestone value = owner.whileCurrent(scope, () -> {
          if (!task.isSuccessful()) throw classify(task);
          return convert.apply(task.getResult());
        });
        result.complete(value);
      } catch (RuntimeException error) {
        result.completeExceptionally(error);
      }
    });
    return result;
  }

  private static RuntimeException classify(Task<?> task) {
    Exception error = task.getException();
    // Transaction failures can wrap validation or stale-session exceptions.
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof Failure) return (Failure) cause;
      if (cause instanceof SessionOwner.StaleSession) return (SessionOwner.StaleSession) cause;
    }
    if (error instanceof FirebaseFirestoreException) {
      switch (((FirebaseFirestoreException) error).getCode()) {
        case ABORTED:
        case DEADLINE_EXCEEDED:
        case RESOURCE_EXHAUSTED:
        case UNAVAILABLE:
          return new Failure(true);
        default:
          return new Failure(false);
      }
    }
    return new Failure(task.isCanceled());
  }
}
