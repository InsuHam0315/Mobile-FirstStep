package com.example.firststep.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** A device-bound encrypted session. Passwords and raw Google credentials are never persisted. */
public final class AuthSession {
  private static final String ALIAS = "firststep.auth.v1";
  private static final Object LOCK = new Object();
  private final SharedPreferences preferences;

  public AuthSession(Context context) {
    preferences = context.getApplicationContext().getSharedPreferences("firststep-auth", Context.MODE_PRIVATE);
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
    return generator.generateKey();
  }

  private JSONObject read() {
    synchronized (LOCK) {
      String sealed = preferences.getString("sealed", null);
      if (sealed == null) return new JSONObject();
      try {
        String[] parts = sealed.split(":", -1);
        if (parts.length != 2) throw new IllegalStateException("Invalid encrypted session");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
      } catch (Exception error) {
        preferences.edit().clear().commit();
        return new JSONObject();
      }
    }
  }

  public void save(JSONObject response) throws Exception {
    JSONObject user = response.getJSONObject("user");
    Object identity = user.get("id");
    String id = String.valueOf(identity), token = response.getString("access_token");
    if (identity == JSONObject.NULL || id.trim().isEmpty() || id.length()>255 || "guest".equals(id)
        || token.isEmpty() || "null".equals(token) || token.length() > 16384)
      throw new IllegalArgumentException("Invalid session response");
    JSONObject session = new JSONObject().put("access_token", token)
        .put("user", new JSONObject().put("id", id).put("nickname", user.optString("nickname", "탐방자"))
            .put("provider", user.optString("provider", "development")));
    synchronized (LOCK) {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      String sealed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
          + Base64.encodeToString(cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
      if (!preferences.edit().clear().putString("sealed", sealed).commit())
        throw new java.io.IOException("Could not save session");
    }
  }

  public String currentUserId() {
    return snapshot().userId;
  }
  public String getAccessToken() { return snapshot().accessToken; }
  /** Owner and bearer must be captured together before any background account operation. */
  public Snapshot snapshot() {
    JSONObject data=read(),user=data.optJSONObject("user");String token=data.optString("access_token","");
    return user==null||token.isEmpty()?new Snapshot("guest",""):new Snapshot(user.optString("id","guest"),token);
  }
  public static final class Snapshot {
    public final String userId;
    public final String accessToken;
    private Snapshot(String userId,String accessToken){this.userId=userId;this.accessToken=accessToken;}
  }
  public String userNickname() { JSONObject user = read().optJSONObject("user"); return user == null ? "" : user.optString("nickname", ""); }
  public String provider() { JSONObject user = read().optJSONObject("user"); return user == null ? "guest" : user.optString("provider", "development"); }
  public void clear() { synchronized (LOCK) { preferences.edit().clear().commit(); } }
  public static String namespace(Context context) {
    String id = new AuthSession(context).currentUserId();
    if ("guest".equals(id)) return "guest";
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return result.toString();
    } catch (Exception impossible) { throw new IllegalStateException(impossible); }
  }
}
