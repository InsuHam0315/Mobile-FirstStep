package com.example.firststep.game.presentation.home.account;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.Milestone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Presentation projection containing no identity, answer data or device evidence. */
public final class AccountHomeState {
  public enum Status { LOADING, UNAVAILABLE, NO_ACCOUNT, UNSTARTED, WAITING, READY, COMPLETE, FAILED }
  public enum Server { NONE, INTRODUCTION, COMPLETE }

  public final Status status;
  public final Server server;
  public final long remainingMillis;
  public final List<String> favorites;
  public final boolean favoritesAvailable;

  AccountHomeState(Status status, Server server, long remainingMillis,
      List<String> favorites, boolean favoritesAvailable) {
    this.status = status;
    this.server = server;
    this.remainingMillis = remainingMillis;
    this.favorites = Collections.unmodifiableList(new ArrayList<>(favorites));
    this.favoritesAvailable = favoritesAvailable;
  }

  static AccountHomeState project(Progress local, Milestone remote, long now,
      List<String> favorites, boolean favoritesAvailable) {
    if (local.mode != Progress.Mode.NORMAL || local.instantDemo) {
      throw new IllegalArgumentException("Invalid account progress mode");
    }
    Server server = remote == null ? Server.NONE
        : remote.completion == null ? Server.INTRODUCTION : Server.COMPLETE;
    Status status;
    long remaining = 0;
    if (local.stamp) status = Status.COMPLETE;
    else if (local.introDoneAt == null) status = Status.UNSTARTED;
    else if (local.active != null) status = Status.READY;
    else if (local.introDoneAt < 0 || local.introDoneAt > Long.MAX_VALUE - ProgressService.WAIT_MILLIS) {
      status = Status.FAILED;
    } else {
      long due = local.introDoneAt + ProgressService.WAIT_MILLIS;
      if (now >= due) status = Status.READY;
      else {
        status = Status.WAITING;
        // A clock before the epoch cannot overflow the displayed remaining time.
        remaining = due - Math.max(0L, now);
      }
    }
    return new AccountHomeState(status, server, remaining, favorites, favoritesAvailable);
  }

  @Override public boolean equals(Object value) {
    if (!(value instanceof AccountHomeState)) return false;
    AccountHomeState other = (AccountHomeState) value;
    return status == other.status && server == other.server && remainingMillis == other.remainingMillis
        && favoritesAvailable == other.favoritesAvailable && favorites.equals(other.favorites);
  }

  @Override public int hashCode() {
    return Objects.hash(status, server, remainingMillis, favorites, favoritesAvailable);
  }
}
