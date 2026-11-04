package com.example.firststep;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.data.AppMode;
import com.example.firststep.data.MissionCompletion;
import com.example.firststep.data.MissionRepository;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Real camera capture plus explicit light/voice fallback inputs; never simulates GPS or tracked AR.
 */
@RunWith(AndroidJUnit4.class)
public class ThreeMissionFlowTest {
  @Test
  public void demoPhotoFallbackRestoresAndPersistsWithoutCameraCapture() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    MissionRepository repo = MissionRepository.getInstance(context);
    int fieldBefore = records(repo, AppMode.FIELD).size();
    MissionCheckpointStore.clearMode(context, true);
    CountDownLatch reset = new CountDownLatch(1);
    repo.resetProgress(AppMode.DEMO, ok -> reset.countDown());
    assertTrue(reset.await(10, TimeUnit.SECONDS));
    repo.setNickname("대체 이미지 검증");
    repo.setMode(AppMode.DEMO);
    MissionCheckpointStore store = new MissionCheckpointStore(context, true, "M01");
    MissionCheckpointStore.State checkpoint = new MissionCheckpointStore.State();
    checkpoint.stage = "GAME";
    store.save(checkpoint);
    try (androidx.test.core.app.ActivityScenario<MissionActivity> scenario =
        androidx.test.core.app.ActivityScenario.launch(new android.content.Intent(context, MissionActivity.class)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("missionIndex", 0).putExtra("demo", true))) {
    waitFor(withText("테스트 이미지로 진행"));
    clickText("테스트 이미지로 진행");
    waitFor(withText("테스트 이미지 확인"));
    scenario.recreate();
    waitFor(withText("테스트 이미지 확인"));
    assertEquals("DEMO_PHOTO", store.load().completion);
    clickText("사진 확인하고 계속");
    clickText("수업과 학습");
    waitFor(withText("기억 조각을 찾았어요"));
    List<MissionCompletion> saved = records(repo, AppMode.DEMO);
    assertEquals(1, saved.size());
    assertEquals("DEMO_PHOTO", saved.get(0).completionMethod);
    assertTrue(new File(saved.get(0).photoUri).isFile());
    assertNotNull(BitmapFactory.decodeFile(saved.get(0).photoUri));
    assertEquals(fieldBefore, records(repo, AppMode.FIELD).size());
    }
    try (androidx.test.core.app.ActivityScenario<MainActivity> scenario =
        androidx.test.core.app.ActivityScenario.launch(new android.content.Intent(context, MainActivity.class)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))) {
    waitFor(withText(containsString("1 / 5 완료")));
    onView(allOf(withText("시설 도감"), withParent(withId(R.id.navigation)))).perform(click());
    waitFor(withText(containsString("테스트 이미지 · 실제 촬영 아님")));
    }
  }

  @Test
  public void cameraLightAndVoicePersistSeparatelyFromFieldAfterRelaunch() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    MissionRepository repo = MissionRepository.getInstance(context);
    repo.setNickname("기능 검증");
    repo.setMode(AppMode.DEMO);
    MissionCheckpointStore.clearMode(context, true);
    CountDownLatch reset = new CountDownLatch(1);
    repo.resetProgress(AppMode.DEMO, ok -> reset.countDown());
    assertTrue("Demo reset timed out", reset.await(10, TimeUnit.SECONDS));

    if (Build.VERSION.SDK_INT >= 28)
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
    context.startActivity(
        new android.content.Intent(context, MainActivity.class)
            .addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
    {
      startNextMission();
      clickText("미션 시작하기");
      waitFor(allOf(withText("촬영하기"), isEnabled()));
      clickText("촬영하기");
      waitFor(withText("사진 확인하고 계속"));
      recreateResumedActivity();
      waitFor(withText("사진 확인하고 계속"));
      clickText("사진 확인하고 계속");
      clickText("수업과 학습");
      finishMission();

      startNextMission();
      clickText("미션 시작하기");
      onView(withText("길게 눌러 기억 열기")).perform(scrollTo(), holdFor(1000));
      recreateResumedActivity();
      waitFor(withText("길게 눌러 기억 열기"));
      onView(withText("길게 눌러 기억 열기")).perform(scrollToFallback(), holdFor(3200));
      waitFor(withText("학습과 자료 이용"));
      clickText("학습과 자료 이용");
      finishMission();

      startNextMission();
      clickText("미션 시작하기");
      onView(withHint("빈칸에 들어갈 두 글자")).perform(scrollTo(), replaceText("생"));
      closeSoftKeyboard();
      recreateResumedActivity();
      waitFor(withText("생"));
      onView(withHint("빈칸에 들어갈 두 글자")).perform(scrollTo(), replaceText("생활"));
      closeSoftKeyboard();
      clickText("답 확인");
      clickText("현장 안내");
      finishMission();

      List<MissionCompletion> records = records(repo, AppMode.DEMO);
      assertEquals(3, records.size());
      assertEquals("M01", records.get(0).missionId);
      assertEquals("PHOTO", records.get(0).completionMethod);
      assertEquals("M02", records.get(1).missionId);
      assertEquals("HOLD", records.get(1).completionMethod);
      assertEquals("M03", records.get(2).missionId);
      assertEquals("TYPED", records.get(2).completionMethod);
      for (MissionCompletion record : records) {
        assertEquals(AppMode.DEMO, record.mode);
        assertEquals("CARD", record.discoveryMethod);
      }
      File photo = new File(records.get(0).photoUri);
      assertTrue(photo.isFile());
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(photo.getAbsolutePath(), bounds);
      assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0);
      assertTrue("Demo progress leaked into field mode", records(repo, AppMode.FIELD).isEmpty());
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(() -> {
            java.util.Collection<android.app.Activity> activities =
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED);
            for (android.app.Activity activity : activities)
              if (activity instanceof MainActivity) activity.recreate();
          });
      waitFor(withText(containsString("3 / 5 완료")));
      assertEquals(3, records(repo, AppMode.DEMO).size());
      startNextMission();
      clickText("미션 시작하기");
      onView(withText("길게 눌러 기억 열기")).perform(scrollToFallback(), holdFor(3200));
      waitFor(withText("주변 시설의 상대적 방향"));
      clickText("주변 시설의 상대적 방향");
      finishMission();
      startNextMission();
      clickText("미션 시작하기");
      onView(withText("길게 눌러 기억 열기")).perform(scrollToFallback(), holdFor(3200));
      waitFor(withText("활동과 휴식 공간의 이용 안내"));
      clickText("활동과 휴식 공간의 이용 안내");
      finishMission();
      List<MissionCompletion> all = records(repo, AppMode.DEMO);
      assertEquals(5, all.size());
      assertEquals("HOLD", all.get(3).completionMethod);
      assertEquals("HOLD", all.get(4).completionMethod);
      assertTrue(records(repo, AppMode.FIELD).isEmpty());
      // A restored saving checkpoint must reconcile the existing award rather than add one.
      MissionCheckpointStore checkpointStore = new MissionCheckpointStore(context, true, "M01");
      MissionCheckpointStore.State saving = new MissionCheckpointStore.State();
      saving.stage = "SAVING";
      saving.photo = all.get(0).photoUri;
      saving.discovery = "CARD";
      saving.completion = "PHOTO";
      checkpointStore.save(saving);
      context.startActivity(
          new android.content.Intent(context, MissionActivity.class)
              .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
              .putExtra("missionIndex", 0)
              .putExtra("demo", true));
      waitFor(withText("기억 조각을 찾았어요"));
      List<MissionCompletion> afterRestore = records(repo, AppMode.DEMO);
      assertEquals(5, afterRestore.size());
      assertEquals(all.get(0).savedAt, afterRestore.get(0).savedAt);
      assertEquals(all.get(0).photoUri, afterRestore.get(0).photoUri);
      clickText("코스로 돌아가기");
    }
  }

  private void recreateResumedActivity() {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> {
          java.util.Collection<android.app.Activity> activities =
              androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                  .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED);
          for (android.app.Activity activity : activities) activity.recreate();
        });
  }

  private void startNextMission() {
    waitFor(withText("기억 찾기"));
    clickText("기억 찾기");
    clickText("기억 발견하기");
    waitFor(withId(R.id.ar_fallback));
    onView(withId(R.id.ar_fallback)).perform(click());
    onView(withText("기억 열기")).perform(click());
    waitFor(withText("미션 시작하기"));
  }

  private void finishMission() {
    waitFor(withText("코스로 돌아가기"));
    clickText("코스로 돌아가기");
    waitFor(withText(containsString("개의 기억")));
  }

  private void clickText(String text) {
    onView(withText(text)).perform(scrollToFallback(), click());
  }

  private List<MissionCompletion> records(MissionRepository repo, AppMode mode) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<List<MissionCompletion>> result = new AtomicReference<>();
    repo.getCompletions(
        mode,
        list -> {
          result.set(list);
          latch.countDown();
        });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    return result.get();
  }

  private void waitFor(Matcher<View> matcher) {
    onView(isRoot())
        .perform(
            new ViewAction() {
              public Matcher<View> getConstraints() {
                return isRoot();
              }

              public String getDescription() {
                return "Wait up to 15 seconds for " + matcher;
              }

              public void perform(UiController ui, View root) {
                long deadline = SystemClock.uptimeMillis() + 15000;
                do {
                  for (View child : TreeIterables.breadthFirstViewTraversal(root))
                    if (matcher.matches(child)) return;
                  ui.loopMainThreadForAtLeast(50);
                } while (SystemClock.uptimeMillis() < deadline);
                throw new AssertionError("View did not appear: " + matcher);
              }
            });
  }

  private ViewAction scrollToFallback() {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return isAssignableFrom(View.class);
      }

      public String getDescription() {
        return "Scroll fallback control into view";
      }

      public void perform(UiController ui, View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof android.widget.ScrollView))
          parent = parent.getParent();
        if (parent instanceof android.widget.ScrollView) {
          android.widget.ScrollView scroll = (android.widget.ScrollView) parent;
          android.graphics.Rect rectangle = new android.graphics.Rect();
          view.getDrawingRect(rectangle);
          scroll.offsetDescendantRectToMyCoords(view, rectangle);
          scroll.scrollTo(
              0,
              Math.max(0, rectangle.bottom - scroll.getHeight() + scroll.getPaddingBottom() + 24));
          ui.loopMainThreadForAtLeast(100);
        }
      }
    };
  }

  private ViewAction holdFor(long millis) {
    return new ViewAction() {
      public Matcher<View> getConstraints() {
        return allOf(isDisplayed(), isEnabled());
      }

      public String getDescription() {
        return "Hold continuously for " + millis + "ms";
      }

      public void perform(UiController ui, View view) {
        int[] point = new int[2];
        view.getLocationOnScreen(point);
        float x = point[0] + view.getWidth() / 2f, y = point[1] + view.getHeight() / 2f;
        long began = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(began, began, MotionEvent.ACTION_DOWN, x, y, 0);
        try {
          ui.injectMotionEvent(down);
        } catch (Exception error) {
          throw new AssertionError(error);
        }
        down.recycle();
        ui.loopMainThreadForAtLeast(millis);
        MotionEvent up =
            MotionEvent.obtain(began, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        try {
          ui.injectMotionEvent(up);
        } catch (Exception error) {
          throw new AssertionError(error);
        }
        up.recycle();
      }
    };
  }
}
