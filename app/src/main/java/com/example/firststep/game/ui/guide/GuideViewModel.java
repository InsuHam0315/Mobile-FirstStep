package com.example.firststep.game.ui.guide;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.ViewModel;
import com.example.firststep.game.content.*;
import java.util.*;
import java.util.concurrent.*;

/** Transient screen coordination only. Practice rules live in the pure PracticeSession. */
public final class GuideViewModel extends ViewModel {
  public enum Panel {
    GUIDE,
    PRACTICE,
    RESULT
  }

  public static final class Saved {
    final String datasetId, panel;
    final int version;
    final Map<String, String> answers;
    final boolean submitted, valid;

    public Saved(
        String datasetId,
        int version,
        String panel,
        Map<String, String> answers,
        boolean submitted,
        boolean valid) {
      this.datasetId = datasetId;
      this.version = version;
      this.panel = panel;
      this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
      this.submitted = submitted;
      this.valid = valid;
    }
  }

  final GuideAdapters.Environment environment;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  GuideBundle guide;
  PracticeSession practice;
  Panel panel = Panel.GUIDE;
  boolean loading, unavailable, sourceUnavailable;
  private boolean closed;
  private long request;
  private Runnable listener;
  private Saved saved;

  GuideViewModel(GuideAdapters.Environment environment, Saved saved) {
    this.environment = environment;
    this.saved = saved;
    reload(false);
  }

  void observe(Runnable listener) {
    this.listener = listener;
    changed();
  }

  private void changed() {
    if (listener != null) listener.run();
  }

  void reload(boolean reset) {
    if (closed) return;
    if (reset) saved = null;
    loading = true;
    unavailable = false;
    sourceUnavailable = false;
    guide = null;
    practice = null;
    long token = ++request;
    changed();
    worker.execute(
        () -> {
          GuideBundle loaded;
          try {
            loaded = environment.loader.load();
          } catch (Exception failure) {
            main.post(() -> finishFailure(token));
            return;
          }
          main.post(
              () -> {
                if (closed || token != request) return;
                try {
                  if (loaded == null || !GuideDecoder.DATASET_ID.equals(loaded.getDatasetId()))
                    throw new IllegalArgumentException("Unregistered guide");
                  PracticeSession restored;
                  Panel restoredPanel = Panel.GUIDE;
                  if (saved != null) {
                    if (!saved.valid
                        || !loaded.getDatasetId().equals(saved.datasetId)
                        || loaded.getVersion() != saved.version)
                      throw new IllegalArgumentException("Invalid saved guide");
                    restored = PracticeSession.restore(loaded, saved.answers, saved.submitted);
                    restoredPanel = Panel.valueOf(saved.panel);
                    if (restoredPanel == Panel.RESULT && !restored.isSubmitted()
                        || restoredPanel == Panel.PRACTICE && restored.isSubmitted())
                      throw new IllegalArgumentException("Invalid saved panel");
                  } else restored = new PracticeSession(loaded);
                  guide = loaded;
                  practice = restored;
                  panel = restoredPanel;
                  saved = null;
                  loading = false;
                  changed();
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                  finishFailure(token);
                }
              });
        });
  }

  private void finishFailure(long token) {
    if (closed || token != request) return;
    loading = false;
    unavailable = true;
    guide = null;
    practice = null;
    changed();
  }

  void startPractice() {
    if (practice == null) return;
    panel = practice.isSubmitted() ? Panel.RESULT : Panel.PRACTICE;
    sourceUnavailable = false;
    changed();
  }

  void showGuide() {
    if (guide != null) {
      panel = Panel.GUIDE;
      sourceUnavailable = false;
      changed();
    }
  }

  void select(String questionId, String optionId) {
    if (practice == null || panel != Panel.PRACTICE) return;
    practice.select(questionId, optionId);
    changed();
  }

  void submit() {
    if (practice == null
        || panel != Panel.PRACTICE
        || practice.getAnswers().size() != guide.getQuestions().size()) return;
    practice.submit();
    panel = Panel.RESULT;
    changed();
  }

  void retry() {
    if (practice != null) {
      practice.retry();
      panel = Panel.PRACTICE;
      changed();
    }
  }

  void openSource(GuideBundle.Source source) {
    sourceUnavailable = !environment.opener.open(source);
    changed();
  }

  Saved snapshot() {
    if (guide == null || practice == null) return saved;
    return new Saved(
        guide.getDatasetId(),
        guide.getVersion(),
        panel.name(),
        practice.getAnswers(),
        practice.isSubmitted(),
        true);
  }

  @Override
  protected void onCleared() {
    closed = true;
    ++request;
    listener = null;
    worker.shutdownNow();
  }
}
