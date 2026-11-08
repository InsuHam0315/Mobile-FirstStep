package com.example.firststep.game.guide;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.example.firststep.*;
import com.example.firststep.game.content.*;
import com.example.firststep.game.ui.guide.GuideActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.hamcrest.Matcher;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Actual APK + approved bundled facts. Browser/asset errors are explicitly simulated. */
@RunWith(AndroidJUnit4.class)
public final class GuideSmokeTest {
  private final Context context = ApplicationProvider.getApplicationContext();
  private Instrumentation.ActivityMonitor monitor;
  private int initialAirplaneMode;

  private static void main(Runnable r) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
  }

  @Before
  public void offlinePrecondition() {
    initialAirplaneMode =
        Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
    String expected = InstrumentationRegistry.getArguments().getString("h10ExpectOffline");
    if (expected != null) {
      assertEquals("Explicit offline scenario only", "true", expected);
      assertEquals("Prepare offline outside instrumentation; never skip", 1, initialAirplaneMode);
    }
  }

  @After
  public void cleanup() {
    main(() -> GuideAdapters.setTestFactory(null));
    if (monitor != null) InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);
    assertEquals(
        "Tests must preserve prepared connectivity",
        initialAirplaneMode,
        Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
  }

  private static void await(BooleanSupplier condition) {
    long end = SystemClock.elapsedRealtime() + 10000;
    while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
    assertTrue("Expected H10 APK state", condition.getAsBoolean());
  }

  private static void panel(ActivityScenario<GuideActivity> s, String name) {
    await(
        () -> {
          boolean[] result = {false};
          s.onActivity(a -> result[0] = a.guideForTest() != null && name.equals(a.panelForTest()));
          return result[0];
        });
  }

  private void configure(Activity activity) {
    String argument = InstrumentationRegistry.getArguments().getString("h10Orientation");
    if (argument == null) return;
    int expected = Integer.parseInt(argument);
    activity.setRequestedOrientation(
        expected == 2
            ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
  }

  private ActivityScenario<GuideActivity> launch(Intent intent) {
    ActivityScenario<GuideActivity> s = ActivityScenario.launch(intent);
    s.onActivity(this::configure);
    if (InstrumentationRegistry.getArguments().containsKey("h10Orientation"))
      await(
          () -> {
            boolean[] result = {false};
            s.onActivity(
                a ->
                    result[0] =
                        a.getResources().getConfiguration().orientation
                            == Integer.parseInt(
                                InstrumentationRegistry.getArguments()
                                    .getString("h10Orientation", "1")));
            return result[0];
          });
    return s;
  }

  private ActivityScenario<GuideActivity> launch() {
    return launch(new Intent(context, GuideActivity.class));
  }

  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return isAssignableFrom(View.class);
      }

      public String getDescription() {
        return "reveal H10 control in its actual ScrollView";
      }

      public void perform(UiController ui, View v) {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        assertTrue(p instanceof ScrollView);
        ScrollView scroll = (ScrollView) p;
        Rect bounds = new Rect();
        v.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(v, bounds);
        int h = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        scroll.scrollTo(0, Math.max(0, bounds.centerY() - h / 2 - scroll.getPaddingTop()));
        ui.loopMainThreadForAtLeast(200);
        assertTrue(isDisplayingAtLeast(90).matches(v));
        if (v instanceof TextView) {
          TextView text = (TextView) v;
          assertNotNull(text.getLayout());
          for (int i = 0; i < text.getLayout().getLineCount(); i++)
            assertEquals(0, text.getLayout().getEllipsisCount(i));
          assertTrue(
              "Text height fits",
              text.getLayout().getHeight()
                  <= text.getHeight()
                      - text.getCompoundPaddingTop()
                      - text.getCompoundPaddingBottom());
        }
        if (v instanceof Button)
          assertTrue(
              "Touch height >=48dp",
              v.getHeight() >= 48 * v.getResources().getDisplayMetrics().density);
      }
    };
  }

  private static void tap(int id) {
    onView(withId(id)).perform(reveal(), click());
  }

  private static void choose(String q, String o) {
    onView(withTagValue(is("guide_option:" + q + ":" + o))).perform(reveal(), click());
  }

  private static void evidence(String name) throws Exception {
    main(
        () -> {
          boolean found = false;
          for (Activity a :
              ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
            if (!(a instanceof GuideActivity)) continue;
            found = true;
            String orientation = InstrumentationRegistry.getArguments().getString("h10Orientation");
            String scale = InstrumentationRegistry.getArguments().getString("h10FontScale");
            if (orientation != null)
              assertEquals(
                  "Actual capture orientation",
                  Integer.parseInt(orientation),
                  a.getResources().getConfiguration().orientation);
            if (scale != null)
              assertEquals(
                  "Actual capture font scale",
                  Float.parseFloat(scale),
                  a.getResources().getConfiguration().fontScale,
                  0.01f);
          }
          assertTrue("Capture must belong to actual guide Activity", found);
        });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    SystemClock.sleep(300);
    String prefix =
        InstrumentationRegistry.getArguments().getString("h10EvidencePrefix", "default");
    assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
    File dir =
        new File(
            ApplicationProvider.getApplicationContext().getExternalFilesDir(null), "h10-evidence");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(image);
    try (FileOutputStream out = new FileOutputStream(new File(dir, prefix + "-" + name + ".png"))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      image.recycle();
    }
  }

  private void environment(ActivityScenario<GuideActivity> s) {
    s.onActivity(
        a -> {
          String scale = InstrumentationRegistry.getArguments().getString("h10FontScale");
          if (scale != null)
            assertEquals(
                Float.parseFloat(scale), a.getResources().getConfiguration().fontScale, 0.01f);
          assertEquals(GuideDecoder.DATASET_ID, a.guideForTest().getDatasetId());
          assertEquals("unverified", a.guideForTest().getArrivalVerification());
        });
  }

  private SortedMap<String, String> databaseHashes() throws Exception {
    SortedMap<String, String> result = new TreeMap<>();
    File dir = new File(context.getDataDir(), "databases");
    File[] files = dir.listFiles();
    if (files == null) return result;
    for (File file : files) {
      if (!file.isFile()) continue;
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      try (InputStream in = new FileInputStream(file)) {
        byte[] bytes = new byte[4096];
        int n;
        while ((n = in.read(bytes)) != -1) hash.update(bytes, 0, n);
      }
      StringBuilder hex = new StringBuilder();
      for (byte b : hash.digest())
        hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      result.put(file.getName(), hex.toString());
    }
    return result;
  }

  @Test
  public void homeEntryLoadsRegisteredGuideWithoutStorageChange() throws Exception {
    try (ActivityScenario<MainActivity> home =
        ActivityScenario.launch(new Intent(context, MainActivity.class))) {
      home.onActivity(this::configure);
      await(
          () -> {
            boolean[] ready = {false};
            home.onActivity(a -> ready[0] = a.findViewById(R.id.game_guide_entry) != null);
            return ready[0];
          });
      SortedMap<String, String> before = databaseHashes();
      tap(R.id.game_guide_entry);
      await(
          () -> {
            boolean[] ready = {false};
            main(
                () -> {
                  for (Activity a :
                      ActivityLifecycleMonitorRegistry.getInstance()
                          .getActivitiesInStage(Stage.RESUMED))
                    if (a instanceof GuideActivity)
                      ready[0] = ((GuideActivity) a).guideForTest() != null;
                });
            return ready[0];
          });
      // The child needs its own request; the parent's orientation was not inherited in this run.
      main(
          () -> {
            for (Activity a :
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
              if (a instanceof GuideActivity) configure(a);
          });
      await(
          () -> {
            boolean[] ready = {false};
            main(
                () -> {
                  for (Activity a :
                      ActivityLifecycleMonitorRegistry.getInstance()
                          .getActivitiesInStage(Stage.RESUMED))
                    if (a instanceof GuideActivity) {
                      String expected =
                          InstrumentationRegistry.getArguments().getString("h10Orientation");
                      ready[0] =
                          ((GuideActivity) a).guideForTest() != null
                              && (expected == null
                                  || a.getResources().getConfiguration().orientation
                                      == Integer.parseInt(expected));
                    }
                });
            return ready[0];
          });
      onView(withId(R.id.game_guide_title)).check(matches(withText(R.string.game_guide_entry)));
      onView(withText("현장 도착 위치·반경은 아직 검증하지 않았습니다."))
          .perform(reveal())
          .check(matches(isDisplayed()));
      onView(withText("소프트웨어학과는 컴퓨터소프트웨어특성화대학의 학과 목록에서 확인할 수 있어요."))
          .perform(reveal())
          .check(matches(isDisplayed()));
      onView(withTagValue(is("guide_source:sw_location")))
          .perform(reveal())
          .check(matches(isCompletelyDisplayed()));
      evidence("official-guide-offline");
      tap(R.id.game_guide_close);
      assertEquals("Guide must not change/create database files", before, databaseHashes());
    }
  }

  @Test
  public void practiceWrongRetryCorrectAndRecreationStayReadOnly() throws Exception {
    SortedMap<String, String> before = databaseHashes();
    Intent intent =
        new Intent(context, GuideActivity.class)
            .putExtra("datasetId", "h04_fixture_v1")
            .putExtra("asset", "../../databases/firststep-progress.db");
    try (ActivityScenario<GuideActivity> s = launch(intent)) {
      panel(s, "GUIDE");
      environment(s);
      tap(R.id.game_guide_primary);
      choose("location", "library");
      s.recreate();
      panel(s, "PRACTICE");
      s.onActivity(a -> assertEquals("library", a.answersForTest().get("location")));
      choose("values", "communicate");
      choose("notice", "official");
      tap(R.id.game_guide_primary);
      panel(s, "RESULT");
      onView(withTagValue(is("guide_score")))
          .perform(reveal())
          .check(matches(withText("연습 정답: 2 / 3")));
      // Explicitly simulate loss of the retained model, then exercise the saved Bundle/new loader.
      // This is not a real operating-system process-death demonstration.
      GuideBundle[] previous = {null};
      s.onActivity(
          a -> {
            previous[0] = a.guideForTest();
            a.getViewModelStore().clear();
          });
      s.recreate();
      panel(s, "RESULT");
      s.onActivity(a -> assertNotSame(previous[0], a.guideForTest()));
      onView(withTagValue(is("guide_score")))
          .perform(reveal())
          .check(matches(withText("연습 정답: 2 / 3")));
      evidence("practice-restored-new-model-simulation");
      evidence("practice-wrong-no-reward");
      s.recreate();
      panel(s, "RESULT");
      onView(withTagValue(is("guide_score")))
          .perform(reveal())
          .check(matches(withText("연습 정답: 2 / 3")));
      tap(R.id.game_guide_read_again);
      panel(s, "GUIDE");
      tap(R.id.game_guide_primary);
      panel(s, "RESULT");
      tap(R.id.game_guide_primary);
      panel(s, "PRACTICE");
      s.onActivity(a -> assertTrue(a.answersForTest().isEmpty()));
      choose("location", "digital_first");
      choose("values", "communicate");
      choose("notice", "official");
      tap(R.id.game_guide_primary);
      panel(s, "RESULT");
      onView(withTagValue(is("guide_score")))
          .perform(reveal())
          .check(matches(withText("연습 정답: 3 / 3")));
      evidence("practice-correct-no-reward");
    }
    assertEquals("Practice must not change/create database files", before, databaseHashes());
  }

  @Test
  public void missingAndMalformedBundleFailClosedThenExplicitRetryLoads() throws Exception {
    SortedMap<String, String> before = databaseHashes();
    AtomicInteger loads = new AtomicInteger();
    main(
        () ->
            GuideAdapters.setTestFactory(
                app ->
                    new GuideAdapters.Environment(
                        () -> {
                          int attempt = loads.incrementAndGet();
                          if (attempt == 1) throw new IOException("Simulated absent asset");
                          if (attempt == 2)
                            return AssetGuideRepository.decodeJson(
                                GuideDecoder.DATASET_ID, "{\"version\":\"1\"}");
                          return new AssetGuideRepository(app).load();
                        },
                        source -> false,
                        true)));
    try (ActivityScenario<GuideActivity> s = launch()) {
      await(
          () -> {
            boolean[] result = {false};
            s.onActivity(a -> result[0] = a.unavailableForTest());
            return result[0];
          });
      s.onActivity(a -> assertNull(a.guideForTest()));
      assertEquals(1, loads.get());
      evidence("simulated-missing-asset-unavailable");
      s.recreate();
      tap(R.id.game_guide_primary);
      await(() -> loads.get() == 2);
      await(
          () -> {
            boolean[] result = {false};
            s.onActivity(a -> result[0] = a.unavailableForTest());
            return result[0];
          });
      s.onActivity(a -> assertNull(a.guideForTest()));
      evidence("simulated-malformed-asset-unavailable");
      tap(R.id.game_guide_primary);
      panel(s, "GUIDE");
      assertEquals(3, loads.get());
      environment(s);
    }
    assertEquals(before, databaseHashes());
  }

  @Test
  public void sourceIntentIsOfficialAndMissingHandlerPreservesChoices() throws Exception {
    SortedMap<String, String> before = databaseHashes();
    AtomicReference<Intent> captured = new AtomicReference<>();
    monitor =
        new Instrumentation.ActivityMonitor() {
          @Override
          public Instrumentation.ActivityResult onStartActivity(Intent intent) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
              captured.set(new Intent(intent));
              throw new ActivityNotFoundException("H10 explicitly simulated missing browser");
            }
            return null;
          }
        };
    InstrumentationRegistry.getInstrumentation().addMonitor(monitor);
    main(
        () ->
            GuideAdapters.setTestFactory(
                app ->
                    new GuideAdapters.Environment(
                        new AssetGuideRepository(app),
                        source -> GuideAdapters.openOfficialSource(app, source),
                        true)));
    try (ActivityScenario<GuideActivity> s = launch()) {
      panel(s, "GUIDE");
      tap(R.id.game_guide_primary);
      choose("location", "digital_first");
      tap(R.id.game_guide_read_again);
      panel(s, "GUIDE");
      onView(withTagValue(is("guide_source:sw_location"))).perform(reveal(), click());
      assertNotNull(captured.get());
      assertEquals(
          "https://www.kunsan.ac.kr/sw/index.kunsan?menuCd=DOM_000009601004000000",
          captured.get().getDataString());
      assertTrue(captured.get().hasCategory(Intent.CATEGORY_BROWSABLE));
      onView(withId(R.id.game_guide_status))
          .perform(reveal())
          .check(matches(withText(R.string.game_guide_source_failed)));
      onView(withId(R.id.game_guide_environment))
          .perform(reveal())
          .check(matches(withText(R.string.game_guide_test_environment)));
      evidence("simulated-missing-browser-official-source");
      s.recreate();
      panel(s, "GUIDE");
      tap(R.id.game_guide_primary);
      panel(s, "PRACTICE");
      s.onActivity(a -> assertEquals("digital_first", a.answersForTest().get("location")));
    }
    assertEquals(before, databaseHashes());
  }

  private String bundledJson() throws Exception {
    try (InputStream in = context.getAssets().open("campus_content/software_guide_v1.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static void reject(String id, String json) {
    try {
      AssetGuideRepository.decodeJson(id, json);
      fail("Malformed/unregistered content must fail");
    } catch (IOException expected) {
      /* executed rejection, never a skipped test */
    }
  }

  @Test
  public void actualAndroidParserRejectsMalformedAndUnregisteredBundle() throws Exception {
    String valid = bundledJson();
    assertEquals(
        3, AssetGuideRepository.decodeJson(GuideDecoder.DATASET_ID, valid).getQuestions().size());
    reject("h04_fixture_v1", valid);
    reject("../game_intro/catalog.json", valid);
    reject(GuideDecoder.DATASET_ID, valid + " garbage");
    reject(GuideDecoder.DATASET_ID, "[" + valid + "]");
    String duplicate = valid.replaceFirst("\"version\"\\s*:\\s*1", "\"version\":1,\"version\":1");
    assertNotEquals("Duplicate-key case must mutate the otherwise-valid bundle", valid, duplicate);
    reject(GuideDecoder.DATASET_ID, duplicate);
    StringBuilder deep = new StringBuilder();
    for (int i = 0; i < 4096; i++) deep.append('[');
    deep.append('0');
    for (int i = 0; i < 4096; i++) deep.append(']');
    reject(GuideDecoder.DATASET_ID, deep.toString());
    reject(
        GuideDecoder.DATASET_ID, valid.replaceFirst("\"version\"\\s*:\\s*1", "\"version\": 1.0"));
    JSONObject nullVersion = new JSONObject(valid);
    nullVersion.put("version", JSONObject.NULL);
    reject(GuideDecoder.DATASET_ID, nullVersion.toString());
    JSONObject unsafe = new JSONObject(valid);
    unsafe
        .getJSONArray("sources")
        .getJSONObject(0)
        .put("url", "https://www.kunsan.ac.kr.evil.example/sw/index.kunsan");
    reject(GuideDecoder.DATASET_ID, unsafe.toString());
    reject(GuideDecoder.DATASET_ID, "{'version': 1}");
    char[] oversized = new char[65537];
    Arrays.fill(oversized, ' ');
    reject(GuideDecoder.DATASET_ID, new String(oversized));
  }
}
