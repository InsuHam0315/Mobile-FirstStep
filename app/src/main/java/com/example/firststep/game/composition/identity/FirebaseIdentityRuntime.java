package com.example.firststep.game.composition.identity;

import android.app.Activity;
import android.content.Context;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.FirestoreSyncBackend;
import com.example.firststep.game.sync.SQLiteSyncStore;
import com.example.firststep.game.sync.SyncCoordinator;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Process-shared named Firebase graph; no default-app auto initialization or legacy exchange. */
public final class FirebaseIdentityRuntime implements IdentityRuntime {
  private static IdentityRuntime instance;

  public static synchronized IdentityRuntime get(Context c) {
    if (instance == null) {
      try {
        instance =
            BuildConfig.H07_CONFIGURED
                ? new FirebaseIdentityRuntime(c.getApplicationContext())
                : new UnconfiguredRuntime();
      } catch (RuntimeException invalidSetup) {
        instance = new UnconfiguredRuntime();
      }
    }
    return instance;
  }

  private final AuthPort auth;
  private final ProfilePort profiles;
  private final GoogleCredentialSource credentials;
  private final AccountProgressSession progressSession;
  private final AccountProgressAccess progress;

  private FirebaseIdentityRuntime(Context c) {
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
    progressSession =
        new AccountProgressSession(
            app.getOptions().getProjectId(),
            auth,
            () -> {
              FirebaseUser user = firebaseAuth.getCurrentUser();
              return user == null || user.isAnonymous() ? null : user.getUid();
            });
    DatasetRegistry registry = DatasetRegistry.production();
    Executor worker = Executors.newSingleThreadExecutor();
    SyncCoordinator sync =
        new SyncCoordinator(
            registry,
            progressSession,
            new SQLiteSyncStore(c, registry),
            new FirestoreSyncBackend(db, registry, progressSession),
            worker);
    progress = new AccountProgressAccess(registry, progressSession, sync, worker);
  }

  public AuthPort auth() {
    return auth;
  }

  public ProfilePort profiles() {
    return profiles;
  }

  @Override
  public ConfirmedOwnerObserver confirmedOwnerObserver() {
    return progressSession::onConfirmedOwner;
  }

  @Override
  public Optional<AccountProgressAccess> accountProgress() {
    return Optional.of(progress);
  }

  public Kind kind() {
    return Kind.LIVE;
  }

  public void attach(Activity a) {
    credentials.attach(a);
  }

  public void detach(Activity a) {
    credentials.detach(a);
  }

  public void close() {} // The process graph outlives its Activity clients.
}
