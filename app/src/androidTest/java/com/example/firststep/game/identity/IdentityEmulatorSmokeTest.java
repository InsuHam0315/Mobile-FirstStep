package com.example.firststep.game.identity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.identity.*;
import com.example.firststep.game.ui.identity.IdentityActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Real Firebase Android SDK against localhost-only emulators. Synthetic email credential, NOT
 * Google.
 */
@RunWith(AndroidJUnit4.class)
public final class IdentityEmulatorSmokeTest {
  private LocalRuntime runtime;
  private static final Profile DATA = new Profile("로컬 시험 사용자", "000123", "직접 입력 예시 학과");

  static void main(Runnable r) {
    if (Looper.myLooper() == Looper.getMainLooper()) r.run();
    else InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
  }

  @Before
  public void setup() throws Exception {
    runtime = new LocalRuntime();
    main(() -> H07VariantFactory.setTestFactory(() -> runtime));
  }

  @After
  public void cleanup() throws Exception {
    if (runtime == null) {
      main(() -> H07VariantFactory.setTestFactory(null));
      return;
    }
    main(
        () -> {
          H07VariantFactory.setTestFactory(null);
          runtime.authPort.close();
          runtime.profiles.close();
        });
    Exception failure = null;
    try {
      Tasks.await(runtime.db.disableNetwork(), 20, TimeUnit.SECONDS);
    } catch (Exception e) {
      failure = e;
    }
    try {
      // SDK channel shutdown can itself await up to 60s; do not impose a shorter fixture bound.
      Tasks.await(runtime.db.terminate(), 90, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (failure == null) failure = e;
      else failure.addSuppressed(e);
    } finally {
      runtime.app.delete();
    }
    if (failure != null) throw failure;
  }

  private ActivityScenario<IdentityActivity> launch() {
    ActivityScenario<IdentityActivity> s =
        ActivityScenario.launch(
            new Intent(ApplicationProvider.getApplicationContext(), IdentityActivity.class));
    int orientation =
        Integer.parseInt(InstrumentationRegistry.getArguments().getString("h07Orientation", "1"));
    s.onActivity(
        a ->
            a.setRequestedOrientation(
                orientation == 2
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    await(
        () -> {
          boolean[] ok = {false};
          s.onActivity(a -> ok[0] = a.getResources().getConfiguration().orientation == orientation);
          return ok[0];
        });
    boolean[] welcome = {false};
    s.onActivity(a -> welcome[0] = a.findViewById(R.id.game_identity_welcome).isShown());
    if (welcome[0]) tap(R.id.game_identity_welcome_login);
    return s;
  }

  private static void await(BooleanSupplier check) {
    long end = SystemClock.elapsedRealtime() + 90000;
    do {
      if (check.getAsBoolean()) return;
      SystemClock.sleep(50);
    } while (SystemClock.elapsedRealtime() < end);
    assertTrue("Expected H07 local emulator condition", check.getAsBoolean());
  }

  private SessionState state(ActivityScenario<IdentityActivity> s) {
    SessionState[] v = {null};
    s.onActivity(a -> v[0] = a.stateForTest());
    return v[0];
  }

  private void stage(ActivityScenario<IdentityActivity> s, SessionState.Stage value) {
    long end = SystemClock.elapsedRealtime() + 90000;
    while (state(s).getStage() != value && SystemClock.elapsedRealtime() < end)
      SystemClock.sleep(50);
    assertEquals(
        "H07 stage; failure="
            + state(s).getFailure()
            + "; saveFailure="
            + runtime.saveFailure
            + "; loadFailure="
            + runtime.loadFailure,
        value,
        state(s).getStage());
  }

  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return isAssignableFrom(View.class);
      }

      public String getDescription() {
        return "reveal actual H07 local emulator UI control";
      }

      public void perform(UiController ui, View v) {
        android.view.ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        assertTrue(p instanceof ScrollView);
        ScrollView scroll = (ScrollView) p;
        Rect b = new Rect();
        v.getDrawingRect(b);
        scroll.offsetDescendantRectToMyCoords(v, b);
        int h = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        scroll.scrollTo(0, Math.max(0, b.centerY() - h / 2 - scroll.getPaddingTop()));
        ui.loopMainThreadForAtLeast(200);
        assertTrue(isDisplayingAtLeast(90).matches(v));
      }
    };
  }

  private static void tap(int id) {
    onView(withId(id)).perform(reveal(), click());
  }

  private static void text(int id, String value) {
    onView(withId(id)).perform(reveal(), replaceText(value), closeSoftKeyboard());
  }

  private static void fill() {
    text(R.id.game_identity_name, DATA.getName());
    text(R.id.game_identity_student, DATA.getStudentNumber());
    text(R.id.game_identity_department, DATA.getDepartment());
  }

  private static void capture(ActivityScenario<IdentityActivity> s, String name) throws Exception {
    s.onActivity(
        a -> {
          assertEquals(
              Float.parseFloat(
                  InstrumentationRegistry.getArguments().getString("h07FontScale", "1.0")),
              a.getResources().getConfiguration().fontScale,
              0.01f);
        });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    String prefix =
        InstrumentationRegistry.getArguments().getString("h07EvidencePrefix", "default");
    assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
    File dir =
        new File(
            ApplicationProvider.getApplicationContext().getExternalFilesDir(null), "h07-evidence");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    Bitmap b = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(b);
    try (FileOutputStream out =
        new FileOutputStream(new File(dir, prefix + "-local-emulator-" + name + ".png"))) {
      assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      b.recycle();
    }
  }

  @Test
  public void sdkServerProfileSurvivesFreshControllerAndRejectsOtherUid() throws Exception {
    try (ActivityScenario<IdentityActivity> s = launch()) {
      stage(s, SessionState.Stage.LOGGED_OUT);
      tap(R.id.game_identity_login);
      stage(s, SessionState.Stage.FORM);
      fill();
      tap(R.id.game_identity_save);
      stage(s, SessionState.Stage.COMPLETE);
      assertEquals(DATA, state(s).getDraft());
      DocumentSnapshot stored =
          Tasks.await(
              runtime
                  .db
                  .collection("users")
                  .document(runtime.uidA)
                  .get(com.google.firebase.firestore.Source.SERVER),
              15,
              TimeUnit.SECONDS);
      assertEquals("000123", stored.getString("studentNumber"));
      assertFalse(stored.getMetadata().hasPendingWrites());
      capture(s, "server-confirmed-profile-fixture");
      try {
        Tasks.await(
            runtime
                .db
                .collection("users")
                .document(runtime.uidB)
                .get(com.google.firebase.firestore.Source.SERVER),
            15,
            TimeUnit.SECONDS);
        fail("Cross UID read must fail");
      } catch (ExecutionException denied) {
        assertTrue(denied.getCause() instanceof FirebaseFirestoreException);
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            ((FirebaseFirestoreException) denied.getCause()).getCode());
      }
    }
    try (ActivityScenario<IdentityActivity> s = launch()) {
      stage(s, SessionState.Stage.COMPLETE);
      assertEquals(DATA, state(s).getDraft());
      s.recreate();
      stage(s, SessionState.Stage.COMPLETE);
      assertEquals(DATA, state(s).getDraft());
    }
  }

  @Test
  public void sdkMalformedProfileCannotBeEditedOrOverwritten() throws Exception {
    boolean first = true;
    for (String field : Arrays.asList("name", "studentNumber", "department")) {
      seedMalformedLocalProfile(runtime.uidA, field);
      try (ActivityScenario<IdentityActivity> s = launch()) {
        if (first) {
          stage(s, SessionState.Stage.LOGGED_OUT);
          tap(R.id.game_identity_login);
          first = false;
        }
        stage(s, SessionState.Stage.PENDING_FAILURE);
        assertEquals(Failure.UNKNOWN, state(s).getFailure());
        assertNull(state(s).getDraft());
        assertFalse(state(s).canEdit());
        assertFalse(state(s).canSave());
        DocumentReference ref = runtime.db.collection("users").document(runtime.uidA);
        Map<String, Object> before =
            Tasks.await(ref.get(com.google.firebase.firestore.Source.SERVER), 15, TimeUnit.SECONDS)
                .getData();
        assertTrue(before.containsKey(field));
        assertNull(before.get(field));
        CountDownLatch done = new CountDownLatch(1);
        Failure[] failure = {null};
        main(
            () ->
                runtime.profiles.save(
                    runtime.uidA,
                    DATA,
                    f -> {
                      failure[0] = f;
                      done.countDown();
                    }));
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertEquals(Failure.UNKNOWN, failure[0]);
        assertEquals(
            before,
            Tasks.await(ref.get(com.google.firebase.firestore.Source.SERVER), 15, TimeUnit.SECONDS)
                .getData());
        tap(R.id.game_identity_retry);
        stage(s, SessionState.Stage.PENDING_FAILURE);
        assertNull(state(s).getDraft());
        capture(s, "malformed-profile-read-only-fixture");
      }
    }
  }

  // Admin fixture bypass ONLY for the fixed local demo emulator; never a live token/project.
  private static void seedMalformedLocalProfile(String uid, String nullField) throws Exception {
    org.json.JSONObject fields = new org.json.JSONObject();
    fields.put("schemaVersion", new org.json.JSONObject().put("integerValue", "1"));
    fields.put("uid", new org.json.JSONObject().put("stringValue", uid));
    fields.put("name", new org.json.JSONObject().put("stringValue", DATA.getName()));
    fields.put(
        "studentNumber", new org.json.JSONObject().put("stringValue", DATA.getStudentNumber()));
    fields.put("department", new org.json.JSONObject().put("stringValue", DATA.getDepartment()));
    fields.put(nullField, new org.json.JSONObject().put("nullValue", org.json.JSONObject.NULL));
    for (String key : Arrays.asList("createdAt", "updatedAt"))
      fields.put(key, new org.json.JSONObject().put("timestampValue", "2026-11-08T00:00:00Z"));
    java.net.HttpURLConnection c =
        (java.net.HttpURLConnection)
            new java.net.URL(
                    "http://10.0.2.2:18080/v1/projects/demo-firststep-h07/databases/(default)/documents/users/"
                        + uid)
                .openConnection();
    c.setRequestMethod("PATCH");
    c.setRequestProperty("Authorization", "Bearer owner");
    c.setRequestProperty("Content-Type", "application/json");
    c.setConnectTimeout(3000);
    c.setReadTimeout(3000);
    c.setDoOutput(true);
    try {
      try (OutputStream out = c.getOutputStream()) {
        out.write(
            new org.json.JSONObject()
                .put("fields", fields)
                .toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
      assertEquals("Local malformed fixture setup", 200, c.getResponseCode());
    } finally {
      c.disconnect();
    }
  }

  @Test
  public void sdkConcurrentCompleteProfileIsCanonicalWithoutOverwrite() throws Exception {
    Profile server = new Profile("기존 서버 예시", "000999", "다른 서버 예시 학과");
    try (ActivityScenario<IdentityActivity> s = launch()) {
      tap(R.id.game_identity_login);
      stage(s, SessionState.Stage.FORM);
      fill();
      Map<String, Object> data = new HashMap<>();
      data.put("schemaVersion", 1);
      data.put("uid", runtime.uidA);
      data.put("name", server.getName());
      data.put("studentNumber", server.getStudentNumber());
      data.put("department", server.getDepartment());
      data.put("createdAt", FieldValue.serverTimestamp());
      data.put("updatedAt", FieldValue.serverTimestamp());
      DocumentReference ref = runtime.db.collection("users").document(runtime.uidA);
      Tasks.await(ref.set(data), 15, TimeUnit.SECONDS);
      com.google.firebase.Timestamp created =
          Tasks.await(ref.get(com.google.firebase.firestore.Source.SERVER), 15, TimeUnit.SECONDS)
              .getTimestamp("createdAt");
      tap(R.id.game_identity_save);
      stage(s, SessionState.Stage.COMPLETE);
      assertEquals(server, state(s).getDraft());
      DocumentSnapshot after =
          Tasks.await(ref.get(com.google.firebase.firestore.Source.SERVER), 15, TimeUnit.SECONDS);
      assertEquals(server.getName(), after.getString("name"));
      assertEquals(created, after.getTimestamp("createdAt"));
      capture(s, "concurrent-server-canonical-no-overwrite-fixture");
    }
  }

  @Test
  public void sdkUnavailableTransportCannotCompleteAndRetryConfirmsServer() throws Exception {
    try (ActivityScenario<IdentityActivity> s = launch()) {
      tap(R.id.game_identity_login);
      stage(s, SessionState.Stage.FORM);
      fill();
      assertEquals(runtime.uidA, state(s).getUid());
      assertFalse(
          "Initial local profile must be missing",
          Tasks.await(
                  runtime
                      .db
                      .collection("users")
                      .document(runtime.uidA)
                      .get(com.google.firebase.firestore.Source.SERVER),
                  15,
                  TimeUnit.SECONDS)
              .exists());
      transport(false);
      tap(R.id.game_identity_save);
      stage(s, SessionState.Stage.PENDING_FAILURE);
      assertEquals(DATA, state(s).getDraft());
      capture(s, "offline-save-pending-fixture");
      System.out.println(
          "H07_LOCAL_OFFLINE saveAck="
              + runtime.saveAck
              + " saveFailure="
              + runtime.saveFailure
              + " loadFailure="
              + runtime.loadFailure
              + " stage="
              + state(s).getStage());
      assertFalse("Unavailable transport must not acknowledge a write", runtime.saveAck);
      transport(true);
      // Explicit local recovery fixture: reset SDK networking after the actual proxy outage.
      // This does not certify automatic reconnect timing or UI Retry alone restoring transport.
      Tasks.await(runtime.db.disableNetwork(), 20, TimeUnit.SECONDS);
      Tasks.await(runtime.db.enableNetwork(), 20, TimeUnit.SECONDS);
      java.net.HttpURLConnection probe =
          (java.net.HttpURLConnection)
              new java.net.URL(
                      "http://10.0.2.2:18080/v1/projects/demo-firststep-h07/databases/(default)/documents/users/"
                          + runtime.uidA)
                  .openConnection();
      probe.setRequestProperty("Authorization", "Bearer owner");
      probe.setConnectTimeout(3000);
      probe.setReadTimeout(3000);
      try {
        int status = probe.getResponseCode();
        System.out.println("H07_LOCAL_RECONNECT directServerStatus=" + status);
        assertEquals("Restored local server must have no failed-save document", 404, status);
      } finally {
        probe.disconnect();
      }
      // Exercise the actual product retry next, without adding an auxiliary SDK watch/listener.
      // COMPLETE still requires the adapter transaction AND controller's Source.SERVER read.
      long recoveryStart = SystemClock.elapsedRealtime();
      int attempts = 0;
      // gRPC's capped reconnect delay can be 120s + jitter. The SDK reset is not a connection ack.
      while (SystemClock.elapsedRealtime() - recoveryStart < 240000) {
        SessionState current = state(s);
        if (current.getStage() == SessionState.Stage.COMPLETE) break;
        assertEquals(runtime.uidA, current.getUid());
        assertEquals(DATA, current.getDraft());
        if (current.getStage() == SessionState.Stage.PENDING_FAILURE) {
          assertEquals(
              "Only transient network failure may be retried",
              Failure.UNAVAILABLE,
              current.getFailure());
          SystemClock.sleep(2000);
          tap(R.id.game_identity_retry);
          attempts++;
        } else {
          assertEquals(SessionState.Stage.SAVING, current.getStage());
          SystemClock.sleep(100);
        }
      }
      System.out.println(
          "H07_LOCAL_RECOVERY attempts="
              + attempts
              + " elapsedMs="
              + (SystemClock.elapsedRealtime() - recoveryStart)
              + " stage="
              + state(s).getStage()
              + " failure="
              + state(s).getFailure());
      assertEquals(SessionState.Stage.COMPLETE, state(s).getStage());
      assertEquals(DATA, state(s).getDraft());
    } finally {
      transport(true);
    }
  }

  @Test
  public void heldSdkCompletionCannotResurrectLoggedOutAccount() throws Exception {
    try (ActivityScenario<IdentityActivity> s = launch()) {
      main(() -> runtime.delivery.hold = true);
      tap(R.id.game_identity_login);
      await(() -> runtime.sdk.getCurrentUser() != null && !runtime.delivery.pending.isEmpty());
      assertEquals(SessionState.Stage.AUTH_BUSY, state(s).getStage());
      tap(R.id.game_identity_logout);
      assertNull(runtime.sdk.getCurrentUser());
      assertEquals(SessionState.Stage.AUTH_BUSY, state(s).getStage());
      main(() -> runtime.delivery.release());
      stage(s, SessionState.Stage.LOGGED_OUT);
      assertNull(runtime.sdk.getCurrentUser());
      main(() -> runtime.source.useB = true);
      tap(R.id.game_identity_login);
      stage(s, SessionState.Stage.FORM);
      assertEquals(runtime.uidB, state(s).getUid());
      assertTrue(state(s).getDraft().getName().isEmpty());
      capture(s, "logout-reselection-B-no-A-fixture");
    }
  }

  private static void transport(boolean online) throws Exception {
    java.net.HttpURLConnection c =
        (java.net.HttpURLConnection)
            new java.net.URL("http://10.0.2.2:18082/" + (online ? "online" : "offline"))
                .openConnection();
    c.setConnectTimeout(3000);
    c.setReadTimeout(3000);
    try {
      assertEquals(200, c.getResponseCode());
    } finally {
      c.disconnect();
    }
  }

  private static final class Delivery implements Executor {
    volatile boolean hold;
    final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    public void execute(Runnable r) {
      if (hold) pending.add(r);
      else new Handler(Looper.getMainLooper()).post(r);
    }

    void release() {
      hold = false;
      Runnable r;
      while ((r = pending.poll()) != null) r.run();
    }
  }

  private static final class LocalRuntime implements IdentityRuntime {
    final FirebaseApp app;
    final FirebaseAuth sdk;
    final FirebaseFirestore db;
    final FirebaseAuthPort authPort;
    final FirestoreProfilePort profiles;
    final Delivery delivery = new Delivery();
    final LocalCredentials source;
    final String uidA, uidB;

    LocalRuntime() throws Exception {
      String id = UUID.randomUUID().toString();
      Context c = ApplicationProvider.getApplicationContext();
      app =
          FirebaseApp.initializeApp(
              c,
              new FirebaseOptions.Builder()
                  .setApplicationId("1:1234567890:android:" + id.replace("-", ""))
                  .setApiKey("local-emulator-only-" + id)
                  .setProjectId("demo-firststep-h07")
                  .build(),
              "H07-local-" + id);
      sdk = FirebaseAuth.getInstance(app);
      sdk.useEmulator("10.0.2.2", 19099);
      db = FirebaseFirestore.getInstance(app);
      db.useEmulator("10.0.2.2", 18081);
      db.setFirestoreSettings(
          new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
              .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
              .build());
      String a = "a-" + id + "@example.invalid",
          b = "b-" + id + "@example.invalid",
          password = "local-fixture-password";
      uidA =
          Tasks.await(sdk.createUserWithEmailAndPassword(a, password), 20, TimeUnit.SECONDS)
              .getUser()
              .getUid();
      uidB =
          Tasks.await(sdk.createUserWithEmailAndPassword(b, password), 20, TimeUnit.SECONDS)
              .getUser()
              .getUid();
      assertNotEquals("Local fixture accounts must be distinct", uidA, uidB);
      sdk.signOut();
      source = new LocalCredentials(a, b, password);
      authPort = new FirebaseAuthPort(sdk, source, delivery);
      profiles = new FirestoreProfilePort(db, sdk);
    }

    public AuthPort auth() {
      return authPort;
    }

    volatile boolean saveAck;
    volatile Failure saveFailure, loadFailure;

    public ProfilePort profiles() {
      return new ProfilePort() {
        public void load(String uid, ProfileCallback cb) {
          profiles.load(
              uid,
              (p, source, f) -> {
                loadFailure = f;
                System.out.println("H07_LOCAL_PORT loadSource=" + source + " failure=" + f);
                cb.onResult(p, source, f);
              });
        }

        public void save(String uid, Profile p, AuthPort.Completion cb) {
          profiles.save(
              uid,
              p,
              f -> {
                saveAck = f == null;
                saveFailure = f;
                System.out.println("H07_LOCAL_PORT saveFailure=" + f);
                cb.onResult(f);
              });
        }
      };
    }

    public Kind kind() {
      return Kind.LOCAL_EMULATOR;
    }

    public void attach(Activity a) {}

    public void detach(Activity a) {}

    public void close() {}
  }

  private static final class LocalCredentials implements CredentialSource {
    final String a, b, password;
    boolean useB;

    LocalCredentials(String a, String b, String p) {
      this.a = a;
      this.b = b;
      password = p;
    }

    public void attach(Activity a) {}

    public void detach(Activity a) {}

    public void cancel() {}

    public void clear(AuthPort.Completion cb) {
      cb.onResult(null);
    }

    public void request(Callback cb) {
      cb.complete(EmailAuthProvider.getCredential(useB ? b : a, password), null);
    }
  }
}
