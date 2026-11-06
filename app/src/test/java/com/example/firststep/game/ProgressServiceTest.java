package com.example.firststep.game;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.*;
import java.util.*;
import org.junit.Test;

public class ProgressServiceTest {
  static GameContent catalog() {
    List<GameContent.Building> bs = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      List<GameContent.Question> qs = new ArrayList<>();
      for (int j = 1; j <= 3; j++)
        qs.add(
            new GameContent.Question(
                "q" + j,
                "synthetic",
                "a",
                Arrays.asList(new GameContent.Option("a", "A"), new GameContent.Option("b", "B"))));
      bs.add(new GameContent.Building("b" + i, "synthetic", qs));
    }
    return new GameContent("v1", bs);
  }

  static class Store implements ProgressRepository {
    Map<String, Progress> rows = new HashMap<>();

    String key(Progress.Mode m, String id) {
      return m + id;
    }

    public synchronized Progress read(Progress.Mode m, String id) {
      return rows.getOrDefault(key(m, id), Progress.empty(m, id));
    }

    public synchronized List<Progress> readAll(Progress.Mode m) {
      List<Progress> result = new ArrayList<>();
      for (Progress p : rows.values()) if (p.mode == m) result.add(p);
      return result;
    }

    public synchronized Progress atomicUpdate(Progress.Mode m, String id, Transition t) {
      Progress p = t.apply(read(m, id));
      rows.put(key(m, id), p);
      return p;
    }
  }

  static class Sources implements ProgressService.Clock, ProgressService.AttemptFactory {
    long now;
    int ids, shuffles;

    public long nowMillis() {
      return now;
    }

    public String nextId() {
      return "attempt" + (++ids);
    }

    public void shuffle(List<?> l) {
      shuffles++;
      Collections.reverse(l);
    }
  }

  static Map<String, String> answers(Progress.Attempt a, String option) {
    Map<String, String> m = new HashMap<>();
    for (GameContent.Question q : a.questions) m.put(q.id, option);
    return m;
  }

  @Test
  public void independentTimingAndReplay() {
    Store store = new Store();
    Sources s = new Sources();
    ProgressService svc = new ProgressService(catalog(), store, s, s);
    svc.intro(Progress.Mode.NORMAL, "b1", false);
    s.now = 100;
    svc.intro(Progress.Mode.NORMAL, "b2", false);
    s.now = ProgressService.WAIT_MILLIS - 1;
    assertFalse(svc.eligible(svc.read(Progress.Mode.NORMAL, "b1")));
    s.now++;
    assertTrue(svc.eligible(svc.read(Progress.Mode.NORMAL, "b1")));
    assertFalse(svc.eligible(svc.read(Progress.Mode.NORMAL, "b2")));
    assertEquals(Long.valueOf(0), svc.intro(Progress.Mode.NORMAL, "b1", false).introDoneAt);
    svc.intro(Progress.Mode.DEMO, "b1", false);
    assertFalse(svc.intro(Progress.Mode.DEMO, "b1", true).instantDemo);
    try {
      svc.intro(Progress.Mode.NORMAL, "b1", true);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    Store overflow = new Store();
    s.now = Long.MAX_VALUE - 1;
    ProgressService edge = new ProgressService(catalog(), overflow, s, s);
    edge.intro(Progress.Mode.NORMAL, "b1", false);
    s.now = Long.MAX_VALUE;
    assertFalse(edge.eligible(edge.read(Progress.Mode.NORMAL, "b1")));
  }

  @Test
  public void retrySnapshotAndIdempotence() {
    Store store = new Store();
    Sources s = new Sources();
    ProgressService svc = new ProgressService(catalog(), store, s, s);
    svc.intro(Progress.Mode.DEMO, "b1", true);
    Progress.Attempt first = svc.start(Progress.Mode.DEMO, "b1").active;
    assertEquals("q3", first.questions.get(0).id);
    assertEquals("b", first.questions.get(0).options.get(0).id);
    assertSame(
        first, new ProgressService(catalog(), store, s, s).start(Progress.Mode.DEMO, "b1").active);
    Map<String, String> wrong = answers(first, "b");
    assertFalse(svc.submit(Progress.Mode.DEMO, "b1", first.id, first.contentVersion, wrong).passed);
    Progress.Attempt retry = svc.read(Progress.Mode.DEMO, "b1").active;
    int ids = s.ids, shuffles = s.shuffles;
    assertFalse(svc.submit(Progress.Mode.DEMO, "b1", first.id, first.contentVersion, wrong).passed);
    assertEquals(ids, s.ids);
    assertEquals(shuffles, s.shuffles);
    assertSame(retry, svc.read(Progress.Mode.DEMO, "b1").active);
    Map<String, String> ok = answers(retry, "a");
    ok.put("q1", "b");
    assertTrue(svc.submit(Progress.Mode.DEMO, "b1", retry.id, retry.contentVersion, ok).passed);
    assertTrue(
        svc.submit(
                Progress.Mode.DEMO, "b1", retry.id, retry.contentVersion, new LinkedHashMap<>(ok))
            .passed);
    try {
      svc.submit(Progress.Mode.DEMO, "b1", retry.id, retry.contentVersion, wrong);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      svc.submit(Progress.Mode.NORMAL, "b1", retry.id, retry.contentVersion, ok);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertFalse(svc.endingAvailable(Progress.Mode.DEMO));
  }

  @Test
  public void rejectsMalformedAndStaleRequests() {
    Store store = new Store();
    Sources s = new Sources();
    ProgressService svc = new ProgressService(catalog(), store, s, s);
    svc.intro(Progress.Mode.DEMO, "b1", true);
    Progress.Attempt a = svc.start(Progress.Mode.DEMO, "b1").active;
    for (Map<String, String> bad :
        Arrays.asList(Collections.singletonMap("q1", "a"), answers(a, "unknown"))) {
      try {
        svc.submit(Progress.Mode.DEMO, "b1", a.id, a.contentVersion, bad);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
    try {
      svc.submit(Progress.Mode.DEMO, "b1", a.id, "stale", answers(a, "a"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      svc.start(Progress.Mode.DEMO, "unknown");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertSame(a, svc.read(Progress.Mode.DEMO, "b1").active);
  }

  @Test
  public void endingRequiresFiveKnownStampsInSameMode() {
    Store store = new Store();
    Sources source = new Sources();
    ProgressService svc = new ProgressService(catalog(), store, source, source);
    for (int i = 1; i <= 5; i++) {
      String id = "b" + i;
      svc.intro(Progress.Mode.DEMO, id, true);
      Progress.Attempt a = svc.start(Progress.Mode.DEMO, id).active;
      svc.submit(Progress.Mode.DEMO, id, a.id, a.contentVersion, answers(a, "a"));
      if (i < 5) assertFalse(svc.endingAvailable(Progress.Mode.DEMO));
    }
    assertTrue(svc.endingAvailable(Progress.Mode.DEMO));
    assertFalse(svc.endingAvailable(Progress.Mode.NORMAL));
    store.rows.remove(store.key(Progress.Mode.DEMO, "b5"));
    Progress known = store.read(Progress.Mode.DEMO, "b1");
    store.rows.put(
        store.key(Progress.Mode.DEMO, "unknown"),
        new Progress(
            Progress.Mode.DEMO,
            "unknown",
            known.introDoneAt,
            known.instantDemo,
            true,
            null,
            known.history));
    assertFalse(svc.endingAvailable(Progress.Mode.DEMO));
  }

  @Test
  public void activeAttemptSurvivesBackwardClock() {
    Store store = new Store();
    Sources s = new Sources();
    ProgressService svc = new ProgressService(catalog(), store, s, s);
    svc.intro(Progress.Mode.NORMAL, "b1", false);
    s.now = ProgressService.WAIT_MILLIS;
    Progress.Attempt a = svc.start(Progress.Mode.NORMAL, "b1").active;
    s.now = -1;
    assertSame(a, svc.start(Progress.Mode.NORMAL, "b1").active);
  }

  @Test
  public void changedCatalogDoesNotChangeActiveSnapshotGrading() {
    Store store = new Store();
    Sources source = new Sources();
    GameContent original = catalog();
    ProgressService first = new ProgressService(original, store, source, source);
    first.intro(Progress.Mode.DEMO, "b1", true);
    Progress.Attempt a = first.start(Progress.Mode.DEMO, "b1").active;
    List<GameContent.Building> buildings = new ArrayList<>();
    for (GameContent.Building b : original.buildings.values()) {
      List<GameContent.Question> qs = new ArrayList<>();
      for (GameContent.Question q : b.questions)
        qs.add(new GameContent.Question(q.id, q.text, "b", q.options));
      buildings.add(new GameContent.Building(b.id, b.label, qs));
    }
    ProgressService changed =
        new ProgressService(new GameContent("v2", buildings), store, source, source);
    assertTrue(changed.submit(Progress.Mode.DEMO, "b1", a.id, "v1", answers(a, "a")).passed);
  }

  @Test
  public void singleBuildingCanCompleteButCannotUnlockEnding() {
    GameContent single =
        new GameContent("v1", Collections.singletonList(catalog().buildings.get("b1")));
    Store store = new Store();
    Sources source = new Sources();
    ProgressService svc = new ProgressService(single, store, source, source);
    svc.intro(Progress.Mode.NORMAL, "b1", false);
    source.now = ProgressService.WAIT_MILLIS;
    Progress.Attempt attempt = svc.start(Progress.Mode.NORMAL, "b1").active;
    assertTrue(
        svc.submit(
                Progress.Mode.NORMAL,
                "b1",
                attempt.id,
                attempt.contentVersion,
                answers(attempt, "a"))
            .passed);
    assertTrue(svc.read(Progress.Mode.NORMAL, "b1").stamp);
    assertFalse(svc.endingAvailable(Progress.Mode.NORMAL));
  }

  @Test
  public void catalogRejectsZeroAndMoreThanFiveBuildings() {
    try {
      new GameContent("v1", Collections.emptyList());
      fail();
    } catch (IllegalArgumentException expected) {
    }
    List<GameContent.Building> six = new ArrayList<>(catalog().buildings.values());
    six.add(new GameContent.Building("b6", "synthetic", six.get(0).questions));
    try {
      new GameContent("v1", six);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
