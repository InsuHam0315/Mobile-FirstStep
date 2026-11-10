package com.example.firststep.game.domain.discovery;

public final class DiscoveryResult {
  public enum Method {
    SHAKE,
    LIGHT,
    LONG_PRESS
  }

  public enum Source {
    REAL_SENSOR,
    SIMULATED,
    LONG_PRESS
  }

  public final long sessionId;
  public final boolean arrivalSimulated;
  public final String targetId;
  public final Method method;
  public final Source source;
  public final boolean cameraFallback, headingFallback;

  public DiscoveryResult(
      long sessionId,
      String targetId,
      Method method,
      Source source,
      boolean cameraFallback,
      boolean headingFallback) {
    this(sessionId, targetId, method, source, cameraFallback, headingFallback, true);
  }

  public DiscoveryResult(
      long sessionId,
      String targetId,
      Method method,
      Source source,
      boolean cameraFallback,
      boolean headingFallback,
      boolean arrivalSimulated) {
    this.sessionId = sessionId;
    this.targetId = targetId;
    this.method = method;
    this.source = source;
    this.cameraFallback = cameraFallback;
    this.headingFallback = headingFallback;
    this.arrivalSimulated = arrivalSimulated;
  }
}
