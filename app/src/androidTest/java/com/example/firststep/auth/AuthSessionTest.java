package com.example.firststep.auth;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AuthSessionTest {
  @Test public void sessionIsEncryptedAndNumericUserIdHasStableAccountNamespace() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AuthSession session = new AuthSession(context);
    Assume.assumeTrue("Run account tests while signed out", "guest".equals(session.currentUserId()));
    String token = "test-session-" + java.util.UUID.randomUUID();
    try {
      JSONObject response = new JSONObject().put("access_token", token).put("user",
          new JSONObject().put("id", 987654321).put("nickname", "암호화 검증").put("provider", "DEVELOPMENT"));
      session.save(response);
      assertEquals("987654321", new AuthSession(context).currentUserId());
      assertTrue("Encrypted token must round-trip", java.security.MessageDigest.isEqual(
          token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
          new AuthSession(context).getAccessToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      assertEquals("암호화 검증", session.userNickname());
      assertEquals("DEVELOPMENT", session.provider());
      String namespace = AuthSession.namespace(context);
      assertTrue(namespace.matches("[0-9a-f]{64}"));
      assertEquals(namespace, AuthSession.namespace(context));
      String persisted = context.getSharedPreferences("firststep-auth", Context.MODE_PRIVATE).getAll().toString();
      assertFalse("Raw token must never be stored in preferences", persisted.contains(token));
      assertFalse("Session data should be encrypted", persisted.contains("암호화 검증"));
      context.getSharedPreferences("firststep-auth", Context.MODE_PRIVATE).edit().putString("sealed", "tampered").commit();
      assertEquals("guest", new AuthSession(context).currentUserId());
      assertEquals("", new AuthSession(context).getAccessToken());
    } finally {session.clear();}
    assertEquals("guest", AuthSession.namespace(context));
  }
}
