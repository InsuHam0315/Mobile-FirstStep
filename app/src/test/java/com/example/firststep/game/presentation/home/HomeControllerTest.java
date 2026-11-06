package com.example.firststep.game.presentation.home;

import static org.junit.Assert.*;

import com.example.firststep.facility.*;
import com.example.firststep.game.domain.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;

public class HomeControllerTest {
  static Facility item(String id, String name, String category) {
    return new Facility(
        id,
        name,
        category,
        "summary",
        Arrays.asList("study"),
        Arrays.asList(name),
        "https://kunsan.ac.kr",
        "source",
        "2026-01-01",
        "unknown",
        "unknown");
  }

  static class Port implements HomeController.Port {
    long token;
    String q, c;
    boolean only;
    Progress.Mode mode;
    String id;
    boolean desired;
    Consumer<Boolean> completion;

    public void load(long t, Progress.Mode m, String q, String c, boolean f) {
      token = t;
      mode = m;
      this.q = q;
      this.c = c;
      only = f;
    }

    public void setFavorite(String id, boolean d, Consumer<Boolean> cb) {
      this.id = id;
      desired = d;
      completion = cb;
    }
  }

  static HomeController.Data data(
      List<Facility> fs, Set<String> fav, String q, String cat, boolean only, Progress.Mode mode) {
    return new HomeController.Data(
        fs,
        FacilitySearch.filter(fs, q, cat, only, fav),
        fav,
        Progress.empty(mode, "digital_information_software"),
        false,
        false,
        0);
  }

  @Test
  public void searchRestoreAndDefensiveSnapshot() {
    Port p = new Port();
    HomeController c = new HomeController(p, s -> {});
    c.restore(HomeState.Screen.SEARCH, Progress.Mode.DEMO, "library", "학습", true, "library");
    c.resume();
    assertEquals(Progress.Mode.DEMO, p.mode);
    assertEquals("library", p.q);
    assertTrue(p.only);
    List<Facility> fs =
        new ArrayList<>(
            Arrays.asList(item("library", "library", "학습"), item("career", "career", "생활")));
    Set<String> favorites = new HashSet<>(Arrays.asList("library", "unknown"));
    HomeController.Data d = data(fs, favorites, p.q, p.c, p.only, p.mode);
    fs.clear();
    favorites.clear();
    c.loaded(p.token, d);
    assertEquals(2, c.snapshot().facilities.size());
    assertEquals(1, c.snapshot().results.size());
    assertTrue(c.snapshot().favoriteIds.contains("unknown"));
    try {
      c.snapshot().favoriteIds.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void staleCallbacksModePauseAndLocation() {
    Port p = new Port();
    HomeController c = new HomeController(p, s -> {});
    c.resume();
    long old = p.token;
    c.filters("x", "", false);
    long query = p.token;
    c.mode(Progress.Mode.DEMO);
    long mode = p.token;
    List<Facility> fs = Arrays.asList(item("library", "library", "학습"));
    c.loaded(old, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    c.loaded(query, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    assertNull(c.snapshot().progress);
    c.loaded(mode, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.DEMO));
    assertEquals(Progress.Mode.DEMO, c.snapshot().progress.mode);
    c.location("fix");
    c.pause();
    c.location("late fix");
    c.select("library");
    assertEquals(HomeState.Screen.HOME, c.snapshot().screen);
    c.resume();
    assertEquals("", c.snapshot().locationMessage);
  }

  @Test
  public void explicitFavoritesFailureRetryAndUnknown() {
    Port p = new Port();
    HomeController c = new HomeController(p, s -> {});
    c.resume();
    List<Facility> fs = Arrays.asList(item("library", "library", "학습"));
    c.loaded(
        p.token,
        data(fs, new HashSet<>(Arrays.asList("unknown")), "", "", false, Progress.Mode.NORMAL));
    c.favorite("missing", true);
    assertNull(p.completion);
    assertNotNull(c.snapshot().error);
    c.refresh();
    c.loaded(
        p.token,
        data(fs, new HashSet<>(Arrays.asList("unknown")), "", "", false, Progress.Mode.NORMAL));
    c.favorite("library", true);
    assertTrue(p.desired);
    p.completion.accept(false);
    assertNotNull(c.snapshot().error);
    c.favorite("library", true);
    assertTrue(p.desired);
    p.completion.accept(true);
    c.loaded(
        p.token,
        data(
            fs,
            new HashSet<>(Arrays.asList("unknown", "library")),
            "",
            "",
            false,
            Progress.Mode.NORMAL));
    assertTrue(c.snapshot().favoriteIds.contains("unknown"));
    assertTrue(c.snapshot().favoriteIds.contains("library"));
    c.favorite("library", true);
    assertTrue(p.desired);
    p.completion.accept(true);
    c.loaded(
        p.token,
        data(
            fs,
            new HashSet<>(Arrays.asList("unknown", "library")),
            "",
            "",
            false,
            Progress.Mode.NORMAL));
    assertTrue(c.snapshot().favoriteIds.contains("library"));
  }

  @Test
  public void loadErrorCanRecover() {
    Port p = new Port();
    HomeController c = new HomeController(p, s -> {});
    c.resume();
    c.failed(p.token);
    assertNotNull(c.snapshot().error);
    c.refresh();
    c.loaded(
        p.token,
        data(Collections.emptyList(), Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    assertNull(c.snapshot().error);
  }

  @Test
  public void countdownSaturatesExtremeClockValues() {
    Progress p =
        new Progress(
            Progress.Mode.NORMAL,
            "target",
            Long.MAX_VALUE - 1,
            false,
            false,
            null,
            Collections.emptyMap());
    assertTrue(HomeController.remainingSeconds(p, Long.MIN_VALUE) > 0);
    assertEquals(
        0, HomeController.remainingSeconds(Progress.empty(Progress.Mode.NORMAL, "target"), 0));
  }

  @Test
  public void actualProgressPolicyFlowsIntoHomeEndingSnapshot() {
    List<GameContent.Building> buildings = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      List<GameContent.Question> qs = new ArrayList<>();
      for (int j = 1; j <= 3; j++)
        qs.add(
            new GameContent.Question(
                "q" + j,
                "synthetic",
                "a",
                Arrays.asList(new GameContent.Option("a", "A"), new GameContent.Option("b", "B"))));
      buildings.add(new GameContent.Building("b" + i, "synthetic", qs));
    }
    class Store implements ProgressRepository {
      Map<String, Progress> rows = new HashMap<>();

      public Progress read(Progress.Mode m, String id) {
        return rows.getOrDefault(m + id, Progress.empty(m, id));
      }

      public List<Progress> readAll(Progress.Mode m) {
        List<Progress> out = new ArrayList<>();
        for (Progress p : rows.values()) if (p.mode == m) out.add(p);
        return out;
      }

      public Progress atomicUpdate(Progress.Mode m, String id, Transition t) {
        Progress p = t.apply(read(m, id));
        rows.put(m + id, p);
        return p;
      }
    }
    Store store = new Store();
    int[] serial = {0};
    ProgressService svc =
        new ProgressService(
            new GameContent("v1", buildings),
            store,
            () -> 0,
            new ProgressService.AttemptFactory() {
              public String nextId() {
                return "attempt" + (++serial[0]);
              }

              public void shuffle(List<?> xs) {}
            });
    Port port = new Port();
    HomeController controller = new HomeController(port, state -> {});
    controller.resume();
    for (int i = 1; i <= 5; i++) {
      String id = "b" + i;
      svc.intro(Progress.Mode.DEMO, id, true);
      Progress p = svc.start(Progress.Mode.DEMO, id);
      Map<String, String> answers = new HashMap<>();
      for (GameContent.Question q : p.active.questions) answers.put(q.id, "a");
      svc.submit(Progress.Mode.DEMO, id, p.active.id, p.active.contentVersion, answers);
      controller.loaded(
          port.token,
          new HomeController.Data(
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptySet(),
              svc.read(Progress.Mode.DEMO, "b1"),
              true,
              svc.endingAvailable(Progress.Mode.DEMO),
              0));
      assertEquals(i == 5, controller.snapshot().endingAvailable);
    }
    controller.mode(Progress.Mode.NORMAL);
    controller.loaded(
        port.token,
        new HomeController.Data(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            svc.read(Progress.Mode.NORMAL, "b1"),
            false,
            svc.endingAvailable(Progress.Mode.NORMAL),
            0));
    assertFalse(controller.snapshot().endingAvailable);
    assertNull(controller.snapshot().progress.introDoneAt);
  }

  @Test
  public void failedFavoriteSurvivesFilterAndReadInEitherCompletionOrder() {
    for (boolean failureFirst : new boolean[] {true, false}) {
      Port p = new Port();
      HomeController c = new HomeController(p, state -> {});
      List<Facility> fs = Arrays.asList(item("library", "library", "학습"));
      c.resume();
      c.loaded(p.token, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
      c.favorite("library", true);
      Consumer<Boolean> write = p.completion;
      c.filters("library", "", false);
      long read = p.token;
      if (failureFirst) write.accept(false);
      c.loaded(read, data(fs, Collections.emptySet(), "library", "", false, Progress.Mode.NORMAL));
      if (!failureFirst) write.accept(false);
      assertEquals("favorite", c.snapshot().error);
      c.retry();
      assertEquals("library", p.id);
      assertTrue(p.desired);
      p.completion.accept(true);
      c.loaded(
          p.token,
          data(
              fs,
              new HashSet<>(Arrays.asList("library", "unknown")),
              "library",
              "",
              false,
              Progress.Mode.NORMAL));
      assertNull(c.snapshot().error);
      assertTrue(c.snapshot().favoriteIds.contains("unknown"));
    }
  }

  @Test
  public void favoriteFailureWhilePausedAppearsOnResumeAndRetries() {
    Port p = new Port();
    HomeController c = new HomeController(p, state -> {});
    List<Facility> fs = Arrays.asList(item("library", "library", "학습"));
    c.resume();
    c.loaded(p.token, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    c.favorite("library", true);
    Consumer<Boolean> completion = p.completion;
    c.pause();
    completion.accept(false);
    c.resume();
    c.loaded(p.token, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    assertEquals("favorite", c.snapshot().error);
    c.retry();
    assertTrue(p.desired);
    p.completion.accept(true);
    c.loaded(
        p.token, data(fs, Collections.singleton("library"), "", "", false, Progress.Mode.NORMAL));
    assertNull(c.snapshot().error);
  }

  @Test
  public void recreatedControllerAdoptsFailureAndRetriesDesiredValue() {
    Port port = new Port();
    HomeController c = new HomeController(port, state -> {});
    c.resume();
    List<Facility> fs = Arrays.asList(item("library", "library", "학습"));
    c.loaded(
        port.token,
        new HomeController.Data(
            fs,
            fs,
            Collections.singleton("unknown"),
            Progress.empty(Progress.Mode.NORMAL, "digital_information_software"),
            false,
            false,
            0,
            Collections.singletonMap("library", true)));
    assertEquals("favorite", c.snapshot().error);
    c.retry();
    assertEquals("library", port.id);
    assertTrue(port.desired);
    port.completion.accept(true);
    c.loaded(
        port.token,
        new HomeController.Data(
            fs,
            fs,
            new HashSet<>(Arrays.asList("library", "unknown")),
            Progress.empty(Progress.Mode.NORMAL, "digital_information_software"),
            false,
            false,
            0,
            Collections.emptyMap()));
    assertNull(c.snapshot().error);
    assertTrue(c.snapshot().favoriteIds.contains("unknown"));
  }

  @Test
  public void independentFavoriteFailuresAreNotLostToOtherSuccess() {
    Port port = new Port();
    HomeController c = new HomeController(port, state -> {});
    c.resume();
    List<Facility> fs = Arrays.asList(item("a", "a", "학습"), item("b", "b", "생활"));
    c.loaded(port.token, data(fs, Collections.emptySet(), "", "", false, Progress.Mode.NORMAL));
    c.favorite("a", true);
    Consumer<Boolean> a = port.completion;
    c.favorite("b", true);
    Consumer<Boolean> b = port.completion;
    a.accept(false);
    b.accept(true);
    c.loaded(port.token, data(fs, Collections.singleton("b"), "", "", false, Progress.Mode.NORMAL));
    assertEquals("favorite", c.snapshot().error);
    c.retry();
    assertEquals("a", port.id);
    assertTrue(port.desired);
  }

  @Test
  public void authoritativeSuccessElsewhereClearsOldFailureAndPendingRetryIsNotReplayed() {
    Port p = new Port();
    HomeController c = new HomeController(p, state -> {});
    c.resume();
    List<Facility> fs = Collections.singletonList(item("library", "library", "학습"));
    c.loaded(
        p.token,
        new HomeController.Data(
            fs,
            fs,
            Collections.emptySet(),
            Progress.empty(Progress.Mode.NORMAL, "digital_information_software"),
            false,
            false,
            0,
            Collections.singletonMap("library", true)));
    c.retry();
    Consumer<Boolean> pending = p.completion;
    c.retry();
    assertSame(pending, p.completion);
    pending.accept(false);
    c.refresh();
    c.loaded(
        p.token,
        new HomeController.Data(
            fs,
            fs,
            Collections.singleton("library"),
            Progress.empty(Progress.Mode.NORMAL, "digital_information_software"),
            false,
            false,
            0,
            Collections.emptyMap()));
    assertNull(c.snapshot().error);
  }
}
