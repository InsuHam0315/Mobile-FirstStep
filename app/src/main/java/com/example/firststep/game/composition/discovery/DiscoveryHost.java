package com.example.firststep.game.composition.discovery;

import android.content.Context;
import android.os.SystemClock;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;
import com.example.firststep.game.device.DiscoveryCamera;
import com.example.firststep.game.device.DiscoverySensors;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;

/** Owns the adapter graph for one Activity; all calls and callbacks are on the main thread. */
public final class DiscoveryHost {
  private final Context context;
  private final LifecycleOwner owner;
  private final PreviewView preview;
  private final boolean simulated;
  private final DiscoveryController controller;
  private DiscoveryTestDriver driver;
  private DiscoveryCamera camera;
  private DiscoverySensors sensors;
  private long generation;
  private boolean active;

  public DiscoveryHost(
      Context context,
      LifecycleOwner owner,
      PreviewView preview,
      boolean simulated,
      DiscoveryController.Listener listener) {
    this.context = context;
    this.owner = owner;
    this.preview = preview;
    this.simulated = simulated;
    controller =
        new DiscoveryController(
            () -> simulated ? (driver == null ? 0 : driver.now()) : SystemClock.elapsedRealtime(),
            listener,
            simulated);
  }

  public DiscoveryController controller() {
    return controller;
  }

  public long resume() {
    active = true;
    generation = controller.resume();
    if (simulated) driver = new DiscoveryTestDriver(controller, generation);
    else {
      sensors = new DiscoverySensors(context, controller, generation);
      sensors.start();
    }
    return generation;
  }

  public void startCamera() {
    closeCamera();
    if (!active) return;
    if (simulated) {
      controller.setCamera(generation, DiscoveryState.CameraStatus.STARTING);
      return;
    }
    long g = generation;
    camera = new DiscoveryCamera(context, owner, preview, state -> controller.setCamera(g, state));
    camera.start();
  }

  public void closeCamera() {
    if (camera != null) {
      camera.close();
      camera = null;
    }
  }

  public void pause() {
    active = false;
    controller.pause();
    closeCamera();
    if (sensors != null) {
      sensors.close();
      sensors = null;
    }
    if (driver != null) {
      driver.close();
      driver = null;
    }
  }
}
