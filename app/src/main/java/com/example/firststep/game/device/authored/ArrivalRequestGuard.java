package com.example.firststep.game.device.authored;

/** Invalidates callbacks from stopped, retried or superseded foreground requests. */
final class ArrivalRequestGuard {
  private long generation;
  private boolean active;

  synchronized long start() {
    active = true;
    return ++generation;
  }

  synchronized void stop() {
    active = false;
    generation++;
  }

  synchronized boolean current(long token) {
    return active && token == generation;
  }
}
