package com.example.firststep.game.presentation.introquiz;

import com.example.firststep.game.domain.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Lifecycle-scoped coordinator. All public methods and callbacks run on the UI thread. */
public final class IntroQuizController {
  public enum Error {
    NONE,
    STORAGE
  }

  public enum Stage {
    LOADING,
    INTRO,
    WAITING,
    QUIZ,
    RESULT,
    ERROR
  }

  public interface Observer {
    void render(Snapshot state);
  }

  public interface Speech {
    void speak(int index);

    void stop();
  }

  public static final class Snapshot {
    public final Stage stage;
    public final Progress progress;
    public final Progress.Result result;
    public final int sentence;
    public final Map<String, String> answers;
    public final Error error;
    public final long remainingMillis;
    public final boolean eligible;

    Snapshot(
        Stage stage,
        Progress progress,
        Progress.Result result,
        int sentence,
        Map<String, String> answers,
        Error error,
        long remainingMillis,
        boolean eligible) {
      this.stage = stage;
      this.progress = progress;
      this.result = result;
      this.sentence = sentence;
      this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
      this.error = error;
      this.remainingMillis = remainingMillis;
      this.eligible = eligible;
    }

    public boolean complete() {
      return progress != null && progress.active != null && answers.size() == 3;
    }
  }

  private final ProgressService service;
  private final Progress.Mode mode;
  private final String building;
  private final boolean instant;
  private final int sentenceCount;
  private final Executor worker, main;
  private final Observer observer;
  private final Speech speech;
  private final LongSupplier clock;
  private Stage stage = Stage.LOADING;
  private Progress progress;
  private Progress.Result result;
  private int sentence;
  private final Map<String, String> answers = new LinkedHashMap<>();
  private Error error = Error.NONE;
  // Navigation intent survives temporary LOADING and interrupted repository callbacks.
  private boolean introRequested;
  private String restoredAttempt, restoredVersion, restoredResult;
  private String pendingId, pendingVersion;
  private Map<String, String> pendingAnswers;
  private long generation;
  private boolean busy, active;

  public IntroQuizController(
      ProgressService service,
      Progress.Mode mode,
      String building,
      boolean instant,
      int sentenceCount,
      Executor worker,
      Executor main,
      Observer observer,
      Speech speech) {
    this(
        service,
        mode,
        building,
        instant,
        sentenceCount,
        worker,
        main,
        observer,
        speech,
        System::currentTimeMillis);
  }

  public IntroQuizController(
      ProgressService service,
      Progress.Mode mode,
      String building,
      boolean instant,
      int sentenceCount,
      Executor worker,
      Executor main,
      Observer observer,
      Speech speech,
      LongSupplier clock) {
    this.clock = Objects.requireNonNull(clock);
    this.service = service;
    this.mode = mode;
    this.building = building;
    this.instant = instant;
    if (sentenceCount < 1) throw new IllegalArgumentException("sentence count");
    this.sentenceCount = sentenceCount;
    this.worker = worker;
    this.main = main;
    this.observer = observer;
    this.speech = speech;
  }

  public Snapshot snapshot() {
    boolean canStart = progress != null && service.eligible(progress);
    long remaining = 0;
    if (progress != null && progress.introDoneAt != null && !canStart) {
      long start = progress.introDoneAt;
      long deadline =
          start > Long.MAX_VALUE - ProgressService.WAIT_MILLIS
              ? Long.MAX_VALUE
              : start + ProgressService.WAIT_MILLIS;
      try {
        remaining = Math.max(0, Math.subtractExact(deadline, clock.getAsLong()));
      } catch (ArithmeticException overflow) {
        remaining = Long.MAX_VALUE;
      }
    }
    return new Snapshot(stage, progress, result, sentence, answers, error, remaining, canStart);
  }

  private void render() {
    observer.render(snapshot());
  }

  public void restore(
      int sentence,
      boolean intro,
      Map<String, String> answers,
      String restoredAttempt,
      String restoredVersion,
      String restoredResult,
      String attempt,
      String version,
      Map<String, String> submitted) {
    this.introRequested = intro;
    this.restoredAttempt = restoredAttempt;
    this.restoredVersion = restoredVersion;
    this.restoredResult = restoredResult;
    this.sentence = Math.max(0, Math.min(sentenceCount - 1, sentence));
    this.answers.clear();
    if (answers != null) this.answers.putAll(answers);
    pendingId = attempt;
    pendingVersion = version;
    pendingAnswers = submitted == null ? null : new LinkedHashMap<>(submitted);
  }

  public String pendingId() {
    return pendingId;
  }

  public boolean wantsIntroduction() {
    return introRequested || stage == Stage.INTRO;
  }

  public String resultIdToRestore() {
    return stage == Stage.RESULT && result != null ? result.attemptId : restoredResult;
  }

  public String pendingVersion() {
    return pendingVersion;
  }

  public Map<String, String> pendingAnswers() {
    return pendingAnswers == null ? null : new LinkedHashMap<>(pendingAnswers);
  }

  private interface Work {
    Progress run();
  }

  private void load(Work work, boolean preserveIntro) {
    if (busy || !active) return;
    busy = true;
    stage = Stage.LOADING;
    render();
    final long token = ++generation;
    worker.execute(
        () -> {
          try {
            Progress p = work.run();
            main.execute(
                () -> {
                  if (token != generation) return;
                  busy = false;
                  progress = p;
                  if (pendingId != null && p.history.containsKey(pendingId)) {
                    result = p.history.get(pendingId);
                    pendingId = null;
                    pendingVersion = null;
                    pendingAnswers = null;
                    answers.clear();
                    stage = Stage.RESULT;
                  } else if (pendingId != null) {
                    stage = Stage.ERROR;
                    error = Error.STORAGE;
                  } else if (preserveIntro) {
                    stage = Stage.INTRO;
                  } else if (restoredResult != null && p.history.containsKey(restoredResult)) {
                    result = p.history.get(restoredResult);
                    restoredResult = null;
                    stage = Stage.RESULT;
                  } else if (p.stamp) {
                    result = lastResult(p);
                    stage = Stage.RESULT;
                  } else if (p.introDoneAt == null) stage = Stage.INTRO;
                  else if (p.active != null) {
                    stage = Stage.QUIZ;
                    validateAnswers();
                  } else stage = Stage.WAITING;
                  render();
                });
          } catch (Exception e) {
            main.execute(
                () -> {
                  if (token != generation) return;
                  busy = false;
                  stage = Stage.ERROR;
                  error = Error.STORAGE;
                  render();
                });
          }
        });
  }

  private Progress.Result lastResult(Progress p) {
    Progress.Result r = null;
    for (Progress.Result x : p.history.values()) r = x;
    return r;
  }

  private void validateAnswers() {
    if (restoredAttempt != null
        && (progress.active == null
            || !restoredAttempt.equals(progress.active.id)
            || !Objects.equals(restoredVersion, progress.active.contentVersion))) answers.clear();
    restoredAttempt = null;
    restoredVersion = null;
    if (progress.active == null) {
      answers.clear();
      return;
    }
    Map<String, String> valid = new LinkedHashMap<>();
    for (GameContent.Question q : progress.active.questions) {
      String a = answers.get(q.id);
      for (GameContent.Option o : q.options) if (o.id.equals(a)) valid.put(q.id, a);
    }
    answers.clear();
    answers.putAll(valid);
  }

  // A conflicting payload can mean another host already committed this attempt.
  // Reconcile only the original ID in the same repository key; all other failures remain visible.
  private Progress submitAndReconcile(String id, String version, Map<String, String> submitted) {
    try {
      service.submit(mode, building, id, version, submitted);
    } catch (IllegalArgumentException rejected) {
      Progress current = service.read(mode, building);
      if (!current.history.containsKey(id)) throw rejected;
      return current;
    }
    return service.read(mode, building);
  }

  public void resume() {
    active = true;
    if (restoredAttempt == null
        && progress != null
        && progress.active != null
        && !answers.isEmpty()) {
      restoredAttempt = progress.active.id;
      restoredVersion = progress.active.contentVersion;
    }
    if (stage == Stage.RESULT && result != null) restoredResult = result.attemptId;
    final boolean showIntro = wantsIntroduction();
    introRequested = showIntro;
    final String id = pendingId, ver = pendingVersion;
    final Map<String, String> a =
        pendingAnswers == null ? null : new LinkedHashMap<>(pendingAnswers);
    load(
        () -> {
          if (id != null && ver != null && a != null) return submitAndReconcile(id, ver, a);
          return service.read(mode, building);
        },
        showIntro);
  }

  public void tick() {
    if (active && stage == Stage.WAITING) render();
  }

  public void pause() {
    active = false;
    generation++;
    busy = false;
    speech.stop();
  }

  public void showIntro() {
    if (!active) return;
    if (stage == Stage.WAITING || stage == Stage.RESULT) {
      introRequested = true;
      restoredResult = null;
      stage = Stage.INTRO;
      sentence = 0;
      render();
    }
  }

  public void replay() {
    if (!active) return;
    if (stage == Stage.INTRO) speech.speak(sentence);
  }

  public void next() {
    if (!active) return;
    if (stage == Stage.INTRO) {
      speech.stop();
      sentence = Math.min(sentenceCount - 1, sentence + 1);
      render();
    }
  }

  public void finish() {
    if (!active) return;
    if (stage == Stage.INTRO) {
      speech.stop();
      introRequested = false;
      load(() -> service.intro(mode, building, instant), false);
    }
  }

  public void start() {
    if (!active) return;
    if (stage == Stage.WAITING) load(() -> service.start(mode, building), false);
  }

  public void choose(String qid, String oid) {
    if (!active) return;
    if (stage != Stage.QUIZ || busy) return;
    for (GameContent.Question q : progress.active.questions)
      if (q.id.equals(qid))
        for (GameContent.Option o : q.options)
          if (o.id.equals(oid)) {
            answers.put(qid, oid);
            render();
            return;
          }
  }

  public void submit() {
    if (!active) return;
    if (stage != Stage.QUIZ || busy || !snapshot().complete()) return;
    pendingId = progress.active.id;
    pendingVersion = progress.active.contentVersion;
    pendingAnswers = new LinkedHashMap<>(answers);
    final String id = pendingId, ver = pendingVersion;
    final Map<String, String> a = new LinkedHashMap<>(pendingAnswers);
    load(
        () -> {
          return submitAndReconcile(id, ver, a);
        },
        false);
  }

  public void retry() {
    if (!active) return;
    if (stage == Stage.RESULT && result != null && !result.passed) {
      result = null;
      answers.clear();
      load(() -> service.read(mode, building), false);
    } else if (stage == Stage.ERROR) resume();
  }

  public boolean eligible() {
    return progress != null && service.eligible(progress);
  }
}
