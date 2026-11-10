package com.example.firststep.game.ui.authored;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.R;
import com.example.firststep.game.composition.authored.AuthoredEpisodeHost;
import com.example.firststep.game.content.GuideAdapters;
import com.example.firststep.game.content.GuideBundle;
import com.example.firststep.game.content.NarrationContent;
import com.example.firststep.game.content.NarrationState;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.speech.IntroSpeech;
import com.example.firststep.game.sync.OutboxState;
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
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Map<String, String> answers = new LinkedHashMap<>();
  private volatile long generation;
  private long syncGeneration;
  private boolean resumed, busy, syncing, waiting, checkingOwner, quizEligible;
  private Runnable pendingSyncRender;
  private AuthoredEpisodeHost workerHost;
  private AuthoredEpisodeHost host;
  private Progress progress;
  private Progress.Result result;
  private NarrationState narration;
  private IntroSpeech speech;
  private TextView state, sync, speechStatus;
  private Button syncRetry;
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
    setContentView(R.layout.game_authored_episode);
    ViewCompat.setAccessibilityHeading(findViewById(R.id.game_authored_title), true);
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_authored_scroll), (v, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    state = findViewById(R.id.game_authored_state);
    sync = findViewById(R.id.game_authored_sync);
    syncRetry = findViewById(R.id.game_authored_sync_retry);
    syncRetry.setOnClickListener(v -> sendProgress());
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
    syncGeneration++;
    host = null;
    progress = null;
    result = null;
    narration = null;
    waiting = false;
    checkingOwner = false;
    quizEligible = false;
    pendingSyncRender = null;
    busy = false;
    syncing = false;
    answers.clear();
    closeSpeech();
    body.removeAllViews();
    sync.setText("");
    syncRetry.setVisibility(View.GONE);
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
      main.post(() -> {
        if (!live(token)) return;
        checkingOwner = false;
        if (!accepted) {
          invalidate();
          blocked(R.string.game_authored_no_account, true);
        } else if (quizReady && waiting && observed == progress && !busy && !syncing) {
          renderCurrent(token, () -> {
            quizEligible = true;
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
    worker.execute(() -> {
      if (token != generation) return;
      try {
        AuthoredEpisodeHost.OpenResult opened = AuthoredEpisodeHost.openProduction(app);
        if (opened.host == null) {
          main.post(() -> {
            if (live(token)) showBlock(opened.reason);
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
            observeSync(token, reconciliation);
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
    if (!resumed || host == null || busy) return;
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
            reconcile ? current.reconciliation() : null;
        main.post(() -> renderCurrent(token, () -> {
          busy = false;
          apply.accept(value);
          if (value instanceof Progress) quizEligible = eligible;
          renderBody();
          if (reconciliation != null) observeSync(token, reconciliation);
          else renderPendingSync();
        }));
      } catch (RuntimeException failure) {
        main.post(() -> renderCurrent(token, () -> {
          busy = false;
          waiting = false;
          blocked(R.string.game_authored_operation_failed, true);
          updateSyncButton();
          renderPendingSync();
        }));
      }
    });
  }

  private void renderBody() {
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
      renderCaption();
    } else if (quizEligible) {
      state.setText(R.string.game_authored_quiz_ready);
      AuthoredEpisodeHost current = host;
      action(R.string.game_authored_start_quiz,
          () -> change(current::startQuiz, saved -> progress = saved, false));
    } else {
      waiting = true;
      state.setText(R.string.game_authored_wait);
    }
    updateSyncButton();
  }

  private void renderCaption() {
    state.setText(R.string.game_authored_intro);
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
      if (!live(token) || busy || narration == null || progress.introDoneAt != null) return;
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
    } else {
      action(R.string.game_authored_finish_intro,
          () -> change(current::finishIntroduction, saved -> progress = saved, true));
    }
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

  private void sendProgress() {
    if (!resumed || host == null || syncing || busy || progress.introDoneAt == null) return;
    AuthoredEpisodeHost current = host;
    final long token = generation;
    CompletableFuture<OutboxState> update = new CompletableFuture<>();
    observeSync(token, update);
    worker.execute(() -> {
      if (token != generation) {
        update.completeExceptionally(new CancellationException());
        return;
      }
      try {
        current.reconcile().thenCompose(outbox ->
            token == generation ? current.flush() : CompletableFuture.completedFuture(outbox))
            .whenComplete((outbox, failure) -> {
              if (failure == null) update.complete(outbox);
              else update.completeExceptionally(failure);
            });
      } catch (RuntimeException failure) {
        update.completeExceptionally(failure);
      }
    });
  }

  private void observeSync(long token, CompletableFuture<OutboxState> update) {
    syncing = true;
    pendingSyncRender = null;
    final long request = ++syncGeneration;
    sync.setText(R.string.game_authored_sync_checking);
    updateSyncButton();
    update.whenComplete((outbox, failure) -> main.post(() -> {
      if (!live(token) || request != syncGeneration) return;
      Runnable render = () -> renderSync(outbox, failure);
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
    syncing = false;
    if (failure != null) {
      sync.setText(R.string.game_authored_sync_failed);
    } else {
      switch (outbox.status) {
        case EMPTY: sync.setText(""); break;
        case PENDING: sync.setText(R.string.game_authored_sync_pending); break;
        case CONFIRMED:
          sync.setText(outbox.confirmed.completion == null
              ? R.string.game_authored_sync_intro_confirmed : R.string.game_authored_sync_pass_confirmed);
          break;
        case RETRYABLE_FAILURE:
        case PERMANENT_FAILURE: sync.setText(R.string.game_authored_sync_failed); break;
      }
    }
    updateSyncButton();
  }

  private void updateSyncButton() {
    boolean saved = progress != null && progress.introDoneAt != null;
    syncRetry.setVisibility(saved ? View.VISIBLE : View.GONE);
    syncRetry.setEnabled(saved && !syncing && !busy);
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
    button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
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
