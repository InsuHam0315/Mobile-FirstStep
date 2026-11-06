package com.example.firststep.game.presentation.introquiz;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.*;
import java.util.*;
import org.junit.Test;

public class IntroQuizControllerTest {
  static class Repo implements ProgressRepository {
    Progress p = Progress.empty(Progress.Mode.NORMAL, "b");

    public Progress read(Progress.Mode m, String b) {
      return p;
    }

    public List<Progress> readAll(Progress.Mode m) {
      return Collections.singletonList(p);
    }

    public Progress atomicUpdate(Progress.Mode m, String b, Transition t) {
      return p = t.apply(p);
    }
  }

  static class Fixture {
    long now = 1000;
    Repo repo = new Repo();
    int ids;
    IntroQuizController c;
    int stops;

    Fixture() {
      List<GameContent.Question> qs = new ArrayList<>();
      for (int i = 0; i < 3; i++)
        qs.add(
            new GameContent.Question(
                "q" + i,
                "question",
                "a",
                Arrays.asList(
                    new GameContent.Option("a", "yes"), new GameContent.Option("b", "no"))));
      GameContent content =
          new GameContent(
              "v1", Collections.singletonList(new GameContent.Building("b", "building", qs)));
      ProgressService svc =
          new ProgressService(
              content,
              repo,
              () -> now,
              new ProgressService.AttemptFactory() {
                public String nextId() {
                  return "attempt" + (++ids);
                }

                public void shuffle(List<?> x) {}
              });
      c =
          new IntroQuizController(
              svc,
              Progress.Mode.NORMAL,
              "b",
              false,
              3,
              Runnable::run,
              Runnable::run,
              s -> {},
              new IntroQuizController.Speech() {
                public void speak(int i) {}

                public void stop() {
                  stops++;
                }
              });
      c.resume();
    }
  }

  @Test
  public void finishIsExplicitAndReplayDoesNotMoveDeadline() {
    Fixture f = new Fixture();
    f.c.next();
    f.c.replay();
    assertNull(f.repo.p.introDoneAt);
    f.c.finish();
    long first = f.repo.p.introDoneAt;
    f.now += 100;
    f.c.showIntro();
    f.c.finish();
    assertEquals(first, f.repo.p.introDoneAt.longValue());
    assertFalse(f.c.eligible());
    f.now = first + ProgressService.WAIT_MILLIS;
    assertTrue(f.c.eligible());
  }

  @Test
  public void answersRequireCompleteAndFailureKeepsRetrySnapshot() {
    Fixture f = new Fixture();
    f.c.finish();
    f.now += ProgressService.WAIT_MILLIS;
    f.c.start();
    String id = f.repo.p.active.id;
    f.c.choose("q0", "b");
    f.c.submit();
    assertTrue(f.repo.p.history.isEmpty());
    f.c.choose("q1", "b");
    f.c.choose("q2", "b");
    f.c.submit();
    assertEquals(IntroQuizController.Stage.RESULT, f.c.snapshot().stage);
    assertFalse(f.c.snapshot().result.passed);
    assertNotEquals(id, f.repo.p.active.id);
    f.c.submit();
    assertEquals(1, f.repo.p.history.size());
    f.c.retry();
    assertEquals(IntroQuizController.Stage.QUIZ, f.c.snapshot().stage);
  }

  @Test
  public void pauseStopsSpeechWithoutCompleting() {
    Fixture f = new Fixture();
    f.c.pause();
    assertTrue(f.stops > 0);
    assertNull(f.repo.p.introDoneAt);
  }
}
