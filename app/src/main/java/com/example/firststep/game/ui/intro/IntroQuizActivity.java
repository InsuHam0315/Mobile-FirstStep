package com.example.firststep.game.ui.intro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.composition.introquiz.IntroQuizHost;
import com.example.firststep.game.content.*;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class IntroQuizActivity extends AppCompatActivity {
  public static final String BUILDING = "digital_information_software";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private IntroQuizController controller;
  private IntroQuizHost host;
  private String modeName;
  private boolean fake;
  private long fakeNow;
  private volatile boolean alive = true;
  private String speechStatus = "";
  private Bundle restored;
  private boolean resumed;
  private String[] sentences;
  private NarrationContent softwareNarration;
  private GuideBundle softwareGuide;
  private boolean startQuizAfterBooklet;
  private int tourQuestionIndex;
  private final ExecutorService integrationWorker = Executors.newSingleThreadExecutor();
  private IntegrationContext integrationContext;
  private boolean integrationRoute, routeReady;
  private IntegrationContext requestedIntegration;
  private long routeGeneration, clockGeneration;

  public IntroQuizController.Snapshot snapshot() {
    return controller == null ? null : controller.snapshot();
  }

  public IntroQuizController.Snapshot getSnapshotForTest() {
    return snapshot();
  }

  public void setDebugClockForTest(long now) {
    if (!BuildConfig.DEBUG || !fake) return;
    if (integrationRoute) {
      IntegrationContext current = integrationContext;
      if (current == null || !resumed) return;
      long routeToken = routeGeneration, clockToken = ++clockGeneration;
      routeReady = false;
      if (host != null) host.stopTicks();
      if (controller != null) controller.pause();
      visible(R.id.game_intro_scroll, false);
      integrationWorker.execute(
          () -> {
            IntegrationContext latest = null;
            try {
              IntegrationContext next = current.withFakeNow(now);
              NavigationStore store = new NavigationStore(getApplicationContext());
              if (store.saveClock(next)) {
                com.example.firststep.game.presentation.integration.NavigationPolicy.State state =
                    store.load(next);
                if (state != null) latest = state.context;
              }
            } catch (RuntimeException ignored) {
            }
            IntegrationContext saved = latest;
            runOnUiThread(
                () -> {
                  if (!alive
                      || !resumed
                      || routeGeneration != routeToken
                      || clockGeneration != clockToken) return;
                  visible(R.id.game_intro_scroll, true);
                  if (saved == null) {
                    error(getString(R.string.game_integration_error));
                    return;
                  }
                  integrationContext = saved;
                  routeReady = true;
                  fakeNow = saved.fakeNow;
                  if (host != null) host.setClock(fakeNow);
                  if (controller != null) {
                    controller.resume();
                    host.startTicks(controller);
                  }
                });
          });
    } else applyDebugClock(now);
  }

  private void applyDebugClock(long now) {
    fakeNow = now;
    if (host != null) host.setClock(now);
    if (resumed && controller != null) controller.tick();
  }

  public int debugSpeechGenerationForTest() {
    return BuildConfig.DEBUG && host != null ? host.speechGenerationForTest() : -1;
  }

  public void debugSpeechErrorForTest(int token) {
    if (BuildConfig.DEBUG && host != null) host.speechErrorForTest(token);
  }

  public void debugReplayForTest() {
    if (BuildConfig.DEBUG && controller != null) controller.replay();
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_intro_activity);
    restored = state;
    startQuizAfterBooklet = state != null && state.getBoolean("start_quiz_after_booklet");
    bindBooklet();
    bindTourQuiz();
    bindTourResult();
    tourQuestionIndex = state == null ? 0 : state.getInt("tour_question_index");
    View scroll = findViewById(R.id.game_intro_scroll);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        scroll,
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    sentences = getResources().getStringArray(R.array.game_intro_sentences);
    findViewById(R.id.game_intro_back).setOnClickListener(v -> finish());
    integrationRoute = IntegrationIntentCodec.hasIntegrationContext(getIntent());
    if (integrationRoute) {
      visible(R.id.game_intro_demo, false);
      for (int id :
          new int[] {
            R.id.game_intro_show_again,
            R.id.game_intro_replay,
            R.id.game_intro_next,
            R.id.game_intro_finish,
            R.id.game_intro_skip,
            R.id.game_intro_reminder,
            R.id.game_quiz_start,
            R.id.game_quiz_submit,
            R.id.game_quiz_retry,
            R.id.game_quiz_result
          }) visible(id, false);
      ((TextView) findViewById(R.id.game_intro_status)).setText(R.string.game_integration_loading);
      requestedIntegration =
          BuildConfig.DEBUG ? IntegrationIntentCodec.readContext(getIntent()) : null;
      if (requestedIntegration == null) error(getString(R.string.game_integration_error));
      else if (softwareDemo(requestedIntegration)) {
        ((TextView) findViewById(R.id.game_intro_title))
            .setText(R.string.game_software_demo_intro_title);
        ((TextView) findViewById(R.id.game_intro_disclosure))
            .setText(R.string.game_software_demo_disclosure);
        visible(R.id.game_intro_caption, false);
      }
      return;
    }
    findViewById(R.id.game_intro_demo)
        .setOnClickListener(
            v -> {
              android.content.Intent i =
                  new android.content.Intent(this, IntroQuizActivity.class)
                      .putExtra("game_intro_building_id", BUILDING)
                      .putExtra("game_intro_mode", "DEMO")
                      .putExtra("game_intro_instant_demo", true);
              if (BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run"))
                i.putExtra(
                    "game_intro_debug_run", getIntent().getStringExtra("game_intro_debug_run"));
              startActivity(i);
            });
    findViewById(R.id.game_intro_demo).setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
    String building = getIntent().getStringExtra("game_intro_building_id");
    modeName = getIntent().getStringExtra("game_intro_mode");
    if (BuildConfig.DEBUG && android.content.Intent.ACTION_MAIN.equals(getIntent().getAction())) {
      if (building == null) building = BUILDING;
      if (modeName == null) modeName = "NORMAL";
    }
    if (!BUILDING.equals(building) || !("NORMAL".equals(modeName) || "DEMO".equals(modeName))) {
      error(getString(R.string.game_intro_ui_1));
      return;
    }
    boolean instant = getIntent().getBooleanExtra("game_intro_instant_demo", false);
    if (instant && !"DEMO".equals(modeName)) {
      error(getString(R.string.game_intro_ui_2));
      return;
    }
    fake = BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_clock");
    fakeNow =
        state != null && fake && state.containsKey("debug_clock")
            ? state.getLong("debug_clock")
            : getIntent().getLongExtra("game_intro_debug_clock", 0);
    String run = getIntent().getStringExtra("game_intro_debug_run");
    if (fake || (BuildConfig.DEBUG && run != null)) {
      try {
        if (run == null || !UUID.fromString(run).toString().equals(run))
          throw new IllegalArgumentException();
      } catch (Exception e) {
        error(getString(R.string.game_intro_ui_3));
        return;
      }
    }

    initializeHost(instant, run);
  }

  private static boolean softwareDemo(IntegrationContext context) {
    return context != null && IntegrationContext.SOFTWARE_DATASET.equals(context.datasetId);
  }

  private void initializeHost(boolean instant, String run) {
    TextView target = findViewById(R.id.game_intro_target);
    if (softwareDemo(integrationContext)) {
      target.setText(R.string.game_software_demo_target);
      ((TextView) findViewById(R.id.game_intro_back)).setText(R.string.game_software_demo_back);
    } else if (integrationRoute && integrationContext != null) {
      target.setText(
          android.text.TextUtils.concat(
              getString(R.string.game_intro_target),
              "\n",
              getString(R.string.game_integration_disclosure),
              "\n",
              integrationContext.datasetId,
              "\n",
              getString(
                  R.string.game_integration_context,
                  integrationContext.runId,
                  integrationContext.mode.name(),
                  getString(
                      integrationContext.clock == IntegrationContext.Clock.REAL
                          ? R.string.game_integration_real_clock
                          : R.string.game_integration_fake_clock))));
    } else target.setText(R.string.game_intro_target);
    visible(R.id.game_intro_target, true);
    for (int id :
        new int[] {
          R.id.game_intro_show_again,
          R.id.game_intro_replay,
          R.id.game_intro_next,
          R.id.game_intro_finish,
          R.id.game_intro_skip,
          R.id.game_intro_reminder,
          R.id.game_quiz_start,
          R.id.game_quiz_submit,
          R.id.game_quiz_retry,
          R.id.game_quiz_result
        }) visible(id, false);
    ((TextView) findViewById(R.id.game_intro_status)).setText(R.string.game_intro_ui_10);
    host =
        new IntroQuizHost(
            this, modeName, instant, BuildConfig.DEBUG ? run : null, fake, fakeNow,
            integrationContext);
    findViewById(R.id.game_intro_show_again).setOnClickListener(v -> controller.showIntro());
    findViewById(R.id.game_intro_replay).setOnClickListener(v -> controller.replay());
    findViewById(R.id.game_intro_next).setOnClickListener(v -> controller.next());
    findViewById(R.id.game_intro_finish).setOnClickListener(v -> {
      if (!softwareDemo(integrationContext) || controller.snapshot().sentence + 1 == sentences.length)
        controller.finish();
    });
    findViewById(R.id.game_intro_skip).setOnClickListener(v -> {
      if (!softwareDemo(integrationContext)) controller.finish();
    });
    findViewById(R.id.game_quiz_start).setOnClickListener(v -> controller.start());
    findViewById(R.id.game_quiz_submit).setOnClickListener(v -> controller.submit());
    findViewById(R.id.game_quiz_retry).setOnClickListener(v -> {
      tourQuestionIndex = 0;
      controller.retry();
    });
    findViewById(R.id.game_intro_reminder).setOnClickListener(v -> reminder());
    host.start(
        sentences,
        BuildConfig.DEBUG
            ? (getIntent().getBooleanExtra("game_intro_debug_speech_failure", false)
                ? "init_error"
                : getIntent().getStringExtra("game_intro_debug_speech_mode"))
            : null,
        message -> {
          if (alive) {
            speechStatus = message;
            if (controller != null) render(controller.snapshot());
          }
        },
        this::render,
        c -> {
          if (softwareDemo(integrationContext)) {
            IntegrationComposition composition = host.composition();
            softwareNarration = composition.narration;
            softwareGuide = composition.guide;
            sentences =
                softwareNarration.sentences().stream().map(sentence -> sentence.text)
                    .toArray(String[]::new);
          }
          controller = c;
          if (restored != null) {
            controller.restore(
                restored.getInt("sentence"),
                restored.getBoolean("intro"),
                map(restored, "answer"),
                restored.getString("active_id"),
                restored.getString("active_version"),
                restored.getString("result_id"),
                restored.getString("pending_id"),
                restored.getString("pending_version"),
                map(restored, "pending"));
          }
          if (resumed && (!integrationRoute || routeReady)) {
            controller.resume();
            host.startTicks(controller);
          }
        },
        () -> error(getString(softwareDemo(integrationContext)
            ? R.string.game_integration_error : R.string.game_intro_ui_4)));
  }

  private Map<String, String> map(Bundle b, String key) {
    Bundle x = b.getBundle(key);
    if (x == null) return null;
    Map<String, String> m = new LinkedHashMap<>();
    for (String k : x.keySet()) m.put(k, x.getString(k));
    return m;
  }

  private Bundle bundle(Map<String, String> m) {
    if (m == null) return null;
    Bundle b = new Bundle();
    for (Map.Entry<String, String> e : m.entrySet()) b.putString(e.getKey(), e.getValue());
    return b;
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    if (BuildConfig.DEBUG && fake) out.putLong("debug_clock", fakeNow);
    if (controller == null && restored != null) {
      out.putAll(restored);
      if (BuildConfig.DEBUG && fake) out.putLong("debug_clock", fakeNow);
    }
    if (controller != null) {
      IntroQuizController.Snapshot s = controller.snapshot();
      out.putInt("sentence", s.sentence);
      out.putBoolean("intro", controller.wantsIntroduction());
      out.putString(
          "active_id",
          s.progress != null && s.progress.active != null ? s.progress.active.id : null);
      out.putString(
          "active_version",
          s.progress != null && s.progress.active != null
              ? s.progress.active.contentVersion
              : null);
      out.putString("result_id", controller.resultIdToRestore());
      out.putBundle("answer", bundle(s.answers));
      out.putString("pending_id", controller.pendingId());
      out.putString("pending_version", controller.pendingVersion());
      out.putBundle("pending", bundle(controller.pendingAnswers()));
    }
    out.putBoolean("start_quiz_after_booklet", startQuizAfterBooklet);
    out.putInt("tour_question_index", tourQuestionIndex);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (integrationRoute) {
      if (requestedIntegration != null) verifyIntegrationResume();
      return;
    }
    if (controller != null) {
      controller.resume();
      if (host != null) host.startTicks(controller);
    }
  }

  private void verifyIntegrationResume() {
    long token = ++routeGeneration;
    routeReady = false;
    ++clockGeneration;
    visible(R.id.game_intro_scroll, false);
    integrationWorker.execute(
        () -> {
          try {
            IntegrationContext loaded =
                IntegrationRouteAccess.requireIntroduction(
                    getApplicationContext(), requestedIntegration);
            runOnUiThread(
                () -> {
                  if (!alive || !resumed || routeGeneration != token || isFinishing()) return;
                  integrationContext = loaded;
                  routeReady = true;
                  modeName = loaded.mode.name();
                  fake = loaded.clock == IntegrationContext.Clock.DEBUG_FAKE;
                  fakeNow = fake ? loaded.fakeNow : 0;
                  visible(R.id.game_intro_scroll, true);
                  for (int id :
                      new int[] {
                        R.id.game_intro_show_again,
                        R.id.game_intro_replay,
                        R.id.game_intro_next,
                        R.id.game_intro_finish,
                        R.id.game_intro_skip,
                        R.id.game_quiz_retry
                      }) findViewById(id).setEnabled(true);
                  if (host == null) initializeHost(loaded.mode == Progress.Mode.DEMO, loaded.runId);
                  else {
                    if (fake) host.setClock(fakeNow);
                    if (controller != null) {
                      controller.resume();
                      host.startTicks(controller);
                    }
                  }
                });
          } catch (Exception failure) {
            runOnUiThread(
                () -> {
                  if (!alive || !resumed || routeGeneration != token || isFinishing()) return;
                  visible(R.id.game_intro_scroll, true);
                  error(getString(R.string.game_integration_error));
                });
          }
        });
  }

  @Override
  protected void onPause() {
    resumed = false;
    routeReady = false;
    ++routeGeneration;
    ++clockGeneration;
    if (host != null) host.stopTicks();
    if (controller != null) controller.pause();
    else if (host != null) host.pauseSpeech();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    alive = false;
    if (host != null) host.close();
    integrationWorker.shutdown();
    super.onDestroy();
  }

  private void error(String s) {
    findViewById(R.id.game_intro_demo).setEnabled(false);
    ((TextView) findViewById(R.id.game_intro_status)).setText(s);
    for (int id :
        new int[] {
          R.id.game_intro_show_again,
          R.id.game_intro_replay,
          R.id.game_intro_next,
          R.id.game_intro_finish,
          R.id.game_intro_skip,
          R.id.game_intro_reminder,
          R.id.game_quiz_start,
          R.id.game_quiz_submit,
          R.id.game_quiz_retry
        }) findViewById(id).setEnabled(false);
  }

  private void visible(int id, boolean show) {
    findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private void render(IntroQuizController.Snapshot s) {
    if (integrationRoute && !routeReady) return;
    boolean software = softwareDemo(integrationContext);
    boolean intro = s.stage == IntroQuizController.Stage.INTRO,
        wait = s.stage == IntroQuizController.Stage.WAITING,
        quiz = s.stage == IntroQuizController.Stage.QUIZ,
        result = s.stage == IntroQuizController.Stage.RESULT;
    for (int id :
        new int[] {
          R.id.game_intro_replay, R.id.game_intro_next, R.id.game_intro_finish, R.id.game_intro_skip
        }) visible(id, intro);
    boolean booklet = software && intro;
    boolean tourQuiz = software && quiz && s.progress != null && s.progress.active != null;
    boolean tourResult = software && result && s.result != null;
    boolean standardHeader = !booklet && !tourQuiz && !tourResult;
    visible(R.id.game_tour_result, tourResult);
    if (tourResult) renderTourResult(s);
    visible(R.id.game_tour_quiz, tourQuiz);
    visible(R.id.game_intro_booklet, booklet);
    visible(R.id.game_intro_title, standardHeader);
    visible(R.id.game_intro_target, standardHeader);
    visible(R.id.game_intro_disclosure, standardHeader);
    visible(R.id.game_intro_status, standardHeader);
    visible(R.id.game_intro_back, standardHeader);
    visible(R.id.game_quiz_questions, quiz && !tourQuiz);
    if (booklet) {
      for (int id : new int[] {R.id.game_intro_replay, R.id.game_intro_next,
          R.id.game_intro_finish, R.id.game_intro_skip}) visible(id, false);
      renderBooklet(s.sentence);
    }
    if (software && startQuizAfterBooklet && wait && s.eligible) {
      findViewById(R.id.game_intro_scroll).post(() -> {
        if (!resumed || !routeReady || !startQuizAfterBooklet || controller == null) return;
        IntroQuizController.Snapshot current = controller.snapshot();
        if (current.stage != IntroQuizController.Stage.WAITING || !current.eligible) return;
        startQuizAfterBooklet = false;
        controller.start();
      });
    } else if (quiz || result || s.stage == IntroQuizController.Stage.ERROR) {
      startQuizAfterBooklet = false;
    }
    visible(R.id.game_intro_show_again, wait || (result && !tourResult));
    visible(R.id.game_intro_caption, intro && !booklet);
    visible(
        R.id.game_intro_reminder,
        wait
            && !integrationRoute
            && !fake
            && !(BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run")));
    visible(R.id.game_quiz_start, wait);
    visible(R.id.game_quiz_submit, quiz && !tourQuiz);
    visible(
        R.id.game_quiz_retry,
        (result && !tourResult && s.result != null && !s.result.passed)
            || s.stage == IntroQuizController.Stage.ERROR);
    visible(R.id.game_quiz_result, result && !tourResult);
    String label =
        software ? getString(R.string.game_software_demo_mode)
        : "DEMO".equals(modeName)
            ? ((integrationRoute && integrationContext != null
                    ? integrationContext.mode == Progress.Mode.DEMO
                    : getIntent().getBooleanExtra("game_intro_instant_demo", false))
                ? getString(R.string.game_intro_ui_5)
                : getString(R.string.game_intro_ui_6))
            : getString(R.string.game_intro_ui_7);
    if (!software
        && (integrationRoute || (BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run"))))
      label += getString(R.string.game_intro_ui_8);
    if (fake) label += getString(R.string.game_intro_ui_9);
    if (BuildConfig.DEBUG
        && (getIntent().hasExtra("game_intro_debug_speech_mode")
            || getIntent().getBooleanExtra("game_intro_debug_speech_failure", false)))
      label += getString(R.string.game_intro_debug_speech_label);
    if (s.stage == IntroQuizController.Stage.LOADING) label += getString(R.string.game_intro_ui_10);
    if (intro)
      label +=
          getString(R.string.game_intro_ui_11)
              + (speechStatus.isEmpty() ? "" : "\n" + speechStatus);
    if (wait) {
      label +=
          s.eligible
              ? getString(R.string.game_intro_ui_12)
              : getString(
                  R.string.game_intro_remaining,
                  (s.remainingMillis / 1000 + (s.remainingMillis % 1000 == 0 ? 0 : 1)) / 60,
                  (s.remainingMillis / 1000 + (s.remainingMillis % 1000 == 0 ? 0 : 1)) % 60);
      if (!software) label += getString(R.string.game_intro_ui_14);
    }
    if (software && quiz) label += "\n" + getString(R.string.game_software_demo_quiz_policy);
    if (s.stage == IntroQuizController.Stage.ERROR)
      label += "\n" + getString(R.string.game_intro_storage_error);
    ((TextView) findViewById(R.id.game_intro_status)).setText(label);
    ((TextView) findViewById(R.id.game_intro_caption)).setText(sentences[s.sentence]);
    findViewById(R.id.game_quiz_start).setEnabled(wait && s.eligible);
    findViewById(R.id.game_quiz_submit).setEnabled(quiz && s.complete());
    LinearLayout list = findViewById(R.id.game_quiz_questions);
    list.removeAllViews();
    if (tourQuiz) renderTourQuiz(s);
    else if (quiz && s.progress.active != null)
      for (GameContent.Question q : s.progress.active.questions) {
        TextView title = new TextView(this);
        title.setText(q.text);
        title.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.game_color_text));
        title.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX,
            getResources().getDimension(R.dimen.game_text_body));
        title.setTag(q.id);
        list.addView(title);
        RadioGroup group = new RadioGroup(this);
        group.setTag(q.id);
        for (GameContent.Option o : q.options) {
          RadioButton choice = new RadioButton(this);
          choice.setId(View.generateViewId());
          choice.setTag(o.id);
          choice.setText(o.text);
          choice.setTextColor(
              androidx.core.content.ContextCompat.getColor(this, R.color.game_color_text));
          choice.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
          group.addView(choice);
          if (o.id.equals(s.answers.get(q.id))) choice.setChecked(true);
          choice.setOnClickListener(v -> controller.choose(q.id, o.id));
        }
        list.addView(group);
      }
    ((TextView) findViewById(R.id.game_quiz_result))
        .setText(
            result && s.result != null
                ? (s.result.passed
                        ? getString(software ? R.string.game_software_demo_stamp
                            : R.string.game_intro_ui_15)
                        : getString(R.string.game_intro_ui_16))
                    + " ("
                    + s.result.correct
                    + "/3)"
                : "");
  }

  private void bindTourResult() {
    findViewById(R.id.game_tour_result_back).setOnClickListener(v -> finish());
    findViewById(R.id.game_tour_result_home).setOnClickListener(v -> finish());
    findViewById(R.id.game_tour_result_home_secondary).setOnClickListener(v -> finish());
    findViewById(R.id.game_tour_result_review).setOnClickListener(v -> controller.showIntro());
    findViewById(R.id.game_tour_result_retry).setOnClickListener(v -> {
      tourQuestionIndex = 0;
      controller.retry();
    });
  }

  private void renderTourResult(IntroQuizController.Snapshot state) {
    boolean passed = state.result.passed;
    ((ImageView) findViewById(R.id.game_tour_result_icon)).setImageResource(
        passed ? R.drawable.game_tour_result_seal : R.drawable.game_booklet_checklist_icon);
    ((TextView) findViewById(R.id.game_tour_result_heading)).setText(passed
        ? R.string.game_tour_result_complete : R.string.game_tour_result_try_again);
    ((TextView) findViewById(R.id.game_tour_result_score)).setText(getString(
        R.string.game_narration_position, state.result.correct, softwareGuide.getQuestions().size()));
    ((TextView) findViewById(R.id.game_tour_result_stamp)).setText(getString(
        R.string.game_tour_result_stamp_count, state.progress != null && state.progress.stamp ? 1 : 0));
    TextView quizStep = findViewById(R.id.game_tour_result_quiz_step);
    quizStep.setCompoundDrawablesRelativeWithIntrinsicBounds(
        passed ? R.drawable.game_demo_hub_check_icon : R.drawable.game_booklet_checklist_icon, 0, 0, 0);
    androidx.core.view.ViewCompat.setStateDescription(
        quizStep, passed ? getString(R.string.game_demo_hub_step_done) : null);
    visible(R.id.game_tour_result_hint, !passed);
    visible(R.id.game_tour_result_retry, !passed);
    visible(R.id.game_tour_result_home, passed);
    visible(R.id.game_tour_result_home_secondary, !passed);
  }

  private void bindTourQuiz() {
    findViewById(R.id.game_tour_quiz_back).setOnClickListener(v -> finish());
    findViewById(R.id.game_tour_quiz_home).setOnClickListener(v -> finish());
    findViewById(R.id.game_tour_quiz_previous)
        .setOnClickListener(v -> moveTourQuestion(tourQuestionIndex - 1));
    findViewById(R.id.game_tour_quiz_next).setOnClickListener(v -> {
      IntroQuizController.Snapshot current = controller.snapshot();
      if (current.stage != IntroQuizController.Stage.QUIZ || current.progress.active == null) return;
      List<GameContent.Question> questions = current.progress.active.questions;
      if (tourQuestionIndex == questions.size() - 1) {
        if (current.complete()) controller.submit();
      } else if (current.answers.containsKey(questions.get(tourQuestionIndex).id)) {
        moveTourQuestion(tourQuestionIndex + 1);
      }
    });
  }

  private void moveTourQuestion(int index) {
    IntroQuizController.Snapshot current = controller.snapshot();
    if (current.stage != IntroQuizController.Stage.QUIZ || current.progress.active == null
        || index < 0 || index >= current.progress.active.questions.size()) return;
    tourQuestionIndex = index;
    renderTourQuiz(current);
    android.widget.ScrollView scroll = findViewById(R.id.game_intro_scroll);
    scroll.scrollTo(0, 0);
  }

  private void renderTourQuiz(IntroQuizController.Snapshot s) {
    List<GameContent.Question> questions = s.progress.active.questions;
    tourQuestionIndex = Math.max(0, Math.min(tourQuestionIndex, questions.size() - 1));
    GameContent.Question question = questions.get(tourQuestionIndex);
    boolean last = tourQuestionIndex == questions.size() - 1;
    renderTourQuizTabs(questions, s.answers);
    ((TextView) findViewById(R.id.game_tour_quiz_page)).setText(getString(
        R.string.game_narration_position, tourQuestionIndex + 1, questions.size()));

    RadioGroup options = findViewById(R.id.game_tour_quiz_options);
    if (options.getTag() != question) {
      options.setTag(question);
      options.removeAllViews();
      TextView prompt = findViewById(R.id.game_tour_quiz_prompt);
      prompt.setText(question.text);
      prompt.setCompoundDrawablesRelativeWithIntrinsicBounds(
          last ? R.drawable.game_booklet_checklist_icon : R.drawable.game_guide_icon_book, 0, 0, 0);
      for (GameContent.Option option : question.options) {
        RadioButton row = (RadioButton) getLayoutInflater()
            .inflate(R.layout.game_guide_quiz_option, options, false);
        row.setId(View.generateViewId());
        row.setText(option.text);
        row.setTag(option.id);
        row.setOnClickListener(v -> controller.choose(question.id, option.id));
        options.addView(row);
      }
      GuideBundle.Question approved = approvedQuestion(question.id);
      TextView source = findViewById(R.id.game_tour_quiz_source);
      source.setOnClickListener(null);
      source.setContentDescription(null);
      visible(R.id.game_tour_quiz_source, approved != null);
      visible(R.id.game_tour_quiz_source_divider, approved != null);
      if (approved != null) bindSource(source, approved.getClaimId());
    }
    String answer = s.answers.get(question.id);
    options.clearCheck();
    for (int i = 0; i < options.getChildCount(); i++) {
      View row = options.getChildAt(i);
      if (row.getTag().equals(answer)) {
        options.check(row.getId());
        break;
      }
    }

    TextView note = findViewById(R.id.game_tour_quiz_note);
    note.setText(last ? getString(R.string.game_tour_quiz_answer_count,
        s.answers.size(), questions.size()) : getString(R.string.game_tour_quiz_stamp_hint));
    visible(R.id.game_tour_quiz_previous, tourQuestionIndex > 0);
    Button next = findViewById(R.id.game_tour_quiz_next);
    next.setText(last ? R.string.game_intro_submit : R.string.game_tour_quiz_next);
    next.setEnabled(last ? s.complete() : answer != null);
  }

  private void renderTourQuizTabs(List<GameContent.Question> questions, Map<String, String> answers) {
    LinearLayout tabs = findViewById(R.id.game_tour_quiz_tabs);
    if (tabs.getChildCount() != questions.size()) {
      tabs.removeAllViews();
      for (int i = 0; i < questions.size(); i++) {
        final int index = i;
        Button tab = (Button) getLayoutInflater().inflate(R.layout.game_tour_quiz_tab, tabs, false);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tab.getLayoutParams();
        if (i != 0) params.setMarginStart(
            getResources().getDimensionPixelSize(R.dimen.game_reskin_control_space));
        tab.setOnClickListener(v -> moveTourQuestion(index));
        tabs.addView(tab);
      }
    }
    for (int i = 0; i < questions.size(); i++) {
      Button tab = (Button) tabs.getChildAt(i);
      boolean active = i == tourQuestionIndex;
      boolean answered = answers.containsKey(questions.get(i).id);
      tab.setText(getString(answered && !active ? R.string.game_tour_quiz_checked_number
          : R.string.game_tour_quiz_number, i + 1));
      tab.setContentDescription(getString(R.string.game_tour_quiz_number_description, i + 1));
      tab.setBackgroundResource(R.drawable.game_discovery_step_idle);
      tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(
          active ? R.color.game_color_action : R.color.game_reskin_tinted)));
      tab.setTextColor(getColor(active ? R.color.game_color_on_action : R.color.game_color_text));
      androidx.core.view.ViewCompat.setStateDescription(tab, active
          ? getString(answered ? R.string.game_tour_quiz_current_answered : R.string.game_tour_quiz_current)
          : answered ? getString(R.string.game_tour_quiz_answered) : null);
    }
  }

  private void bindBooklet() {
    findViewById(R.id.game_booklet_back).setOnClickListener(v -> finish());
    findViewById(R.id.game_booklet_home).setOnClickListener(v -> finish());
    findViewById(R.id.game_booklet_previous).setOnClickListener(v -> {
      speechStatus = "";
      controller.previous();
    });
    findViewById(R.id.game_booklet_replay).setOnClickListener(v -> controller.replay());
    findViewById(R.id.game_booklet_next).setOnClickListener(v -> {
      IntroQuizController.Snapshot state = controller.snapshot();
      if (state.stage != IntroQuizController.Stage.INTRO) return;
      speechStatus = "";
      if (state.sentence + 1 < sentences.length) {
        controller.next();
      } else {
        startQuizAfterBooklet = true;
        controller.finish();
      }
    });
  }

  private void renderBooklet(int index) {
    NarrationContent.Sentence sentence = softwareNarration.sentences().get(index);
    boolean last = index + 1 == sentences.length;
    ((TextView) findViewById(R.id.game_booklet_page)).setText(
        getString(R.string.game_narration_position, index + 1, sentences.length));
    TextView title = findViewById(R.id.game_booklet_title);
    title.setText(bookletTitle(sentence.id));
    title.setCompoundDrawablesRelativeWithIntrinsicBounds(
        last ? R.drawable.game_booklet_checklist_icon : R.drawable.game_guide_icon_book, 0, 0, 0);
    ((TextView) findViewById(R.id.game_booklet_caption)).setText(last
        ? getString(R.string.game_booklet_final_caption, sentence.text) : sentence.text);
    visible(R.id.game_booklet_checklist, last);
    visible(R.id.game_booklet_tools, !last);
    visible(R.id.game_booklet_previous, true);
    findViewById(R.id.game_booklet_previous).setEnabled(index > 0);
    ((TextView) findViewById(R.id.game_booklet_next)).setText(
        last ? R.string.game_booklet_quiz : R.string.game_booklet_next_page);
    TextView speech = findViewById(R.id.game_booklet_speech_status);
    speech.setText(speechStatus);
    visible(R.id.game_booklet_speech_status, !last && !speechStatus.isEmpty());
    TextView source = findViewById(R.id.game_booklet_source);
    source.setOnClickListener(null);
    source.setContentDescription(null);
    boolean official = sentence.provenance == NarrationContent.Provenance.CLAIM;
    visible(R.id.game_booklet_source, official);
    if (official) bindSource(source, sentence.claimId);
  }

  private static int bookletTitle(String id) {
    switch (id) {
      case "greeting": return R.string.game_booklet_title_greeting;
      case "visit": return R.string.game_guide_section_visit;
      case "values": return R.string.game_booklet_title_values;
      case "resources": return R.string.game_guide_section_life;
      case "freshness": return R.string.game_booklet_title_done;
      default: return R.string.game_booklet_title_membership;
    }
  }

  private GuideBundle.Question approvedQuestion(String questionId) {
    for (GuideBundle.Question question : softwareGuide.getQuestions())
      if (questionId.equals(question.getId())) return question;
    return null;
  }

  private void bindSource(TextView text, String claimId) {
    GuideBundle.Source source = null;
    for (GuideBundle.Claim claim : softwareGuide.getClaims()) {
      if (!claimId.equals(claim.getId())) continue;
      for (GuideBundle.Source item : softwareGuide.getSources()) {
        if (claim.getSourceId().equals(item.getId())) {
          source = item;
          break;
        }
      }
      break;
    }
    if (source == null) {
      text.setText(R.string.game_narration_source_unavailable);
      return;
    }
    GuideBundle.Source official = source;
    text.setText(R.string.game_guide_review_source);
    text.setContentDescription(getString(R.string.game_narration_source_accessibility,
        official.getLabel(), official.getCheckedDate()));
    text.setOnClickListener(v -> {
      if (!GuideAdapters.openOfficialSource(this, official))
        Toast.makeText(this, R.string.game_guide_source_failed, Toast.LENGTH_LONG).show();
    });
  }

  private void reminder() {
    if (controller == null
        || integrationRoute
        || fake
        || (BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run"))) return;
    IntroQuizController.Snapshot s = controller.snapshot();
    if (s.progress == null || s.progress.introDoneAt == null) return;
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 7404);
      return;
    }
    try {
      host.remind(s.progress);
      Toast.makeText(this, getString(R.string.game_intro_ui_17), Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      Toast.makeText(this, getString(R.string.game_intro_ui_18), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 7404) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) reminder();
      else Toast.makeText(this, getString(R.string.game_intro_ui_19), Toast.LENGTH_LONG).show();
    }
  }
}
