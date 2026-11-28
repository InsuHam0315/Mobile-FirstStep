package com.example.firststep.game.composition.home.entry;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.firststep.game.auth.Profile;
import com.example.firststep.game.presentation.home.entry.CampusChoice;
import java.util.Map;
import java.util.Objects;

/** Local-only selections. All methods are synchronous and must run on a worker thread. */
public final class LocalCampusChoices {
  private static final String FILE = "local_campus_choices";
  private static final Object LOCK = new Object();
  private final SharedPreferences preferences;

  public LocalCampusChoices(Context context) {
    preferences =
        Objects.requireNonNull(context)
            .getApplicationContext()
            .getSharedPreferences(FILE, Context.MODE_PRIVATE);
  }

  /** Null identifies the guest. Invalid UIDs and malformed stored values are rejected. */
  public CampusChoice load(String uidOrNull) {
    String prefix = prefix(uidOrNull);
    synchronized (LOCK) {
      Map<String, ?> values = preferences.getAll();
      return new CampusChoice(
          read(values, prefix + "college", 120), read(values, prefix + "department", 120));
    }
  }

  public boolean save(String uidOrNull, CampusChoice choice) {
    String prefix = prefix(uidOrNull);
    if (choice == null) throw new IllegalArgumentException("Choice is required");
    synchronized (LOCK) {
      return preferences.edit()
          .putString(prefix + "college", choice.getCollege())
          .putString(prefix + "department", choice.getDepartment())
          .commit();
    }
  }

  public Profile loadGuestProfile() {
    synchronized (LOCK) {
      Map<String, ?> values = preferences.getAll();
      return new Profile(
          read(values, "guest.profile.name", 80),
          read(values, "guest.profile.studentNumber", 40),
          read(values, "guest.profile.department", 120));
    }
  }

  /** Saves matching guest profile and campus choice in one synchronous commit. */
  public boolean saveGuest(Profile profile, CampusChoice choice) {
    if (profile == null) throw new IllegalArgumentException("Profile is required");
    if (choice == null) throw new IllegalArgumentException("Choice is required");
    checkLength(profile.getName(), 80);
    checkLength(profile.getStudentNumber(), 40);
    checkLength(profile.getDepartment(), 120);
    if (!choice.getDepartment().equals(profile.getDepartment())) {
      throw new IllegalArgumentException("Departments must match");
    }
    synchronized (LOCK) {
      return preferences.edit()
          .putString("guest.choice.college", choice.getCollege())
          .putString("guest.choice.department", choice.getDepartment())
          .putString("guest.profile.name", profile.getName())
          .putString("guest.profile.studentNumber", profile.getStudentNumber())
          .putString("guest.profile.department", profile.getDepartment())
          .commit();
    }
  }

  public boolean saveGuestProfile(Profile profile) {
    if (profile == null) throw new IllegalArgumentException("Profile is required");
    checkLength(profile.getName(), 80);
    checkLength(profile.getStudentNumber(), 40);
    checkLength(profile.getDepartment(), 120);
    synchronized (LOCK) {
      return preferences.edit()
          .putString("guest.profile.name", profile.getName())
          .putString("guest.profile.studentNumber", profile.getStudentNumber())
          .putString("guest.profile.department", profile.getDepartment())
          .commit();
    }
  }

  private static String read(Map<String, ?> values, String key, int max) {
    if (!values.containsKey(key)) return "";
    Object value = values.get(key);
    if (!(value instanceof String)) throw new IllegalStateException("Malformed local campus data");
    String string = (String) value;
    if (string.length() > max) throw new IllegalStateException("Oversized local campus data");
    return string;
  }

  private static void checkLength(String value, int max) {
    if (value.length() > max) throw new IllegalArgumentException("Profile field is too long");
  }

  private static String prefix(String uid) {
    if (uid == null) return "guest.choice.";
    if (uid.isEmpty() || uid.length() > 128 || !uid.equals(uid.trim())) {
      throw new IllegalArgumentException("Invalid UID");
    }
    StringBuilder encoded = new StringBuilder("account.");
    for (int i = 0; i < uid.length(); i++) {
      char c = uid.charAt(i);
      if (Character.isISOControl(c) || Character.isSurrogate(c) || Character.isWhitespace(c)) {
        throw new IllegalArgumentException("Invalid UID");
      }
      String hex = Integer.toHexString(c);
      for (int j = hex.length(); j < 4; j++) encoded.append('0');
      encoded.append(hex);
    }
    return encoded.append(".choice.").toString();
  }
}
