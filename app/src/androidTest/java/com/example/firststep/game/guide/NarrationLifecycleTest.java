package com.example.firststep.game.guide;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.not;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import org.hamcrest.Matcher;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.Lifecycle;
import com.example.firststep.R;
import com.example.firststep.game.ui.guide.NarrationActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NarrationLifecycleTest {
  private static ViewAction reveal() {
    return new ViewAction() {
      public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
      public String getDescription() { return "reveal narration control in scroll view"; }
      public void perform(UiController ui, View view) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        assertTrue(parent instanceof ScrollView);
        ScrollView scroll = (ScrollView) parent;
        Rect bounds = new Rect();
        view.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(view, bounds);
        int height = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        scroll.scrollTo(0, Math.max(0, bounds.centerY() - height / 2 - scroll.getPaddingTop()));
        ui.loopMainThreadForAtLeast(200);
      }
    };
  }
  private static void tap(int id) { onView(withId(id)).perform(reveal(), click()); }
  private static String text(ActivityScenario<NarrationActivity> s, int id) {
    String[] value = {null}; s.onActivity(a -> value[0] = ((TextView)a.findViewById(id)).getText().toString()); return value[0];
  }
  private static void awaitCaption(ActivityScenario<NarrationActivity> scenario, String expected) {
    long deadline = SystemClock.elapsedRealtime() + 10000;
    while (SystemClock.elapsedRealtime() < deadline) {
      if (expected.equals(text(scenario, R.id.narration_caption))) return;
      SystemClock.sleep(50);
    }
    assertEquals("Timed out waiting for narration", expected, text(scenario, R.id.narration_caption));
  }
  @Test public void manualNavigationAndRecreationWithSpeechUnavailable() {
    Context context = ApplicationProvider.getApplicationContext();
    Intent intent = new Intent(context, NarrationActivity.class).putExtra("game_narration_debug_speech_mode", "absent");
    try (ActivityScenario<NarrationActivity> s = ActivityScenario.launch(intent)) {
      awaitCaption(s, "안녕하세요. 디지털정보관을 소개할 안내 요정이에요.");
      onView(withId(R.id.narration_previous)).check(matches(not(isEnabled())));
      assertEquals("안녕하세요. 디지털정보관을 소개할 안내 요정이에요.", text(s, R.id.narration_caption));
      tap(R.id.narration_replay);
      onView(withId(R.id.narration_status)).check(matches(withText(R.string.game_intro_speech_1)));
      tap(R.id.narration_next); tap(R.id.narration_next); tap(R.id.narration_next);
      assertEquals("학과 공식 위치 안내에는 디지털정보관 1층이 적혀 있어요.", text(s, R.id.narration_caption));
      onView(withId(R.id.narration_source)).check(matches(withText(R.string.game_guide_review_source)));
      s.moveToState(Lifecycle.State.CREATED);
      s.moveToState(Lifecycle.State.RESUMED);
      assertEquals("학과 공식 위치 안내에는 디지털정보관 1층이 적혀 있어요.", text(s, R.id.narration_caption));
      s.recreate();
      awaitCaption(s, "학과 공식 위치 안내에는 디지털정보관 1층이 적혀 있어요.");
      tap(R.id.narration_previous);
      assertEquals("소프트웨어학과는 컴퓨터소프트웨어특성화대학의 학과 목록에서 확인할 수 있어요.", text(s, R.id.narration_caption));
      tap(R.id.narration_skip);
    }
  }
  @Test public void speechErrorDoesNotAdvanceAndFinishRemainsExplicit() {
    Context context = ApplicationProvider.getApplicationContext();
    Intent intent = new Intent(context, NarrationActivity.class).putExtra("game_narration_debug_speech_mode", "play_error");
    try (ActivityScenario<NarrationActivity> s = ActivityScenario.launch(intent)) {
      awaitCaption(s, "안녕하세요. 디지털정보관을 소개할 안내 요정이에요.");
      tap(R.id.narration_replay);
      onView(withId(R.id.narration_status)).check(matches(withText(R.string.game_intro_speech_4)));
      assertEquals("안녕하세요. 디지털정보관을 소개할 안내 요정이에요.", text(s, R.id.narration_caption));
      for (int i = 0; i < 6; i++) tap(R.id.narration_next);
      onView(withId(R.id.narration_next)).check(matches(not(isEnabled())));
      assertEquals("방문하기 전에는 최신 안내를 다시 확인해 주세요.", text(s, R.id.narration_caption));
      tap(R.id.narration_finish);
    }
  }
}
