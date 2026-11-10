package com.example.firststep.game.sync;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.Source;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Android SDK transport against an explicitly provisioned local emulator pair. */
@RunWith(AndroidJUnit4.class)
public final class FirestoreSyncBackendEmulatorTest {
  private static final String PROJECT = "demo-firststep-progress-sdk";
  private static final String HOST = "10.0.2.2"; // Android emulator's alias for host loopback.
  private static final int AUTH_PORT = 19383;
  private static final int FIRESTORE_PORT = 18383;
  private static final String BUILDING = "sdk-building";
  private static final int TIMEOUT_SECONDS = 20;

  @Test
  public void googleOwnerWritesAndSecondSdkInstanceReadsUnderCandidateRules() throws Exception {
    String run = InstrumentationRegistry.getArguments().getString("firststepProgressSdkRun");
    assumeTrue("Requires the isolated progress SDK runner", run != null);
    assertTrue("Requires the debug APK", BuildConfig.DEBUG);
    assertTrue("Invalid synthetic fixture identifier", run.matches("[a-f0-9]{32}"));
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    String avd = InstrumentationRegistry.getArguments().getString("firststepProgressSdkAvd", "");
    assertTrue("Requires a disposable progress SDK AVD", avd.matches("FirstStep_ProgressSdk_[A-Za-z0-9_]+"));
    assertTrue("Wrong AVD", onlyLine(instrumentation, "getprop ro.boot.qemu.avd_name", avd));
    assertTrue("Expected Android user 0", onlyLine(instrumentation, "am get-current-user", "0"));
    assertEquals(UserHandle.getUserHandleForUid(0), Process.myUserHandle());
    Context context = instrumentation.getTargetContext();
    DatasetRegistry registry = AuthoredRouteTestRegistry.create(dataset(run), BUILDING);
    long introAt = 10_000L;
    long passAt = introAt + ProgressService.WAIT_MILLIS;
    Milestone intro = new Milestone(introAt, null);
    Milestone passed = new Milestone(introAt,
        new Milestone.Completion("sdk-first-pass", "sdk-content-v1", 2, passAt));
    Milestone late = new Milestone(introAt - 1,
        new Milestone.Completion("sdk-late-pass", "sdk-content-v1", 3, passAt + 1));
    Milestone merged = new Milestone(late.introDoneAtMillis, passed.completion);

    try (Client writer = new Client(context, run, "writer");
         Client reader = new Client(context, run, "reader");
         Client other = new Client(context, run, "other")) {
      SyncScope writerScope = writer.signIn(run, "owner");
      FirestoreSyncBackend writeBackend = writer.backend(registry);
      assertNull(await(writeBackend.read(writerScope), "missing document"));
      assertEquals(intro, await(writeBackend.merge(writerScope, intro), "intro write"));
      assertEquals(passed, await(writeBackend.merge(writerScope, passed), "first pass"));
      assertEquals(merged, await(writeBackend.merge(writerScope, late), "monotonic merge"));
      assertEquals(merged, await(writeBackend.merge(writerScope, late), "duplicate merge"));

      SyncScope readerScope = reader.signIn(run, "owner");
      assertTrue("Second client did not receive the same owner", writerScope.uid.equals(readerScope.uid));
      assertEquals(merged, await(reader.backend(registry).read(readerScope), "second SDK read"));

      String path = MilestoneDocument.path(writerScope);
      DocumentReference writerDocument = writer.firestore.document(path);
      Milestone removedPass = new Milestone(merged.introDoneAtMillis, null);
      assertDenied(writerDocument.set(MilestoneDocument.encode(removedPass)), "completion removal");
      SyncScope otherScope = other.signIn(run, "other");
      assertFalse("Fixture users must be distinct", writerScope.uid.equals(otherScope.uid));
      assertNull(await(other.backend(registry).read(otherScope), "other owner's empty document"));
      assertDenied(other.firestore.document(path).get(Source.SERVER), "cross-owner read");
      assertEquals(merged, await(writeBackend.read(writerScope), "unchanged owner document"));

      writer.owner.revoke();
      assertThrows(SessionOwner.StaleSession.class, () -> writeBackend.read(writerScope));
    }
  }

  private static String dataset(String run) {
    return "sdk-authored-" + run;
  }

  private static boolean onlyLine(Instrumentation instrumentation, String command, String expected)
      throws IOException {
    ParcelFileDescriptor output = instrumentation.getUiAutomation().executeShellCommand(command);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        new ParcelFileDescriptor.AutoCloseInputStream(output), StandardCharsets.UTF_8))) {
      return expected.equals(reader.readLine()) && reader.readLine() == null;
    }
  }

  private static final class Client implements AutoCloseable {
    final FirebaseApp app;
    final FirebaseAuth auth;
    final FirebaseFirestore firestore;
    final ConfirmedSessionOwner owner = new ConfirmedSessionOwner(PROJECT);

    Client(Context context, String run, String role) {
      app = FirebaseApp.initializeApp(context, new FirebaseOptions.Builder()
          .setProjectId(PROJECT)
          .setApplicationId("1:1234567890:android:" + run)
          .setApiKey("progress-sdk-local-only")
          .build(), "ProgressSdk-" + role + "-" + run);
      try {
        auth = FirebaseAuth.getInstance(app);
        auth.useEmulator(HOST, AUTH_PORT);
        firestore = FirebaseFirestore.getInstance(app);
        firestore.useEmulator(HOST, FIRESTORE_PORT);
        firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder(
            firestore.getFirestoreSettings()).setLocalCacheSettings(
                MemoryCacheSettings.newBuilder().build()).build());
      } catch (RuntimeException failure) {
        app.delete();
        throw new AssertionError("Local SDK setup failed (" + failure.getClass().getSimpleName() + ")");
      }
    }

    SyncScope signIn(String run, String label) throws Exception {
      String subject = "progress-sdk-" + run + "-" + label;
      String credential = new JSONObject()
          .put("sub", subject)
          .put("email", subject + "@example.invalid")
          .put("email_verified", true)
          .toString();
      FirebaseUser user = await(auth.signInWithCredential(GoogleAuthProvider.getCredential(
          credential, null)), "local Google sign-in").getUser();
      assertNotNull("Local account absent", user);
      assertEquals("google.com", await(user.getIdToken(false), "local provider claim").getSignInProvider());
      String uid = user.getUid();
      DocumentSnapshot profile = await(firestore.collection("users").document(uid).get(Source.SERVER),
          "private profile read");
      assertTrue("Preseeded profile missing", profile.exists());
      assertTrue("Profile UID mismatch", uid.equals(profile.getString("uid")));
      assertEquals("00123", profile.getString("studentNumber"));
      assertFalse(profile.getMetadata().isFromCache());
      assertFalse(profile.getMetadata().hasPendingWrites());
      owner.onAuthChanged(uid);
      owner.confirm(uid);
      return owner.capture(dataset(run), Progress.Mode.NORMAL, BUILDING);
    }

    FirestoreSyncBackend backend(DatasetRegistry registry) {
      return new FirestoreSyncBackend(firestore, registry, owner);
    }

    @Override public void close() throws Exception {
      owner.revoke();
      try {
        await(firestore.terminate(), "local client teardown");
      } finally {
        try {
          auth.signOut();
        } finally {
          app.delete();
        }
      }
    }
  }

  private static <T> T await(Task<T> task, String stage) throws InterruptedException {
    try {
      return Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException failure) {
      throw testFailure(stage, failure);
    }
  }

  private static <T> T await(CompletableFuture<T> future, String stage) throws InterruptedException {
    try {
      return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException failure) {
      throw testFailure(stage, failure);
    }
  }

  private static void assertDenied(Task<?> task, String stage) throws InterruptedException {
    try {
      Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
      fail(stage + " was allowed");
    } catch (ExecutionException failure) {
      if (!(failure.getCause() instanceof FirebaseFirestoreException)) {
        throw testFailure(stage, failure);
      }
      assertEquals(stage, FirebaseFirestoreException.Code.PERMISSION_DENIED,
          ((FirebaseFirestoreException) failure.getCause()).getCode());
    } catch (TimeoutException failure) {
      throw testFailure(stage, failure);
    }
  }

  private static AssertionError testFailure(String stage, Exception failure) {
    Throwable detail = failure instanceof ExecutionException && failure.getCause() != null
        ? failure.getCause() : failure;
    // Keep SDK error messages and credential material out of instrumentation output.
    return new AssertionError(stage + " failed (" + detail.getClass().getSimpleName() + ")");
  }
}
