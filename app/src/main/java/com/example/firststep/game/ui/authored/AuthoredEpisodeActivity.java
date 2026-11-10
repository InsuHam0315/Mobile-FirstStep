package com.example.firststep.game.ui.authored;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.composition.authored.AuthoredEpisodeHost;
import com.example.firststep.game.content.GuideAdapters;
import com.example.firststep.game.content.GuideBundle;
import com.example.firststep.game.content.NarrationContent;
import com.example.firststep.game.content.NarrationState;
import com.example.firststep.game.device.authored.ArrivalPolicy;
import com.example.firststep.game.device.authored.ForegroundArrival;
import com.example.firststep.game.device.authored.NormalDiscoverySession;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.notifications.authored.AuthoredReminder;
import com.example.firststep.game.notifications.authored.AuthoredReminderAndroid;
import com.example.firststep.game.presentation.authored.AuthoredReminderEntryBinding;
import com.example.firststep.game.presentation.authored.AuthoredSyncPublication;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.speech.IntroSpeech;
import com.example.firststep.game.ui.GameVisuals;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SyncScope;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Separate, account-bound NORMAL entry. All progress work runs on the serial worker. */
public final class AuthoredEpisodeActivity extends AppCompatActivity {
  private static final int LOCATION_REQUEST = 601;
  private static final int CAMERA_REQUEST = 602;
  private static final int REMINDER_REQUEST = 603;

  interface ArrivalInput {
    void start();
    void retry();
    void stop();
    ArrivalPolicy.Verdict currentVerdict();
  }

  interface DiscoveryInput {
    void start();
    void close();
  }

  interface DebugRuntime {
    AuthoredEpisodeHost.OpenResult open(Context context, SyncScope expectedOwner);
    default ArrivalInput arrival(ForegroundArrival.Listener listener) { return null; }
    default DiscoveryInput discovery(NormalDiscoverySession session) { return null; }
    default DiscoveryController.Clock discoveryClock() { return SystemClock::elapsedRealtime; }
  }

  private static volatile DebugRuntime installedDebugRuntime;

  /** A process-local lease for tests in this package. Release builds cannot install it. */
  static synchronized AutoCloseable installDebugRuntime(DebugRuntime runtime) {
    if (!BuildConfig.DEBUG) throw new IllegalStateException("Debug-only runtime");
    if (runtime == null || installedDebugRuntime != null) {
      throw new IllegalArgumentException("Expected one isolated debug runtime");
    }
    installedDebugRuntime = runtime;
    return () -> {
      synchronized (AuthoredEpisodeActivity.class) {
        if (installedDebugRuntime == runtime) installedDebugRuntime = null;
      }
    };
  }

  public static final class ReminderEntryState extends ViewModel {
    private final AuthoredReminderEntryBinding binding = new AuthoredReminderEntryBinding();
  }

  private final AuthoredSyncPublication publication = new AuthoredSyncPublication();
  private AuthoredReminderEntryBinding reminderEntry;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Map<String, String> answers = new LinkedHashMap<>();
  private volatile long generation;
  private boolean resumed, busy, waiting, checkingOwner, quizEligible;
  private long discoveryGeneration, arrivalGeneration, arrivalObservation;
  private boolean locationPermissionDeclined;
  private ArrivalInput arrival;
  private DebugRuntime debugRuntime;
  private ForegroundArrival.State arrivalState;
  private AuthoredDiscoveryView discoveryView;
  private NormalDiscoverySession.Receipt discovered;
  private Runnable pendingSyncRender;
  private AuthoredEpisodeHost workerHost;
  private AuthoredEpisodeHost host;
  private AuthoredEpisodeHost pendingReminderPermission, grantedReminderPermission;
  private Progress progress;
  private Progress.Result result;
  private NarrationState narration;
  private IntroSpeech speech;
  private TextView state, sync, remote, speechStatus;
  private Button syncRetry, syncRead;
  private LinearLayout body;
  private final Runnable checkCurrent = new Runnable() {
    @Override public void run() {
      checkOwner();
      if (resumed) main.postDelayed(this, 1000L);
    }
  };

  public static Intent intent(Context context) {
    return new Intent(context, AuthoredEpisodeActivity.class);
  }

  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    debugRuntime = BuildConfig.DEBUG ? installedDebugRuntime : null;
    reminderEntry = new ViewModelProvider(this).get(ReminderEntryState.class).binding;
    setContentView(R.layout.game_authored_episode);
    findViewById(R.id.game_authored_synthetic_runtime)
        .setVisibility(debugRuntime == null ? View.GONE : View.VISIBLE);
    ViewCompat.setAccessibilityHeading(findViewById(R.id.game_authored_title), true);
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_authored_scroll), (v, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    state = findViewById(R.id.game_authored_state);
    sync = findViewById(R.id.game_authored_sync);
    remote = findViewById(R.id.game_authored_remote);
    syncRead = findViewById(R.id.game_authored_sync_read);
    syncRead.setOnClickListener(v -> requestSync(true));
    syncRetry = findViewById(R.id.game_authored_sync_retry);
    syncRetry.setOnClickListener(v -> requestSync(false));
    body = findViewById(R.id.game_authored_body);
    findViewById(R.id.game_authored_back).setOnClickListener(v -> finish());
  }

  @Override protected void onResume() {
    super.onResume();
    resumed = true;
    open();
    main.postDelayed(checkCurrent, 1000L);
  }

  @Override protected void onPause() {
    resumed = false;
    main.removeCallbacks(checkCurrent);
    invalidate();
    state.setText(R.string.game_authored_opening);
    super.onPause();
  }

  @Override protected void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }

  private void closeWorkerHost() {
    if (workerHost == null) return;
    workerHost.close();
    workerHost = null;
  }

  private void invalidate() {
    generation++;
    publication.reset();
    closeDiscovery();
    stopArrival();
    discovered = null;
    host = null;
    progress = null;
    result = null;
    narration = null;
    waiting = false;
    checkingOwner = false;
    quizEligible = false;
    pendingSyncRender = null;
    busy = false;
    answers.clear();
    closeSpeech();
    body.removeAllViews();
    sync.setText("");
    remote.setText("");
    syncRetry.setVisibility(View.GONE);
    syncRead.setVisibility(View.GONE);
    worker.execute(this::closeWorkerHost);
  }

  private boolean live(long token) {
    return resumed && token == generation && !isFinishing();
  }

  private void checkOwner() {
    AuthoredEpisodeHost current = host;
    if (!resumed || current == null || checkingOwner) return;
    checkingOwner = true;
    final long token = generation;
    final Progress observed = waiting ? progress : null;
    final ArrivalInput observingArrival = arrivalState == ForegroundArrival.State.ADMITTED
        ? arrival : null;
    final long observation = arrivalObservation;
    worker.execute(() -> {
      if (token != generation) return;
      boolean valid = current.isCurrent();
      boolean ready = false;
      if (valid && observed != null) {
        try {
          ready = current.eligible(observed);
        } catch (IllegalStateException stale) {
          valid = false;
        }
      }
      final boolean accepted = valid;
      final boolean quizReady = ready;
      final ArrivalPolicy.Verdict arrivalVerdict =
          valid && observingArrival != null ? observingArrival.currentVerdict() : null;
      main.post(() -> {
        if (!live(token)) return;
        checkingOwner = false;
        if (!accepted) {
          invalidate();
          blocked(R.string.game_authored_no_account, true);
        } else if (quizReady && waiting && observed == progress && !busy && !publication.explicitRunning()) {
          renderCurrent(token, () -> {
            quizEligible = true;
            renderBody();
          });
        } else if (arrivalVerdict != null && arrivalVerdict != ArrivalPolicy.Verdict.ADMITTED
            && observingArrival == arrival && observation == arrivalObservation
            && arrivalState == ForegroundArrival.State.ADMITTED
            && !busy && discoveryView == null && discovered == null) {
          renderCurrent(token, () -> {
            arrivalState = ForegroundArrival.State.valueOf(arrivalVerdict.name());
            renderBody();
          });
        }
      });
    });
  }

  private void open() {
    invalidate();
    final long token = generation;
    state.setText(R.string.game_authored_opening);
    Context app = getApplicationContext();
    Intent launch = getIntent();
    worker.execute(() -> {
      if (token != generation) return;
      try {
        SyncScope expected = null;
        if (AuthoredReminderAndroid.isReminderEntry(launch)) {
          try {
            expected = reminderEntry.resolve(launch.getAction() + "\n" + launch.getDataString(),
                () -> AuthoredReminderAndroid.requireCurrentEntry(launch));
          } catch (RuntimeException invalid) {
            main.post(() -> {
              if (live(token)) blocked(R.string.game_authored_reminder_invalid, false);
            });
            return;
          }
        }
        AuthoredEpisodeHost.OpenResult opened = debugRuntime == null
            ? AuthoredEpisodeHost.openProduction(app, expected) : debugRuntime.open(app, expected);
        if (opened.host == null) {
          main.post(() -> {
            if (live(token)) {
              grantedReminderPermission = null;
              showBlock(opened.reason);
            }
          });
          return;
        }
        workerHost = opened.host;
        Progress saved = opened.host.read();
        boolean eligible = opened.host.eligible(saved);
        CompletableFuture<OutboxState> reconciliation = opened.host.reconciliation();
        main.post(() -> {
          if (!live(token)) return;
          host = opened.host;
          renderCurrent(token, () -> {
            progress = saved;
            quizEligible = eligible;
            renderBody();
            observeAutomaticSync(token, reconciliation);
            if (grantedReminderPermission != null) {
              AuthoredEpisodeHost origin = grantedReminderPermission;
              grantedReminderPermission = null;
              requestReminderSchedule(origin);
            }
          });
        });
      } catch (RuntimeException failure) {
        main.post(() -> {
          if (live(token)) {
            invalidate();
            blocked(R.string.game_authored_operation_failed, true);
          }
        });
      }
    });
  }

  private void showBlock(AuthoredEpisodeHost.Block reason) {
    switch (reason) {
      case NO_ACCOUNT: blocked(R.string.game_authored_no_account, true); break;
      case UNREGISTERED: blocked(R.string.game_authored_unregistered, false); break;
      case ARRIVAL_UNAVAILABLE: blocked(R.string.game_authored_arrival_unavailable, true); break;
      case NOT_ARRIVED: blocked(R.string.game_authored_not_arrived, true); break;
      case INVALID_CONTENT: blocked(R.string.game_authored_content_unavailable, true); break;
    }
  }

  private void blocked(int message, boolean retry) {
    state.setText(message);
    body.removeAllViews();
    if (retry) action(R.string.game_authored_check_again, this::open);
  }

  private void renderCurrent(long token, Runnable render) {
    if (!live(token) || host == null) return;
    if (!host.renderIfCurrent(render)) {
      invalidate();
      blocked(R.string.game_authored_no_account, true);
    }
  }

  private <T> void change(Supplier<T> operation, Consumer<T> apply, boolean reconcile) {
    if (!resumed || host == null || busy || publication.explicitRunning()) return;
    busy = true;
    closeSpeech();
    updateSyncButton();
    final long token = generation;
    final AuthoredEpisodeHost current = host;
    worker.execute(() -> {
      if (token != generation) return;
      try {
        T value = operation.get();
        boolean eligible = value instanceof Progress && current.eligible((Progress) value);
        CompletableFuture<OutboxState> reconciliation =
            reconcile && (!(value instanceof Progress.Result) || ((Progress.Result) value).passed)
                ? current.reconciliation() : null;
        main.post(() -> renderCurrent(token, () -> {
          busy = false;
          apply.accept(value);
          if (value instanceof Progress) quizEligible = eligible;
          renderBody();
          observeAutomaticSync(token, reconciliation);
          renderPendingSync();
        }));
      } catch (RuntimeException failure) {
        main.post(() -> renderCurrent(token, () -> {
          busy = false;
          waiting = false;
          closeDiscovery();
          stopArrival();
          blocked(R.string.game_authored_operation_failed, true);
          updateSyncButton();
          renderPendingSync();
        }));
      }
    });
  }

  private void renderBody() {
    if (discoveryView != null) return;
    if (progress.introDoneAt != null) stopArrival();
    body.removeAllViews();
    waiting = false;
    if (result != null) {
      renderResult(result);
    } else if (progress.stamp) {
      for (Progress.Result item : progress.history.values()) {
        if (item.passed) {
          renderResult(item);
          break;
        }
      }
    } else if (progress.active != null) {
      renderQuiz(progress.active);
    } else if (progress.introDoneAt == null) {
      if (discovered == null) renderArrival();
      else renderCaption();
    } else if (quizEligible) {
      state.setText(R.string.game_authored_quiz_ready);
      AuthoredEpisodeHost current = host;
      action(R.string.game_authored_start_quiz,
          () -> change(current::startQuiz, saved -> progress = saved, false));
    } else {
      waiting = true;
      state.setText(R.string.game_authored_wait);
      action(R.string.game_authored_reminder_opt_in, this::enableReminder);
      text(getString(R.string.game_authored_reminder_lifetime));
    }
    updateSyncButton();
  }

  private void renderArrival() {
    if (arrival == null) {
      final long token = generation;
      final long requestId = ++arrivalGeneration;
      // ForegroundArrival delivers on main after validating its own request token.
      ForegroundArrival.Listener listener = value -> {
        if (!live(token) || requestId != arrivalGeneration || arrival == null
            || progress == null || progress.introDoneAt != null) return;
        arrivalObservation++;
        if (arrivalState == value) return;
        arrivalState = value;
        if (!busy && discoveryView == null && discovered == null) {
          renderCurrent(token, this::renderBody);
        }
      };
      arrival = debugRuntime == null ? platformArrival(listener) : debugRuntime.arrival(listener);
      if (arrival == null) {
        state.setText(R.string.game_authored_arrival_unavailable);
        return;
      }
      ArrivalInput request = arrival;
      main.post(() -> {
        if (live(token) && arrival == request) request.start();
      });
    }
    if (arrivalState == ForegroundArrival.State.ADMITTED) {
      state.setText(R.string.game_authored_arrival_confirmed);
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
          != PackageManager.PERMISSION_GRANTED) {
        action(R.string.game_authored_camera_permission, () -> ActivityCompat.requestPermissions(
            this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST));
      }
      action(R.string.game_authored_discovery_start, this::beginDiscovery);
      return;
    }
    if (arrivalState == null || arrivalState == ForegroundArrival.State.LOCATING) {
      state.setText(R.string.game_authored_locating);
      return;
    }
    switch (arrivalState) {
      case PERMISSION_REQUIRED:
      case APPROXIMATE_ONLY:
        state.setText(arrivalState == ForegroundArrival.State.APPROXIMATE_ONLY
            ? R.string.game_authored_approximate_only
            : locationPermissionDeclined ? R.string.game_authored_location_denied
                : R.string.game_authored_precise_permission);
        action(R.string.game_authored_location_permission,
            () -> ForegroundArrival.requestPermissions(this, LOCATION_REQUEST));
        return;
      case NOT_ARRIVED: state.setText(R.string.game_authored_not_arrived); break;
      case STALE: state.setText(R.string.game_authored_location_stale); break;
      case LOW_ACCURACY: state.setText(R.string.game_authored_location_inaccurate); break;
      case MOCK: state.setText(R.string.game_authored_location_mock); break;
      default: state.setText(R.string.game_authored_arrival_unavailable);
    }
    action(R.string.game_authored_check_again, () -> {
      arrivalState = ForegroundArrival.State.LOCATING;
      renderBody();
      arrival.retry();
    });
  }

  private ArrivalInput platformArrival(ForegroundArrival.Listener listener) {
    ForegroundArrival platform = host.createForegroundArrival(this, listener);
    if (platform == null) return null;
    return new ArrivalInput() {
      @Override public void start() { platform.start(); }
      @Override public void retry() { platform.retry(); }
      @Override public void stop() { platform.stop(); }
      @Override public ArrivalPolicy.Verdict currentVerdict() { return platform.currentVerdict(); }
    };
  }

  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REMINDER_REQUEST) {
      AuthoredEpisodeHost origin = pendingReminderPermission;
      pendingReminderPermission = null;
      if (origin == null || grantResults.length == 0
          || grantResults[0] != PackageManager.PERMISSION_GRANTED) return;
      if (resumed && host != null) requestReminderSchedule(origin);
      else grantedReminderPermission = origin;
    } else if (requestCode == LOCATION_REQUEST) {
      locationPermissionDeclined = ContextCompat.checkSelfPermission(this,
          Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
      if (resumed && arrival != null) {
        arrivalState = null;
        arrival.retry();
      }
    }
  }

  private void enableReminder() {
    AuthoredEpisodeHost current = host;
    if (!resumed || current == null || !waiting || busy || pendingReminderPermission != null) return;
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      pendingReminderPermission = current;
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS},
          REMINDER_REQUEST);
    } else {
      requestReminderSchedule(current);
    }
  }

  private void requestReminderSchedule(AuthoredEpisodeHost origin) {
    final AuthoredEpisodeHost current = host;
    final long token = generation;
    if (current == null || !waiting) return;
    worker.execute(() -> {
      if (token != generation) return;
      AuthoredReminder.Result outcome;
      try { outcome = current.retryReminder(origin); }
      catch (RuntimeException unavailable) { outcome = AuthoredReminder.Result.UNAVAILABLE; }
      boolean scheduled = outcome == AuthoredReminder.Result.SCHEDULED
          || outcome == AuthoredReminder.Result.ALREADY_SCHEDULED;
      main.post(() -> renderCurrent(token, () -> {
        if (waiting) state.setText(scheduled
            ? R.string.game_authored_reminder_scheduled : R.string.game_authored_reminder_unavailable);
      }));
    });
  }

  private void stopArrival() {
    arrivalGeneration++;
    if (arrival != null) arrival.stop();
    arrival = null;
    arrivalState = null;
  }

  private void beginDiscovery() {
    if (busy || publication.explicitRunning() || host == null || discoveryView != null) return;
    final AuthoredEpisodeHost current = host;
    final long token = generation;
    final long request = ++discoveryGeneration;
    final DiscoveryController.Clock elapsed = debugRuntime == null
        ? SystemClock::elapsedRealtime : debugRuntime.discoveryClock();
    busy = true;
    updateSyncButton();
    worker.execute(() -> {
      if (token != generation) return;
      try {
        NormalDiscoverySession session = current.beginDiscovery(elapsed,
            new NormalDiscoverySession.Listener() {
              @Override public void onState(DiscoveryState value) {
                main.post(() -> {
                  if (live(token) && request == discoveryGeneration && discoveryView != null) {
                    discoveryView.render(value);
                  }
                });
              }

              @Override public void onCompleted(NormalDiscoverySession.Receipt receipt) {
                main.post(() -> {
                  if (live(token) && request == discoveryGeneration && discoveryView != null) {
                    finishDiscovery(receipt);
                  }
                });
              }
            });
        main.post(() -> {
          renderCurrent(token, () -> {
            busy = false;
            state.setText(R.string.game_authored_discovery_active);
            DiscoveryInput input = debugRuntime == null ? null : debugRuntime.discovery(session);
            discoveryView = new AuthoredDiscoveryView(this, body, session, this::cancelDiscovery, input);
            updateSyncButton();
          });
          if (live(token) && request == discoveryGeneration && discoveryView != null) {
            discoveryView.start();
          }
        });
      } catch (RuntimeException unavailable) {
        main.post(() -> renderCurrent(token, () -> {
          busy = false;
          stopArrival();
          renderBody();
        }));
      }
    });
  }

  private void finishDiscovery(NormalDiscoverySession.Receipt receipt) {
    AuthoredEpisodeHost current = host;
    change(() -> current.acceptDiscovery(receipt), accepted -> {
      closeDiscovery();
      if (accepted) discovered = receipt;
      else stopArrival();
    }, false);
  }

  private void cancelDiscovery() {
    if (busy || host == null) return;
    AuthoredEpisodeHost current = host;
    closeDiscovery(); // Invalidates the receipt before any queued callback can be accepted.
    change(() -> {
      current.cancelDiscovery();
      return null;
    }, ignored -> {}, false);
  }

  private void closeDiscovery() {
    discoveryGeneration++;
    if (discoveryView != null) discoveryView.close();
    discoveryView = null;
  }

  private void renderCaption() {
    state.setText(R.string.game_authored_intro);
    int discoveryLabel = discovered.source == DiscoveryResult.Source.LONG_PRESS
        ? R.string.game_discovery_result_hold : R.string.game_discovery_result_real;
    text(getString(discoveryLabel)
        + (discovered.cameraFallback ? getString(R.string.game_discovery_fallback_camera_suffix) : "")
        + (discovered.headingFallback ? getString(R.string.game_discovery_fallback_heading_suffix) : ""));
    AuthoredEpisodeHost current = host;
    NarrationContent content = current.captions();
    if (narration == null) narration = new NarrationState(content.sentences().size(), 0);
    int position = narration.position();
    NarrationContent.Sentence sentence = content.sentences().get(position);
    text(getString(R.string.game_narration_position, position + 1, content.sentences().size()));
    text(sentence.text);
    if (sentence.provenance == NarrationContent.Provenance.CLAIM) {
      GuideBundle.Source source = sourceFor(current.guide(), sentence.claimId);
      if (source != null) {
        TextView link = text(getString(R.string.game_narration_source,
            source.getLabel(), source.getCheckedDate(), source.getUrl()));
        link.setOnClickListener(v -> {
          if (!GuideAdapters.openOfficialSource(this, source))
            state.setText(R.string.game_guide_source_failed);
        });
      }
    } else {
      text(getString(sentence.provenance == NarrationContent.Provenance.FICTIONAL
          ? R.string.game_narration_fictional : R.string.game_narration_editorial));
    }
    speechStatus = text("");
    long token = generation;
    if (speech == null) main.post(() -> {
      if (!live(token) || busy || narration == null || progress == null || progress.introDoneAt != null) return;
      if (speech == null) speech = new IntroSpeech(this, null, message -> {
        if (speechStatus != null) speechStatus.setText(message);
      });
    });
    if (position > 0) action(R.string.game_narration_previous, () -> moveCaption(false));
    action(R.string.game_narration_replay, () -> {
      if (resumed && !busy && speech != null) speech.speak(sentence.text);
    });
    if (position + 1 < content.sentences().size()) {
      action(R.string.game_narration_next, () -> moveCaption(true));
      action(R.string.game_authored_skip_intro, () -> finishIntroduction(current));
    } else {
      action(R.string.game_authored_finish_intro, () -> finishIntroduction(current));
    }
  }

  private void finishIntroduction(AuthoredEpisodeHost current) {
    change(current::finishIntroduction, saved -> progress = saved, true);
  }

  private void moveCaption(boolean next) {
    if (!resumed || busy || narration == null) return;
    if (speech != null) speech.stop();
    if (next) narration.next();
    else narration.previous();
    renderBody();
  }

  private static GuideBundle.Source sourceFor(GuideBundle guide, String claimId) {
    for (GuideBundle.Claim claim : guide.getClaims()) {
      if (claim.getId().equals(claimId)) {
        for (GuideBundle.Source source : guide.getSources()) {
          if (source.getId().equals(claim.getSourceId())) return source;
        }
      }
    }
    return null;
  }

  private void renderQuiz(Progress.Attempt attempt) {
    state.setText(R.string.game_authored_quiz);
    int index = 0;
    Button submit = newButton(R.string.game_authored_submit);
    for (GameContent.Question question : attempt.questions) {
      text(getString(R.string.game_guide_question, ++index, question.text));
      RadioGroup group = new RadioGroup(this);
      group.setOrientation(RadioGroup.VERTICAL);
      for (GameContent.Option option : question.options) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(option.text);
        button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
        group.addView(button);
        if (option.id.equals(answers.get(question.id))) group.check(button.getId());
        button.setOnClickListener(v -> {
          if (!busy) {
            answers.put(question.id, option.id);
            submit.setEnabled(answers.size() == attempt.questions.size());
          }
        });
      }
      body.addView(group);
    }
    submit.setEnabled(answers.size() == attempt.questions.size());
    submit.setOnClickListener(v -> {
      AuthoredEpisodeHost current = host;
      Map<String, String> snapshot = new LinkedHashMap<>(answers);
      change(() -> current.submit(attempt.id, attempt.contentVersion, snapshot), accepted -> {
        answers.clear();
        result = accepted;
      }, true);
    });
    body.addView(submit);
  }

  private void renderResult(Progress.Result saved) {
    state.setText(getString(saved.passed ? R.string.game_authored_saved
        : R.string.game_authored_failed, saved.correct));
    if (!saved.passed) {
      AuthoredEpisodeHost current = host;
      action(R.string.game_authored_retry_quiz, () -> change(current::startQuiz, next -> {
        progress = next;
        result = null;
      }, false));
    }
  }

  private void requestSync(boolean read) {
    if (!resumed || host == null || publication.running() || busy || discoveryView != null
        || (!read && progress.introDoneAt == null)) return;
    AuthoredEpisodeHost current = host;
    final long token = generation;
    CompletableFuture<OutboxState> update = new CompletableFuture<>();
    observeSync(token, publication.beginExplicit(), update);
    worker.execute(() -> {
      if (token != generation) {
        update.completeExceptionally(new CancellationException());
        return;
      }
      try {
        CompletableFuture<OutboxState> operation = read ? current.refresh()
            : current.reconcile().thenCompose(outbox -> token == generation
                ? current.flush() : CompletableFuture.completedFuture(outbox));
        operation.whenComplete((outbox, failure) -> {
          if (failure == null) update.complete(outbox);
          else update.completeExceptionally(failure);
        });
      } catch (RuntimeException failure) {
        update.completeExceptionally(failure);
      }
    });
  }

  private void observeAutomaticSync(long token, CompletableFuture<OutboxState> update) {
    AuthoredSyncPublication.Ticket ticket = publication.observeAutomatic(update);
    if (ticket != null) observeSync(token, ticket, update);
  }

  private void observeSync(long token, AuthoredSyncPublication.Ticket ticket,
      CompletableFuture<OutboxState> update) {
    pendingSyncRender = null;
    sync.setText(R.string.game_authored_sync_checking);
    updateSyncButton();
    update.whenComplete((outbox, failure) -> main.post(() -> {
      if (!live(token) || !publication.accepts(ticket)) return;
      Runnable render = () -> {
        if (publication.complete(ticket)) renderSync(outbox, failure);
      };
      if (busy) pendingSyncRender = render;
      else renderCurrent(token, render);
    }));
  }

  private void renderPendingSync() {
    Runnable render = pendingSyncRender;
    pendingSyncRender = null;
    if (render != null) render.run();
  }

  private void renderSync(OutboxState outbox, Throwable failure) {
    if (failure != null) {
      sync.setText(R.string.game_authored_sync_failed);
    } else {
      if (outbox.confirmed != null) {
        remote.setText(outbox.confirmed.completion == null
            ? getString(R.string.game_authored_sync_intro_confirmed)
            : getString(R.string.game_authored_sync_pass_confirmed,
                outbox.confirmed.completion.correct));
      } else {
        remote.setText(R.string.game_authored_sync_no_remote);
      }
      switch (outbox.status) {
        case EMPTY:
        case CONFIRMED: sync.setText(""); break;
        case PENDING: sync.setText(R.string.game_authored_sync_pending); break;
        case RETRYABLE_FAILURE:
        case PERMANENT_FAILURE: sync.setText(R.string.game_authored_sync_failed); break;
      }
    }
    updateSyncButton();
  }

  private void updateSyncButton() {
    boolean opened = progress != null;
    boolean saved = opened && progress.introDoneAt != null;
    boolean available = !publication.running() && !busy && discoveryView == null;
    syncRetry.setVisibility(saved ? View.VISIBLE : View.GONE);
    syncRetry.setEnabled(saved && available);
    syncRead.setVisibility(opened ? View.VISIBLE : View.GONE);
    syncRead.setEnabled(opened && available);
  }

  private void closeSpeech() {
    if (speech != null) speech.close();
    speech = null;
    speechStatus = null;
  }

  private TextView text(String message) {
    TextView view = new TextView(this);
    view.setText(message);
    view.setTextColor(getColor(R.color.game_color_text));
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
        getResources().getDimension(R.dimen.game_text_body));
    body.addView(view);
    return view;
  }

  private Button newButton(int label) {
    Button button = new Button(this);
    GameVisuals.action(button, label == R.string.game_authored_discovery_start
        || label == R.string.game_authored_finish_intro || label == R.string.game_authored_start_quiz
        || label == R.string.game_authored_submit);
    button.setText(label);
    button.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    return button;
  }

  private void action(int label, Runnable action) {
    Button button = newButton(label);
    button.setOnClickListener(v -> action.run());
    body.addView(button);
  }
}
