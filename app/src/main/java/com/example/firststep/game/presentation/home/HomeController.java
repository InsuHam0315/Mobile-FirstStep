package com.example.firststep.game.presentation.home;

import com.example.firststep.facility.Facility;
import com.example.firststep.game.domain.Progress;
import java.util.*;

/** Main-thread state machine; asynchronous results carry a monotonically increasing generation. */
public final class HomeController {
  public interface Port {
    void load(
        long generation, Progress.Mode mode, String query, String category, boolean favoritesOnly);

    void setFavorite(String id, boolean desired, java.util.function.Consumer<Boolean> complete);
  }

  public interface Observer {
    void render(HomeState state);
  }

  public static final class Data {
    public final List<Facility> facilities, results;
    public final Set<String> favoriteIds;
    public final Progress progress;
    public final Map<String, Boolean> failedFavorites;
    public final boolean eligible, endingAvailable;
    public final long nowMillis;

    public Data(
        List<Facility> facilities,
        List<Facility> results,
        Set<String> favoriteIds,
        Progress progress,
        boolean eligible,
        boolean endingAvailable,
        long nowMillis) {
      this(facilities, results, favoriteIds, progress, eligible, endingAvailable, nowMillis, null);
    }

    public Data(
        List<Facility> facilities,
        List<Facility> results,
        Set<String> favoriteIds,
        Progress progress,
        boolean eligible,
        boolean endingAvailable,
        long nowMillis,
        Map<String, Boolean> failedFavorites) {
      this.failedFavorites =
          failedFavorites == null
              ? null
              : Collections.unmodifiableMap(new LinkedHashMap<>(failedFavorites));
      this.facilities = Collections.unmodifiableList(new ArrayList<>(facilities));
      this.results = Collections.unmodifiableList(new ArrayList<>(results));
      this.favoriteIds = Collections.unmodifiableSet(new HashSet<>(favoriteIds));
      this.progress = progress;
      this.eligible = eligible;
      this.endingAvailable = endingAvailable;
      this.nowMillis = nowMillis;
    }
  }

  private final Port port;
  private final Observer observer;
  private HomeState.Screen screen = HomeState.Screen.HOME;
  private Progress.Mode mode = Progress.Mode.NORMAL;
  private String query = "",
      category = "",
      selectedId = null,
      error = null,
      location = "",
      tile = "";
  private boolean favoritesOnly, loading, eligible, ending, active;
  private long generation, now, writeGeneration;
  private final Map<String, Long> writeTokens = new HashMap<>();
  private final Map<String, Boolean> failedFavorites = new LinkedHashMap<>();
  private List<Facility> facilities = Collections.emptyList(), results = Collections.emptyList();
  private Set<String> favorites = Collections.emptySet();
  private Progress progress;

  public HomeController(Port port, Observer observer) {
    this.port = port;
    this.observer = observer;
  }

  public HomeState snapshot() {
    return new HomeState(
        screen,
        mode,
        query,
        category,
        selectedId,
        !failedFavorites.isEmpty() ? "favorite" : error,
        location,
        tile,
        favoritesOnly,
        loading,
        eligible,
        ending,
        results,
        facilities,
        favorites,
        progress,
        now,
        remainingSeconds(progress, now));
  }

  public static long remainingSeconds(Progress progress, long now) {
    if (progress == null || progress.introDoneAt == null || progress.instantDemo) return 0;
    java.math.BigInteger remaining =
        java.math.BigInteger.valueOf(progress.introDoneAt)
            .add(
                java.math.BigInteger.valueOf(
                    com.example.firststep.game.domain.ProgressService.WAIT_MILLIS))
            .subtract(java.math.BigInteger.valueOf(now));
    if (remaining.signum() <= 0) return 0;
    java.math.BigInteger seconds =
        remaining.add(java.math.BigInteger.valueOf(999)).divide(java.math.BigInteger.valueOf(1000));
    return seconds.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
  }

  private void emit() {
    observer.render(snapshot());
  }

  public void restore(
      HomeState.Screen s, Progress.Mode m, String q, String c, boolean f, String id) {
    screen = s;
    mode = m;
    query = q;
    category = c;
    favoritesOnly = f;
    selectedId = id;
  }

  public void resume() {
    active = true;
    location = "";
    refresh();
  }

  public void pause() {
    active = false;
    generation++;
  }

  public void refresh() {
    if (!active) return;
    long token = ++generation;
    loading = true;
    error = null;
    emit();
    port.load(token, mode, query, category, favoritesOnly);
  }

  public void loaded(long token, Data d) {
    if (!active || token != generation) return;
    if (d.failedFavorites != null) {
      Map<String, Boolean> pendingFailures = new LinkedHashMap<>();
      for (String id : writeTokens.keySet())
        if (failedFavorites.containsKey(id)) pendingFailures.put(id, failedFavorites.get(id));
      failedFavorites.clear();
      failedFavorites.putAll(d.failedFavorites);
      failedFavorites.putAll(pendingFailures);
    }
    facilities = d.facilities;
    results = d.results;
    favorites = d.favoriteIds;
    progress = d.progress;
    eligible = d.eligible;
    ending = d.endingAvailable;
    now = d.nowMillis;
    loading = false;
    error = null;
    emit();
  }

  public void failed(long token) {
    if (!active || token != generation) return;
    loading = false;
    error = "load";
    emit();
  }

  public void navigate(HomeState.Screen value) {
    if (!active) return;
    screen = value;
    emit();
  }

  public void select(String id) {
    if (!active) return;
    if (!known(id)) {
      error = "selection";
      emit();
      return;
    }
    selectedId = id;
    screen = HomeState.Screen.DETAIL;
    emit();
  }

  private boolean known(String id) {
    for (Facility f : facilities) if (f.id.equals(id)) return true;
    return false;
  }

  public void filters(String q, String c, boolean f) {
    if (!active) return;
    query = q;
    category = c;
    favoritesOnly = f;
    refresh();
  }

  public void mode(Progress.Mode value) {
    if (!active || mode == value) return;
    mode = value;
    progress = null;
    eligible = false;
    ending = false;
    refresh();
  }

  public void favorite(String id, boolean desired) {
    if (!active) return;
    if (!known(id)) {
      error = "selection";
      emit();
      return;
    }
    generation++; // A read admitted before this write must not overwrite it.
    long writeToken = ++writeGeneration;
    writeTokens.put(id, writeToken);
    port.setFavorite(
        id,
        desired,
        ok -> {
          if (!Objects.equals(writeTokens.get(id), writeToken)) return;
          writeTokens.remove(id);
          if (ok) {
            failedFavorites.remove(id);
            if (active) refresh();
          } else {
            failedFavorites.put(id, desired);
            loading = false;
            if (active) emit();
          }
        });
  }

  /** Retry the failed desired-state write, or reload after a read failure. */
  public void retry() {
    if (!active) return;
    if (!failedFavorites.isEmpty()) {
      for (Map.Entry<String, Boolean> failure : failedFavorites.entrySet()) {
        if (!writeTokens.containsKey(failure.getKey())) {
          favorite(failure.getKey(), failure.getValue());
          return;
        }
      }
    } else refresh();
  }

  public void location(String message) {
    if (!active) return;
    location = message;
    emit();
  }

  public void tile(String message) {
    if (!active) return;
    tile = message;
    emit();
  }

  public void tick(long time, boolean isEligible) {
    if (!active) return;
    now = time;
    eligible = isEligible;
    emit();
  }
}
