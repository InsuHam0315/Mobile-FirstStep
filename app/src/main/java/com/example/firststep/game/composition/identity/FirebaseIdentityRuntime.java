package com.example.firststep.game.composition.identity;

import android.app.Activity;
import android.content.Context;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.auth.*;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

/** Process-shared named Firebase graph; no default-app auto initialization or legacy exchange. */
public final class FirebaseIdentityRuntime implements IdentityRuntime {
  private static IdentityRuntime instance;

  public static synchronized IdentityRuntime get(Context c) {
    if (instance == null) {
      try {
        instance = new FirebaseIdentityRuntime(c);
      } catch (RuntimeException invalidSetup) {
        instance = new UnconfiguredRuntime();
      }
    }
    return instance;
  }

  private final AuthPort auth;
  private final ProfilePort profiles;
  private final GoogleCredentialSource credentials;

  private FirebaseIdentityRuntime(Context c) {
    if (!BuildConfig.H07_CONFIGURED) {
      credentials = null;
      auth =
          new AuthPort() {
            public boolean isConfigured() {
              return false;
            }

            public String currentUid() {
              return null;
            }

            public Subscription observe(AuthObserver o) {
              o.onChanged(null);
              return () -> {};
            }

            public void signIn(boolean r, Completion cb) {
              cb.onResult(Failure.UNAVAILABLE);
            }

            public void signOut(Completion cb) {
              cb.onResult(null);
            }
          };
      profiles =
          new ProfilePort() {
            public void load(String u, ProfileCallback cb) {
              cb.onResult(null, Source.SERVER, Failure.UNAVAILABLE);
            }

            public void save(String u, Profile p, AuthPort.Completion cb) {
              cb.onResult(Failure.UNAVAILABLE);
            }
          };
      return;
    }
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.H07_APP_ID)
            .setApiKey(BuildConfig.H07_API_KEY)
            .setProjectId(BuildConfig.H07_PROJECT_ID)
            .build();
    FirebaseApp app;
    try {
      app = FirebaseApp.getInstance("FirstStepIdentity");
    } catch (IllegalStateException absent) {
      app = FirebaseApp.initializeApp(c.getApplicationContext(), options, "FirstStepIdentity");
    }
    FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(app);
    FirebaseFirestore db = FirebaseFirestore.getInstance(app);
    db.setFirestoreSettings(
        new FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build());
    credentials = new GoogleCredentialSource(c, BuildConfig.H07_WEB_CLIENT_ID);
    auth = new FirebaseAuthPort(firebaseAuth, credentials);
    profiles = new FirestoreProfilePort(db, firebaseAuth);
  }

  public AuthPort auth() {
    return auth;
  }

  public ProfilePort profiles() {
    return profiles;
  }

  public Kind kind() {
    return credentials == null ? Kind.UNCONFIGURED : Kind.LIVE;
  }

  public void attach(Activity a) {
    if (credentials != null) credentials.attach(a);
  }

  public void detach(Activity a) {
    if (credentials != null) credentials.detach(a);
  }

  public void
      close() {} // Shared SDK graph persists; each controller cancels its own observer on pause.
}
