package com.example.firststep.game.composition.discovery;

import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;

/** Debug-mode instrumentation seam. Invoke on the main thread. */
public final class DiscoveryTestDriver implements DiscoveryController.Clock {
  private static DiscoveryTestDriver current;
  private final DiscoveryController controller;
  private final long generation;
  private long time;

  public DiscoveryTestDriver(DiscoveryController controller, long generation) {
    this.controller = controller;
    this.generation = generation;
    current = this;
  }

  public static DiscoveryTestDriver current() {
    return current;
  }

  public void close() {
    if (current == this) current = null;
  }

  public DiscoveryState snapshot() {
    return controller.snapshot();
  }

  public void advanceTime(long ms) {
    if (ms < 0) throw new IllegalArgumentException();
    time += ms;
    if (controller.generation() == generation) controller.tick();
  }

  public long now() {
    return time;
  }

  public void heading(float degrees, boolean reliable) {
    controller.heading(generation, degrees, reliable);
  }

  public void acceleration(float magnitude) {
    controller.acceleration(generation, magnitude);
  }

  public void light(float lux) {
    controller.light(generation, lux);
  }

  public void cameraState(DiscoveryState.CameraStatus state) {
    controller.setCamera(generation, state);
  }

  public void tick() {
    if (controller.generation() == generation) controller.tick();
  }
}
