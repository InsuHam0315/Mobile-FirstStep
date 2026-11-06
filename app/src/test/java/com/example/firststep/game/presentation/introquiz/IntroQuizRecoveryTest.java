package com.example.firststep.game.presentation.introquiz;

import static org.junit.Assert.*;

import com.example.firststep.game.composition.introquiz.DiscoveryIntroTarget;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;

public class IntroQuizRecoveryTest {
  static final String B = "b";

  static class Repo implements ProgressRepository {
    final Map<Progress.Mode, Progress> rows = new EnumMap<>(Progress.Mode.class);
    boolean fail;
    int reads;

    public Progress read(Progress.Mode mode, String id) {
      reads++;
      if (fail) throw new IllegalStateException();
      return rows.getOrDefault(mode, Progress.empty(mode, id));
    }

    public List<Progress> readAll(Progress.Mode mode) {
      return Collections.singletonList(read(mode, B));
    }

    public Progress atomicUpdate(Progress.Mode mode, String id, Transition transition) {
      Progress p = transition.apply(read(mode, id));
      rows.put(mode, p);
      return p;
    }
  }

  static class Fixture {
    Repo repo = new Repo();
    long now = 1000;
    int seq;
    ProgressService service;

    Fixture() {
      List<GameContent.Question> q = new ArrayList<>();
      for (int n = 0; n < 3; n++)
        q.add(
            new GameContent.Question(
                "q" + n,
                "question",
                "a",
                Arrays.asList(
                    new GameContent.Option("a", "yes"), new GameContent.Option("b", "no"))));
      GameContent content =
          new GameContent("v1", Collections.singletonList(new GameContent.Building(B, "label", q)));
      service =
          new ProgressService(
              content,
              repo,
              () -> now,
              new ProgressService.AttemptFactory() {
                public String nextId() {
                  return "id" + (++seq);
                }

                public void shuffle(List<?> list) {}
              });
    }

    IntroQuizController controller(Progress.Mode mode, boolean instant, Executor work) {
      return new IntroQuizController(
          service,
          mode,
          B,
          instant,
          3,
          work,
          Runnable::run,
          s -> {},
          new IntroQuizController.Speech() {
            public void speak(int i) {}

            public void stop() {}
          });
    }

    IntroQuizController started() {
      IntroQuizController c = controller(Progress.Mode.NORMAL, false, Runnable::run);
      c.resume();
      c.finish();
      now += ProgressService.WAIT_MILLIS;
      c.start();
      return c;
    }
  }

  @Test
  public void recreationRestoresOnlyMatchingDraftAndResult() {
    Fixture f = new Fixture();
    IntroQuizController a = f.started();
    a.choose("q0", "b");
    Progress.Attempt attempt = a.snapshot().progress.active;
    IntroQuizController b = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    b.restore(
        1, false, a.snapshot().answers, attempt.id, attempt.contentVersion, null, null, null, null);
    b.resume();
    assertEquals("b", b.snapshot().answers.get("q0"));
    b.choose("q1", "b");
    b.choose("q2", "b");
    b.submit();
    Progress.Result failed = b.snapshot().result;
    IntroQuizController c = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    c.restore(0, false, Collections.emptyMap(), null, null, failed.attemptId, null, null, null);
    c.resume();
    assertEquals(IntroQuizController.Stage.RESULT, c.snapshot().stage);
    assertEquals(failed.attemptId, c.snapshot().result.attemptId);
    c.retry();
    assertEquals(IntroQuizController.Stage.QUIZ, c.snapshot().stage);
    assertNotEquals(attempt.id, c.snapshot().progress.active.id);
    IntroQuizController d = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    d.restore(
        0,
        false,
        Collections.singletonMap("q0", "a"),
        attempt.id,
        attempt.contentVersion,
        null,
        null,
        null,
        null);
    d.resume();
    assertTrue(d.snapshot().answers.isEmpty());
  }

  @Test
  public void pendingSubmissionIsReconciledIdempotently() {
    Fixture f = new Fixture();
    IntroQuizController a = f.started();
    for (int n = 0; n < 3; n++) a.choose("q" + n, "a");
    Progress.Attempt attempt = a.snapshot().progress.active;
    Map<String, String> answers = a.snapshot().answers;
    IntroQuizController b = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    b.restore(
        0,
        false,
        answers,
        attempt.id,
        attempt.contentVersion,
        null,
        attempt.id,
        attempt.contentVersion,
        answers);
    b.resume();
    assertTrue(b.snapshot().result.passed);
    assertEquals(1, f.repo.read(Progress.Mode.NORMAL, B).history.size());
    IntroQuizController c = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    c.restore(
        0,
        false,
        answers,
        attempt.id,
        attempt.contentVersion,
        null,
        attempt.id,
        attempt.contentVersion,
        answers);
    c.resume();
    assertTrue(c.snapshot().result.passed);
    assertEquals(1, f.repo.read(Progress.Mode.NORMAL, B).history.size());
  }

  @Test
  public void demoIsIsolatedAndInstantWithoutNotification() {
    Fixture f = new Fixture();
    IntroQuizController normal = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    normal.resume();
    normal.finish();
    IntroQuizController demo = f.controller(Progress.Mode.DEMO, true, Runnable::run);
    demo.resume();
    demo.finish();
    assertTrue(demo.eligible());
    assertFalse(normal.eligible());
    demo.start();
    assertNotNull(demo.snapshot().progress.active);
    assertNull(f.repo.read(Progress.Mode.NORMAL, B).active);
    f.now += ProgressService.WAIT_MILLIS;
    normal.resume();
    assertTrue(normal.eligible());
  }

  @Test
  public void storageFailureRetryDoesNotEraseProgress() {
    Fixture f = new Fixture();
    IntroQuizController c = f.started();
    String id = c.snapshot().progress.active.id;
    f.repo.fail = true;
    c.pause();
    c.resume();
    assertEquals(IntroQuizController.Stage.ERROR, c.snapshot().stage);
    f.repo.fail = false;
    c.retry();
    assertEquals(IntroQuizController.Stage.QUIZ, c.snapshot().stage);
    assertEquals(id, c.snapshot().progress.active.id);
  }

  @Test
  public void staleStorageCallbackAfterPauseCannotRender() {
    Fixture f = new Fixture();
    List<Runnable> queue = new ArrayList<>();
    IntroQuizController c = f.controller(Progress.Mode.NORMAL, false, queue::add);
    c.resume();
    c.pause();
    queue.remove(0).run();
    assertEquals(IntroQuizController.Stage.LOADING, c.snapshot().stage);
    c.resume();
    queue.remove(0).run();
    assertEquals(IntroQuizController.Stage.INTRO, c.snapshot().stage);
  }

  @Test
  public void discoveryRejectsSyntheticCover() {
    DiscoveryResult cover =
        new DiscoveryResult(
            1,
            "synthetic_cover_demo",
            DiscoveryResult.Method.LIGHT,
            DiscoveryResult.Source.SIMULATED,
            true,
            true);
    try {
      DiscoveryIntroTarget.from(cover);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    DiscoveryResult valid =
        new DiscoveryResult(
            2,
            "digital_information_software",
            DiscoveryResult.Method.SHAKE,
            DiscoveryResult.Source.SIMULATED,
            true,
            true);
    assertSame(valid, DiscoveryIntroTarget.from(valid).provenance);
  }

  @Test
  public void pausedActionsCannotWriteAndChangedAttemptDiscardsDraft() {
    Fixture f = new Fixture();
    IntroQuizController c = f.started();
    c.choose("q0", "a");
    c.pause();
    c.submit();
    assertTrue(f.repo.read(Progress.Mode.NORMAL, B).history.isEmpty());
    Progress.Attempt old = f.repo.read(Progress.Mode.NORMAL, B).active;
    Map<String, String> wrong = new LinkedHashMap<>();
    for (int n = 0; n < 3; n++) wrong.put("q" + n, "b");
    f.service.submit(Progress.Mode.NORMAL, B, old.id, old.contentVersion, wrong);
    c.resume();
    assertTrue(c.snapshot().answers.isEmpty());
    assertNotEquals(old.id, c.snapshot().progress.active.id);
  }

  @Test
  public void foregroundTickCrossesBoundaryWithoutStorageOrPausedRender() {
    Fixture f = new Fixture();
    int[] renders = {0};
    IntroQuizController c =
        new IntroQuizController(
            f.service,
            Progress.Mode.NORMAL,
            B,
            false,
            3,
            Runnable::run,
            Runnable::run,
            s -> renders[0]++,
            new IntroQuizController.Speech() {
              public void speak(int i) {}

              public void stop() {}
            },
            () -> f.now);
    c.resume();
    c.finish();
    int reads = f.repo.reads;
    f.now += ProgressService.WAIT_MILLIS - 1;
    c.tick();
    assertEquals(1, c.snapshot().remainingMillis);
    assertFalse(c.snapshot().eligible);
    assertEquals(reads, f.repo.reads);
    f.now++;
    c.tick();
    assertEquals(0, c.snapshot().remainingMillis);
    assertTrue(c.snapshot().eligible);
    assertEquals(reads, f.repo.reads);
    c.pause();
    int before = renders[0];
    c.tick();
    assertEquals(before, renders[0]);
    assertEquals(reads, f.repo.reads);
  }

  @Test
  public void replayIntentSurvivesInterruptedReload() {
    Fixture f = new Fixture();
    f.service.intro(Progress.Mode.NORMAL, B, false);
    List<Runnable> queue = new ArrayList<>();
    IntroQuizController c = f.controller(Progress.Mode.NORMAL, false, queue::add);
    c.resume();
    queue.remove(0).run();
    c.showIntro();
    c.next();
    c.pause();
    c.resume();
    assertEquals(IntroQuizController.Stage.LOADING, c.snapshot().stage);
    assertTrue(c.wantsIntroduction());
    c.pause();
    c.resume();
    queue.remove(0).run(); // Obsolete completion must not decide the new screen.
    queue.remove(0).run();
    assertEquals(IntroQuizController.Stage.INTRO, c.snapshot().stage);
    assertEquals(1, c.snapshot().sentence);
    assertEquals(Long.valueOf(1000), c.snapshot().progress.introDoneAt);
    c.pause();
    c.resume();
    IntroQuizController recreated = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    recreated.restore(c.snapshot().sentence, c.wantsIntroduction(), null, null, null, null, null, null, null);
    c.pause();
    recreated.resume();
    queue.remove(0).run();
    assertEquals(IntroQuizController.Stage.INTRO, recreated.snapshot().stage);
    assertEquals(1, recreated.snapshot().sentence);
  }

  @Test
  public void passedReplaySurvivesRecreationUntilExplicitFinish() {
    Fixture f = new Fixture();
    IntroQuizController c = f.started();
    for (int n = 0; n < 3; n++) c.choose("q" + n, "a");
    c.submit();
    c.showIntro();
    c.next();
    IntroQuizController recreated = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    recreated.restore(c.snapshot().sentence, true, null, null, null, null, null, null, null);
    recreated.resume();
    assertEquals(IntroQuizController.Stage.INTRO, recreated.snapshot().stage);
    assertEquals(1, recreated.snapshot().sentence);
    recreated.finish();
    assertEquals(IntroQuizController.Stage.RESULT, recreated.snapshot().stage);
    assertEquals(1, recreated.snapshot().progress.history.size());
    assertEquals(Long.valueOf(1000), recreated.snapshot().progress.introDoneAt);
  }

  @Test
  public void failedResultIntentSurvivesRecreationDuringReload() {
    Fixture f = new Fixture();
    IntroQuizController c = f.started();
    for (int n = 0; n < 3; n++) c.choose("q" + n, "b");
    c.submit();
    String result = c.snapshot().result.attemptId;
    List<Runnable> queue = new ArrayList<>();
    IntroQuizController loading = f.controller(Progress.Mode.NORMAL, false, queue::add);
    loading.restore(0, false, null, null, null, result, null, null, null);
    loading.resume();
    assertEquals(IntroQuizController.Stage.LOADING, loading.snapshot().stage);
    assertEquals(result, loading.resultIdToRestore());
    IntroQuizController recreated = f.controller(Progress.Mode.NORMAL, false, Runnable::run);
    recreated.restore(0, loading.wantsIntroduction(), null, null, null,
        loading.resultIdToRestore(), null, null, null);
    loading.pause();
    queue.remove(0).run();
    recreated.resume();
    assertEquals(IntroQuizController.Stage.RESULT, recreated.snapshot().stage);
    assertEquals(result, recreated.snapshot().result.attemptId);
    assertFalse(recreated.snapshot().result.passed);
    assertEquals(1, recreated.snapshot().progress.history.size());
  }
}
