package com.example.firststep.auth;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses the actual configured development API; no Google account dialog is automated. */
@RunWith(AndroidJUnit4.class)
public class DevelopmentAuthIntegrationTest {
  @Test public void developmentRegistrationLoginIdentityAndRevocationUseRealServer() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AuthSession session = new AuthSession(context);
    Assume.assumeTrue("Run server account tests while signed out", "guest".equals(session.currentUserId()));
    CloudApi publicApi = new CloudApi(context, "");
    JSONObject config = publicApi.request("GET", "/v1/config", null);
    Assume.assumeTrue("Development login must be explicitly enabled", config.optBoolean("development_login_enabled"));
    assertFalse(config.optBoolean("school_sso_enabled"));
    String username = "android_" + java.util.UUID.randomUUID().toString().replace("-", "");
    String password = java.util.UUID.randomUUID().toString();
    String token = "";
    try {
      JSONObject registered = publicApi.request("POST", "/v1/auth/development/register",
          new JSONObject().put("username", username).put("password", password).put("nickname", "서버 연결 검증"));
      session.save(registered);
      String userId = session.currentUserId();
      assertNotEquals("guest", userId);
      token = session.getAccessToken();
      JSONObject me = new CloudApi(context, token).request("GET", "/v1/me", null);
      JSONObject user = me.optJSONObject("user") == null ? me : me.getJSONObject("user");
      assertEquals(userId, String.valueOf(user.get("id")));
      assertEquals("DEVELOPMENT", user.getString("provider"));
      try {
        publicApi.request("POST", "/v1/auth/development/login", new JSONObject().put("username", username).put("password", password + "incorrect"));
        fail("Incorrect password must be rejected");
      } catch (CloudApi.ApiException error) {assertEquals(401, error.status);}
      new CloudApi(context, token).request("POST", "/v1/auth/logout", new JSONObject());
      try {new CloudApi(context, token).request("GET", "/v1/me", null);fail("Revoked session must not authorize requests");}
      catch (CloudApi.ApiException error) {assertEquals(401, error.status);}
      JSONObject signedIn = publicApi.request("POST", "/v1/auth/development/login", new JSONObject().put("username", username.toUpperCase(java.util.Locale.ROOT)).put("password", password));
      session.save(signedIn);token = session.getAccessToken();
      assertEquals(userId, session.currentUserId());
    } finally {
      if(!token.isEmpty())try{new CloudApi(context,token).request("POST","/v1/auth/logout",new JSONObject());}catch(CloudApi.ApiException ignored){}
      session.clear();
    }
  }
}
