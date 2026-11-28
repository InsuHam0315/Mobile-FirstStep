package com.example.firststep.game.guide;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.firststep.R;
import com.example.firststep.game.auth.AuthPort;
import com.example.firststep.game.auth.Profile;
import com.example.firststep.game.auth.ProfilePort;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.home.account.AccountHomeTestRuntime;
import com.example.firststep.game.composition.identity.H07VariantFactory;
import com.example.firststep.game.composition.identity.IdentityRuntime;
import com.example.firststep.game.content.GuideDecoder;
import com.example.firststep.game.presentation.home.account.AccountHomeController.Reading;
import com.example.firststep.game.presentation.home.account.AccountHomeState;
import com.example.firststep.game.ui.guide.GuideActivity;
import com.example.firststep.game.ui.guide.NarrationActivity;
import com.example.firststep.game.ui.home.CampusHomeActivity;
import com.example.firststep.game.ui.identity.IdentityActivity;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Activities and bundled assets with account-free identity/Home dependencies. */
@RunWith(AndroidJUnit4.class)
public final class KoreanGuideRouteTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();

  @Test public void guestPracticeRecreationAndReturnKeepSoftwareStateSeparate() throws Exception {
    Map<String, String> before = databaseHashes();
    onMain(() -> { H07VariantFactory.setTestFactory(DisabledIdentity::new); return null; });
    try (ActivityScenario<IdentityActivity> identity =
             ActivityScenario.launch(new Intent(context, IdentityActivity.class));
         AutoCloseable children = trackGuideChildren()) {
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_identity_welcome).isShown());
      tap(R.id.game_identity_welcome_guest);
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_main_dashboard).isShown());
      tap(R.id.game_identity_open_campus);
      awaitActivity(CampusHomeActivity.class, a -> a.findViewById(R.id.game_campus_home_guide).isEnabled());
      tap(R.id.game_campus_home_guide);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_primary);
      guide(GuideDecoder.DATASET_ID, "PRACTICE");
      option("location", "library");
      tap(R.id.game_guide_quiz_back);
      GuideActivity software = guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_department_switch);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      onView(withId(R.id.game_guide_department)).perform(reveal())
          .check(matches(withText(R.string.game_korean_guide_department)));
      onView(withId(R.id.game_guide_title)).check(matches(withText(R.string.game_korean_guide_title)));
      onView(withText(R.string.game_korean_guide_office_notice)).perform(reveal())
          .check(matches(isDisplayed()));
      tapSection("ko_office_claim");
      onView(withText("학과 사무실 위치 안내에는 인문관 3층 1326호가 나와 있어요. 방문 전 최신 안내를 확인해 주세요."))
          .perform(reveal()).check(matches(isDisplayed()));
      onView(withTagValue(is("guide_source:ko_office"))).perform(reveal())
          .check(matches(isEnabled()));
      assertKoreanGuideScope(guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE"));
      tap(R.id.game_guide_primary);
      guide(GuideDecoder.KOREAN_DATASET_ID, "PRACTICE");
      option("ko_office_question", "ko_office_wrong1");
      GuideActivity first = guide(GuideDecoder.KOREAN_DATASET_ID, "PRACTICE");
      onMain(() -> { first.recreate(); return null; });
      GuideActivity recreated = awaitActivity(GuideActivity.class, a -> a != first
          && a.guideForTest() != null && "PRACTICE".equals(a.panelForTest()));
      assertEquals("ko_office_wrong1",
          onMain(() -> recreated.answersForTest().get("ko_office_question")));
      tap(R.id.game_guide_primary);
      option("ko_study_question", "ko_study_correct");
      tap(R.id.game_guide_primary);
      option("ko_info_question", "ko_info_correct");
      tap(R.id.game_guide_primary);
      GuideActivity result = guide(GuideDecoder.KOREAN_DATASET_ID, "RESULT");
      score(2);
      // Force a fresh model, then verify the saved Bundle restores this dataset and answers.
      onMain(() -> { result.getViewModelStore().clear(); result.recreate(); return null; });
      awaitActivity(GuideActivity.class, a -> a != result && a.guideForTest() != null
          && GuideDecoder.KOREAN_DATASET_ID.equals(a.guideForTest().getDatasetId())
          && "RESULT".equals(a.panelForTest()));
      score(2);
      tap(R.id.game_guide_primary);
      guide(GuideDecoder.KOREAN_DATASET_ID, "PRACTICE");
      option("ko_office_question", "ko_office_correct");
      tap(R.id.game_guide_primary);
      option("ko_study_question", "ko_study_correct");
      tap(R.id.game_guide_primary);
      option("ko_info_question", "ko_info_correct");
      tap(R.id.game_guide_primary);
      guide(GuideDecoder.KOREAN_DATASET_ID, "RESULT");
      score(3);
      tap(R.id.game_guide_quiz_back);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      tap(R.id.game_guide_department_switch);
      assertSame(software, guide(GuideDecoder.DATASET_ID, "GUIDE"));
      assertEquals(Collections.singletonMap("location", "library"),
          onMain(software::answersForTest));
      onView(withId(R.id.game_guide_demo_entry)).perform(reveal())
          .check(matches(isEnabled()));
      onView(withId(R.id.game_narration_entry)).perform(reveal()).check(matches(isEnabled()));
      tap(R.id.game_guide_primary);
      guide(GuideDecoder.DATASET_ID, "PRACTICE");
      onView(withTagValue(is("guide_option:location:library"))).perform(reveal())
          .check(matches(isChecked()));
      tap(R.id.game_guide_quiz_back);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_close);
      awaitActivity(CampusHomeActivity.class, a -> a.findViewById(R.id.game_campus_home_guide).isShown());
      androidx.test.espresso.Espresso.pressBack();
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_main_dashboard).isShown());
    } finally {
      onMain(() -> { H07VariantFactory.setTestFactory(null); return null; });
    }
    assertEquals("Guides and practice must leave all stored game data unchanged", before, databaseHashes());
  }

  @Test public void campusGuideCanOpenKoreanAndReturnThroughSoftwareGuide() throws Exception {
    Map<String, String> before = databaseHashes();
    try (AutoCloseable home = AccountHomeTestRuntime.install(
             () -> Reading.blocked(AccountHomeState.Status.UNAVAILABLE, Collections.emptyList(), true),
             false, System::currentTimeMillis);
         ActivityScenario<CampusHomeActivity> campus =
             ActivityScenario.launch(CampusHomeRoute.intent(context));
         AutoCloseable children = trackGuideChildren()) {
      awaitActivity(CampusHomeActivity.class, a -> a.findViewById(R.id.game_campus_home_guide).isEnabled());
      tap(R.id.game_campus_home_guide);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_department_switch);
      GuideActivity korean = guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      assertKoreanGuideScope(korean);
      tap(R.id.game_guide_close);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_close);
      awaitActivity(CampusHomeActivity.class, a -> true);
    }
    assertEquals(before, databaseHashes());
  }

  @Test public void guestKoreanHeroNarrationBackSkipAndFinishStayInKoreanPractice() throws Exception {
    Map<String, String> before = databaseHashes();
    onMain(() -> { H07VariantFactory.setTestFactory(DisabledIdentity::new); return null; });
    try (ActivityScenario<IdentityActivity> identity =
             ActivityScenario.launch(new Intent(context, IdentityActivity.class));
         AutoCloseable children = trackGuideChildren()) {
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_identity_welcome).isShown());
      tap(R.id.game_identity_welcome_guest);
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_main_dashboard).isShown());
      tap(R.id.game_identity_open_campus);
      awaitActivity(CampusHomeActivity.class, a -> a.findViewById(R.id.game_campus_home_guide).isEnabled());
      tap(R.id.game_campus_home_guide);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_department_switch);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      onView(withText(R.string.game_guide_hero)).perform(reveal()).check(matches(isDisplayed()));
      tap(R.id.game_narration_entry);
      koreanNarrationAt(1);
      onView(withId(R.id.narration_heading))
          .check(matches(withText(R.string.game_korean_narration_heading)));
      tap(R.id.narration_back);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");

      tap(R.id.game_narration_entry);
      koreanNarrationAt(1);
      tap(R.id.narration_replay);
      koreanNarrationAt(1);
      tap(R.id.narration_next);
      tap(R.id.narration_next);
      NarrationActivity first = koreanNarrationAt(3);
      onView(withId(R.id.narration_source_details)).perform(reveal())
          .check(matches(withSubstring("https://www.kunsan.ac.kr/index.kunsan?contentsSid=7359&sso=ok")));
      onMain(() -> { first.recreate(); return null; });
      awaitActivity(NarrationActivity.class, a -> a != first
          && context.getString(R.string.game_narration_position, 3, 7)
              .contentEquals(((TextView) a.findViewById(R.id.narration_position)).getText()));
      koreanNarrationAt(3);
      tap(R.id.narration_skip);
      GuideActivity practice = guide(GuideDecoder.KOREAN_DATASET_ID, "PRACTICE");
      onMain(() -> { practice.recreate(); return null; });
      awaitActivity(GuideActivity.class, a -> a != practice && a.guideForTest() != null
          && GuideDecoder.KOREAN_DATASET_ID.equals(a.guideForTest().getDatasetId())
          && "PRACTICE".equals(a.panelForTest()));
      option("ko_office_question", "ko_office_correct");
      tap(R.id.game_guide_quiz_back);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      tap(R.id.game_narration_entry);
      koreanNarrationAt(1);
      for (int page = 2; page <= 7; page++) {
        tap(R.id.narration_next);
        koreanNarrationAt(page);
      }
      onView(withId(R.id.narration_next)).check(matches(withEffectiveVisibility(Visibility.GONE)));
      tap(R.id.narration_finish);
      GuideActivity resumed = guide(GuideDecoder.KOREAN_DATASET_ID, "PRACTICE");
      assertEquals(Collections.singletonMap("ko_office_question", "ko_office_correct"),
          onMain(resumed::answersForTest));
      tap(R.id.game_guide_quiz_back);
      guide(GuideDecoder.KOREAN_DATASET_ID, "GUIDE");
      tap(R.id.game_guide_department_switch);
      guide(GuideDecoder.DATASET_ID, "GUIDE");
      tap(R.id.game_guide_close);
      awaitActivity(CampusHomeActivity.class, a -> a.findViewById(R.id.game_campus_home_guide).isShown());
      androidx.test.espresso.Espresso.pressBack();
      awaitActivity(IdentityActivity.class, a -> a.findViewById(R.id.game_main_dashboard).isShown());
    } finally {
      onMain(() -> { H07VariantFactory.setTestFactory(null); return null; });
    }
    assertEquals(before, databaseHashes());
  }

  @Test public void koreanSpeechUnavailableKeepsManualCaptionAndKoreanSource() throws Exception {
    Intent intent = NarrationActivity.koreanIntent(context)
        .putExtra("game_narration_debug_speech_mode", "absent");
    try (ActivityScenario<NarrationActivity> screen = ActivityScenario.launch(intent)) {
      koreanNarrationAt(1);
      tap(R.id.narration_replay);
      onView(withId(R.id.narration_status)).perform(reveal())
          .check(matches(withText(R.string.game_intro_speech_1)));
      koreanNarrationAt(1);
      tap(R.id.narration_next);
      tap(R.id.narration_next);
      tap(R.id.narration_next);
      koreanNarrationAt(4);
      onView(withId(R.id.narration_source_details)).perform(reveal())
          .check(matches(withSubstring("https://www.kunsan.ac.kr/korean/index.kunsan?menuCd=DOM_000001101004000000")));
      screen.recreate();
      koreanNarrationAt(4);
    }
  }

  private NarrationActivity koreanNarrationAt(int page) throws Exception {
    NarrationActivity activity = awaitActivity(NarrationActivity.class,
        a -> context.getString(R.string.game_narration_position, page, 7)
            .contentEquals(((TextView) a.findViewById(R.id.narration_position)).getText()));
    onMain(() -> {
      assertEquals(context.getString(R.string.game_korean_narration_heading),
          ((TextView) activity.findViewById(R.id.narration_heading)).getText().toString());
      String caption = ((TextView) activity.findViewById(R.id.narration_caption)).getText().toString();
      assertFalse(caption.contains("소프트웨어"));
      assertFalse(caption.contains("디지털정보관"));
      if (page == 1) {
        assertTrue(caption.contains("국어국문학과"));
        assertTrue(caption.contains("강린"));
      }
      return null;
    });
    return activity;
  }

  private void assertKoreanGuideScope(GuideActivity activity) throws Exception {
    onMain(() -> {
      assertEquals(View.GONE, activity.findViewById(R.id.game_guide_demo_entry).getVisibility());
      assertTrue(activity.findViewById(R.id.game_narration_entry).isEnabled());
      assertEquals("humanities_korean", activity.guideForTest().getTargetId());
      assertEquals("unverified", activity.guideForTest().getArrivalVerification());
      assertEquals(3, activity.guideForTest().getQuestions().size());
      return null;
    });
  }

  private GuideActivity guide(String dataset, String panel) throws Exception {
    return awaitActivity(GuideActivity.class, activity -> activity.guideForTest() != null
        && dataset.equals(activity.guideForTest().getDatasetId())
        && panel.equals(activity.panelForTest()));
  }

  private <T extends Activity> T awaitActivity(Class<T> type, Predicate<T> condition) throws Exception {
    int orientation = Integer.parseInt(InstrumentationRegistry.getArguments()
        .getString("koreanGuideOrientation", "1"));
    assertTrue(orientation == 1 || orientation == 2);
    long deadline = SystemClock.elapsedRealtime() + 10000;
    do {
      T found = onMain(() -> {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)) {
          if (!type.isInstance(activity)) continue;
          if (activity.getResources().getConfiguration().orientation != orientation) {
            activity.setRequestedOrientation(orientation == 2
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            continue;
          }
          if (condition.test(type.cast(activity))) return type.cast(activity);
        }
        return null;
      });
      if (found != null) return found;
      SystemClock.sleep(50);
    } while (SystemClock.elapsedRealtime() < deadline);
    throw new AssertionError("Expected " + type.getSimpleName() + " state was not reached");
  }

  private static void tap(int id) { onView(withId(id)).perform(reveal(), click()); }
  private static void tapSection(String claim) {
    onView(withTagValue(is("guide_section:" + claim))).perform(reveal(), click());
  }
  private static void option(String question, String option) {
    onView(withTagValue(is("guide_option:" + question + ":" + option))).perform(reveal(), click());
  }
  private void score(int correct) {
    onView(withTagValue(is("guide_score"))).perform(reveal())
        .check(matches(withText(context.getString(R.string.game_guide_score, correct, 3))));
  }

  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
      public String getDescription() { return "Reveal guide content or fixed control"; }
      public void perform(UiController ui, View view) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        if (parent instanceof ScrollView) {
          ScrollView scroll = (ScrollView) parent;
          Rect bounds = new Rect();
          view.getDrawingRect(bounds);
          scroll.offsetDescendantRectToMyCoords(view, bounds);
          int height = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
          scroll.scrollTo(0, Math.max(0, bounds.centerY() - height / 2 - scroll.getPaddingTop()));
        }
        ui.loopMainThreadForAtLeast(200);
        assertTrue("Control must be reachable", isDisplayingAtLeast(90).matches(view));
      }
    };
  }

  private AutoCloseable trackGuideChildren() throws Exception {
    Set<Activity> guides = new LinkedHashSet<>();
    ActivityLifecycleCallback observer = (activity, stage) -> {
      if (!(activity instanceof GuideActivity) && !(activity instanceof NarrationActivity)
          && !(activity instanceof CampusHomeActivity)) return;
      if (stage == Stage.PRE_ON_CREATE) guides.add(activity);
      if (stage == Stage.DESTROYED) guides.remove(activity);
    };
    onMain(() -> {
      ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(observer);
      return null;
    });
    return () -> {
      onMain(() -> {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(observer);
        for (Activity activity : guides) {
          if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish();
        }
        return null;
      });
      instrumentation.waitForIdleSync();
    };
  }

  private <T> T onMain(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    instrumentation.runOnMainSync(task);
    return task.get(5, TimeUnit.SECONDS);
  }

  private Map<String, String> databaseHashes() throws Exception {
    Map<String, String> hashes = new TreeMap<>();
    File[] files = new File(context.getDataDir(), "databases").listFiles();
    if (files == null) return hashes;
    for (File file : files) {
      if (!file.isFile()) continue;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (FileInputStream input = new FileInputStream(file)) {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
      }
      hashes.put(file.getName(), android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP));
    }
    return hashes;
  }

  private static final class DisabledIdentity implements IdentityRuntime {
    private final AuthPort auth = new AuthPort() {
      public boolean isConfigured() { return false; }
      public String currentUid() { return null; }
      public Subscription observe(AuthObserver observer) { observer.onChanged(null); return () -> {}; }
      public void signIn(boolean reselect, Completion callback) { throw new AssertionError("Unexpected sign-in"); }
      public void signOut(Completion callback) { throw new AssertionError("Unexpected sign-out"); }
    };
    public AuthPort auth() { return auth; }
    public ProfilePort profiles() {
      return new ProfilePort() {
        public void load(String uid, ProfileCallback callback) { throw new AssertionError("Unexpected profile read"); }
        public void save(String uid, Profile profile, AuthPort.Completion callback) {
          throw new AssertionError("Unexpected profile write");
        }
      };
    }
    public Kind kind() { return Kind.FAKE; }
    public void attach(Activity activity) {}
    public void detach(Activity activity) {}
    public void close() {}
  }
}
