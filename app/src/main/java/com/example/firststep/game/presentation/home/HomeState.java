package com.example.firststep.game.presentation.home;

import com.example.firststep.facility.Facility;
import com.example.firststep.game.domain.Progress;
import java.util.*;

/** Immutable presentation snapshot. */
public final class HomeState {
  public enum Screen {
    HOME,
    SEARCH,
    DETAIL,
    MAP,
    PROFILE,
    ENDING
  }

  public final Screen screen;
  public final Progress.Mode mode;
  public final String query, category, selectedId, error, locationMessage, tileMessage;
  public final boolean favoritesOnly, loading, eligible, endingAvailable;
  public final List<Facility> results, facilities;
  public final Set<String> favoriteIds;
  public final Progress progress;
  public final long nowMillis, remainingSeconds;

  HomeState(
      Screen screen,
      Progress.Mode mode,
      String query,
      String category,
      String selectedId,
      String error,
      String locationMessage,
      String tileMessage,
      boolean favoritesOnly,
      boolean loading,
      boolean eligible,
      boolean endingAvailable,
      List<Facility> results,
      List<Facility> facilities,
      Set<String> favoriteIds,
      Progress progress,
      long nowMillis,
      long remainingSeconds) {
    this.screen = screen;
    this.mode = mode;
    this.query = query;
    this.category = category;
    this.selectedId = selectedId;
    this.error = error;
    this.locationMessage = locationMessage;
    this.tileMessage = tileMessage;
    this.favoritesOnly = favoritesOnly;
    this.loading = loading;
    this.eligible = eligible;
    this.endingAvailable = endingAvailable;
    this.results = Collections.unmodifiableList(new ArrayList<>(results));
    this.facilities = Collections.unmodifiableList(new ArrayList<>(facilities));
    this.favoriteIds = Collections.unmodifiableSet(new HashSet<>(favoriteIds));
    this.progress = progress;
    this.nowMillis = nowMillis;
    this.remainingSeconds = remainingSeconds;
  }
}
