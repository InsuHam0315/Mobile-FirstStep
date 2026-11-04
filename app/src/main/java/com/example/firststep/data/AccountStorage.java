package com.example.firststep.data;

import android.content.Context;
import com.example.firststep.auth.AuthSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stable account boundaries; email/nickname are never used as identifiers or filenames. */
public final class AccountStorage {
  private AccountStorage() {}
  static String namespace(String owner) {
    if ("guest".equals(owner)) return "guest";
    try {
      StringBuilder value = new StringBuilder();
      for (byte b : MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8)))
        value.append(String.format(Locale.ROOT, "%02x", b & 255));
      return value.toString();
    } catch (Exception impossible) { throw new IllegalStateException(impossible); }
  }
  public static String checkpointPreferences(Context context) {
    String scope = AuthSession.namespace(context);
    return "guest".equals(scope) ? "mission_checkpoints" : "mission_checkpoints-" + scope;
  }
}
