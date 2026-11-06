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

  private void initializeHost(boolean instant, String run) {
    TextView target = findViewById(R.id.game_intro_target);
    if (integrationRoute && integrationContext != null) {
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
        new IntroQuizHost(this, modeName, instant, BuildConfig.DEBUG ? run : null, fake, fakeNow);
    findViewById(R.id.game_intro_show_again).setOnClickListener(v -> controller.showIntro());
    findViewById(R.id.game_intro_replay).setOnClickListener(v -> controller.replay());
    findViewById(R.id.game_intro_next).setOnClickListener(v -> controller.next());
    findViewById(R.id.game_intro_finish).setOnClickListener(v -> controller.finish());
    findViewById(R.id.game_intro_skip).setOnClickListener(v -> controller.finish());
    findViewById(R.id.game_quiz_start).setOnClickListener(v -> controller.start());
    findViewById(R.id.game_quiz_submit).setOnClickListener(v -> controller.submit());
    findViewById(R.id.game_quiz_retry).setOnClickListener(v -> controller.retry());
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
        () -> error(getString(R.string.game_intro_ui_4)));
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
    boolean intro = s.stage == IntroQuizController.Stage.INTRO,
        wait = s.stage == IntroQuizController.Stage.WAITING,
        quiz = s.stage == IntroQuizController.Stage.QUIZ,
        result = s.stage == IntroQuizController.Stage.RESULT;
    for (int id :
        new int[] {
          R.id.game_intro_replay, R.id.game_intro_next, R.id.game_intro_finish, R.id.game_intro_skip
        }) visible(id, intro);
    visible(R.id.game_intro_show_again, wait || result);
    visible(R.id.game_intro_caption, intro);
    visible(
        R.id.game_intro_reminder,
        wait
            && !integrationRoute
            && !fake
            && !(BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run")));
    visible(R.id.game_quiz_start, wait);
    visible(R.id.game_quiz_submit, quiz);
    visible(
        R.id.game_quiz_retry,
        (result && s.result != null && !s.result.passed)
            || s.stage == IntroQuizController.Stage.ERROR);
    visible(R.id.game_quiz_result, result);
    String label =
        "DEMO".equals(modeName)
            ? ((integrationRoute && integrationContext != null
                    ? integrationContext.mode == Progress.Mode.DEMO
                    : getIntent().getBooleanExtra("game_intro_instant_demo", false))
                ? getString(R.string.game_intro_ui_5)
                : getString(R.string.game_intro_ui_6))
            : getString(R.string.game_intro_ui_7);
    if (integrationRoute || (BuildConfig.DEBUG && getIntent().hasExtra("game_intro_debug_run")))
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
      label += getString(R.string.game_intro_ui_14);
    }
    if (s.stage == IntroQuizController.Stage.ERROR)
      label += "\n" + getString(R.string.game_intro_storage_error);
    ((TextView) findViewById(R.id.game_intro_status)).setText(label);
    ((TextView) findViewById(R.id.game_intro_caption)).setText(sentences[s.sentence]);
    findViewById(R.id.game_quiz_start).setEnabled(wait && s.eligible);
    findViewById(R.id.game_quiz_submit).setEnabled(quiz && s.complete());
    LinearLayout list = findViewById(R.id.game_quiz_questions);
    list.removeAllViews();
    if (quiz && s.progress.active != null)
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
                        ? getString(R.string.game_intro_ui_15)
                        : getString(R.string.game_intro_ui_16))
                    + " ("
                    + s.result.correct
                    + "/3)"
                : "");
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
