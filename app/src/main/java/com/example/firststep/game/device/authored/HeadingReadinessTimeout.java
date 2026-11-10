package com.example.firststep.game.device.authored;

/** One-shot first reliable heading deadline measured using elapsed realtime. */
final class HeadingReadinessTimeout {
  static final long FIRST_FIX_TIMEOUT_MS = 3000L;

  enum Outcome { PENDING, UNAVAILABLE, UNRELIABLE }

  private final long startedAt;
  private boolean valid, invalid, closed, reported;

  HeadingReadinessTimeout(long startedAt) {
    if (startedAt < 0) throw new IllegalArgumentException("Invalid start time");
    this.startedAt = startedAt;
  }

  void validHeading() {
    if (!closed) valid = true;
  }

  void invalidHeading() {
    if (!closed && !valid) invalid = true;
  }

  Outcome poll(long now) {
    if (closed || valid || reported || now < startedAt
        || now - startedAt < FIRST_FIX_TIMEOUT_MS) return Outcome.PENDING;
    reported = true;
    return invalid ? Outcome.UNRELIABLE : Outcome.UNAVAILABLE;
  }

  void close() {
    closed = true;
  }
}
