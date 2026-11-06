package com.example.firststep.game.presentation.discovery;

import com.example.firststep.game.domain.discovery.DiscoveryResult;

public final class DiscoveryState {
  public enum CameraStatus {
    PERMISSION_REQUIRED,
    DENIED,
    UNAVAILABLE,
    ERROR,
    STARTING,
    READY,
    FALLBACK
  }

  public enum HeadingStatus {
    WAITING,
    UNAVAILABLE,
    UNRELIABLE,
    STALE,
    MISALIGNED,
    ALIGNED,
    FALLBACK
  }

  public final long sessionId;
  public final boolean active,
      simulated,
      arrived,
      cameraFallback,
      headingFallback,
      completed,
      lightAvailable,
      motionAvailable;
  public final CameraStatus camera;
  public final HeadingStatus heading;
  public final DiscoveryResult.Method method;
  public final DiscoveryResult result;
  public final int shakeCount;
  public final String lightStatus;

  DiscoveryState(
      long sessionId,
      boolean active,
      boolean simulated,
      boolean arrived,
      boolean cameraFallback,
      boolean headingFallback,
      CameraStatus camera,
      HeadingStatus heading,
      DiscoveryResult.Method method,
      DiscoveryResult result,
      int shakeCount,
      String lightStatus,
      boolean lightAvailable,
      boolean motionAvailable) {
    this.sessionId = sessionId;
    this.active = active;
    this.simulated = simulated;
    this.arrived = arrived;
    this.cameraFallback = cameraFallback;
    this.headingFallback = headingFallback;
    this.camera = camera;
    this.heading = heading;
    this.method = method;
    this.result = result;
    this.completed = result != null;
    this.shakeCount = shakeCount;
    this.lightStatus = lightStatus;
    this.lightAvailable = lightAvailable;
    this.motionAvailable = motionAvailable;
  }
}
