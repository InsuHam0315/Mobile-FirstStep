package com.example.firststep.game.presentation.discovery;

import com.example.firststep.game.domain.discovery.DiscoveryResult;
import java.util.ArrayDeque;

/** Main-thread only. Times are elapsed realtime milliseconds, never wall-clock time. */
public final class DiscoveryController {
  public interface Clock {
    long now();
  }

  public interface Listener {
    void onState(DiscoveryState state);

    void onCompletion(DiscoveryResult result);
  }

  public static final long HEADING_FRESH_MS = 1000,
      LIGHT_FRESH_MS = 2500,
      COVER_MS = 1500,
      HOLD_MS = 1500,
      SHAKE_WINDOW_MS = 3000,
      SHAKE_DEBOUNCE_MS = 150;

  public static final class Config {
    public final long headingFreshMs, lightFreshMs, coverMs, holdMs, shakeWindowMs, debounceMs;
    public final float alignEnter,
        alignExit,
        shakeThreshold,
        shakeRearm,
        minimumBaseline,
        lightFraction;

    public Config() {
      this(1000, 2500, 1500, 1500, 3000, 150, 15, 20, 12, 6, 1, 0.2f);
    }

    public Config(
        long hf,
        long lf,
        long cover,
        long hold,
        long window,
        long debounce,
        float enter,
        float exit,
        float threshold,
        float rearm,
        float baseline,
        float fraction) {
      headingFreshMs = hf;
      lightFreshMs = lf;
      coverMs = cover;
      holdMs = hold;
      shakeWindowMs = window;
      debounceMs = debounce;
      alignEnter = enter;
      alignExit = exit;
      shakeThreshold = threshold;
      shakeRearm = rearm;
      minimumBaseline = baseline;
      lightFraction = fraction;
    }
  }

  private final Config config;
  private final Clock clock;
  private final Listener listener;
  private final boolean simulated;
  private static final java.util.concurrent.atomic.AtomicLong NEXT_SESSION =
      new java.util.concurrent.atomic.AtomicLong();
  private long generation,
      headingAt = -1,
      lightAt = -1,
      darkAt = -1,
      holdAt = -1,
      lastPulse = -10000;
  private boolean active,
      arrived,
      cameraFallback,
      headingFallback,
      aligned,
      rearmed = true,
      lightAvailable = true,
      motionAvailable = true;
  private float baseline = -1, cachedLight = -1;
  private long cachedLightAt = -1;
  private boolean dark, tooDark;
  private DiscoveryState.CameraStatus camera = DiscoveryState.CameraStatus.STARTING;
  private DiscoveryState.HeadingStatus heading = DiscoveryState.HeadingStatus.WAITING;
  private DiscoveryResult.Method method = DiscoveryResult.Method.SHAKE;
  private DiscoveryResult result;
  private final ArrayDeque<Long> pulses = new ArrayDeque<>();

  public DiscoveryController(Clock clock, Listener listener, boolean simulated) {
    this(clock, listener, simulated, new Config());
  }

  public DiscoveryController(Clock clock, Listener listener, boolean simulated, Config config) {
    this.clock = clock;
    this.listener = listener;
    this.simulated = simulated;
    this.config = config;
  }

  public long resume() {
    generation = NEXT_SESSION.incrementAndGet();
    active = true;
    arrived = false;
    cameraFallback = false;
    headingFallback = false;
    aligned = false;
    headingAt = -1;
    lightAt = -1;
    baseline = -1;
    cachedLight = -1;
    cachedLightAt = -1;
    tooDark = false;
    darkAt = -1;
    holdAt = -1;
    result = null;
    pulses.clear();
    rearmed = true;
    lastPulse = -10000;
    camera = DiscoveryState.CameraStatus.STARTING;
    heading = DiscoveryState.HeadingStatus.WAITING;
    lightAvailable = true;
    motionAvailable = true;
    publish();
    return generation;
  }

  public void pause() {
    active = false;
    generation++;
    holdAt = -1;
    darkAt = -1;
    pulses.clear();
    publish();
  }

  public long generation() {
    return generation;
  }

  private boolean valid(long g) {
    return active && g == generation && result == null;
  }

  public DiscoveryState snapshot() {
    return new DiscoveryState(
        generation,
        active,
        simulated,
        arrived,
        cameraFallback,
        headingFallback,
        camera,
        heading,
        method,
        result,
        pulses.size(),
        lightText(),
        lightAvailable,
        motionAvailable);
  }

  private String lightText() {
    if (!lightAvailable) return "missing";
    if (baseline < 0) return tooDark ? "too_dark" : "baseline";
    if (lightAt < 0 || clock.now() - lightAt > config.lightFreshMs) return "stale";
    return dark ? "dark" : "ready";
  }

  private void publish() {
    if (method == DiscoveryResult.Method.LIGHT
        && baseline < 0
        && cachedLightAt >= 0
        && clock.now() - cachedLightAt <= config.lightFreshMs
        && ready()) {
      lightAt = cachedLightAt;
      if (cachedLight > config.minimumBaseline) {
        baseline = cachedLight;
        tooDark = false;
      } else tooDark = true;
    }
    listener.onState(snapshot());
  }

  public void arrival() {
    if (!active || result != null) return;
    arrived = true;
    publish();
  }

  public void setCamera(long g, DiscoveryState.CameraStatus value) {
    if (!valid(g) || cameraFallback) return;
    camera = value;
    if (value != DiscoveryState.CameraStatus.READY) cancelProgress();
    publish();
  }

  public void cameraFallback() {
    if (!active || result != null) return;
    cancelProgress();
    cameraFallback = true;
    camera = DiscoveryState.CameraStatus.FALLBACK;
    publish();
  }

  public void retryCamera() {
    if (!active || result != null) return;
    cancelProgress();
    cameraFallback = false;
    camera = DiscoveryState.CameraStatus.STARTING;
    publish();
  }

  public void sensorAvailability(long g, boolean motion, boolean light) {
    if (!valid(g)) return;
    motionAvailable = motion;
    lightAvailable = light;
    if (!motion || !light) cancelProgress();
    if (!light) {
      baseline = -1;
      lightAt = -1;
      cachedLightAt = -1;
    }
    publish();
  }

  public void headingUnavailable(long g, boolean unreliable) {
    if (!valid(g) || headingFallback) return;
    headingAt = -1;
    aligned = false;
    heading =
        unreliable
            ? DiscoveryState.HeadingStatus.UNRELIABLE
            : DiscoveryState.HeadingStatus.UNAVAILABLE;
    cancelProgress();
    publish();
  }

  public void heading(long g, float degrees, boolean reliable) {
    if (!valid(g) || headingFallback) return;
    if (!reliable || Float.isNaN(degrees) || Float.isInfinite(degrees)) {
      headingUnavailable(g, true);
      return;
    }
    headingAt = clock.now();
    float d = Math.abs(((degrees % 360) + 540) % 360 - 180);
    aligned = d <= (aligned ? config.alignExit : config.alignEnter);
    heading =
        aligned ? DiscoveryState.HeadingStatus.ALIGNED : DiscoveryState.HeadingStatus.MISALIGNED;
    if (!aligned) cancelProgress();
    publish();
  }

  public void headingFallback() {
    if (!active || result != null) return;
    headingFallback = true;
    aligned = true;
    heading = DiscoveryState.HeadingStatus.FALLBACK;
    publish();
  }

  public void select(DiscoveryResult.Method value) {
    if (!active || result != null) return;
    method = value;
    cancelProgress();
    baseline = -1;
    lightAt = -1;
    tooDark = false;
    if (value == DiscoveryResult.Method.LIGHT
        && cachedLightAt >= 0
        && clock.now() - cachedLightAt <= config.lightFreshMs) {
      lightAt = cachedLightAt;
      if (cachedLight > config.minimumBaseline) baseline = cachedLight;
      else tooDark = true;
    }
    publish();
  }

  private boolean ready() {
    if (!headingFallback && headingAt >= 0 && clock.now() - headingAt > config.headingFreshMs) {
      aligned = false;
      heading = DiscoveryState.HeadingStatus.STALE;
      headingAt = -1;
      cancelProgress();
    }
    return active
        && arrived
        && aligned
        && (camera == DiscoveryState.CameraStatus.READY || cameraFallback)
        && result == null;
  }

  private void cancelProgress() {
    pulses.clear();
    darkAt = -1;
    dark = false;
    holdAt = -1;
    rearmed = true;
  }

  public void acceleration(long g, float magnitude) {
    if (!valid(g)
        || !ready()
        || !motionAvailable
        || method != DiscoveryResult.Method.SHAKE
        || !Float.isFinite(magnitude)) return;
    long now = clock.now();
    float excess = magnitude - 9.8f;
    while (!pulses.isEmpty() && now - pulses.peekFirst() > config.shakeWindowMs)
      pulses.removeFirst();
    if (excess < config.shakeRearm) rearmed = true;
    if (excess >= config.shakeThreshold && rearmed) {
      rearmed = false;
      if (now - lastPulse >= config.debounceMs) {
        lastPulse = now;
        pulses.addLast(now);
        if (pulses.size() >= 5) complete(DiscoveryResult.Method.SHAKE);
      }
    }
    publish();
  }

  public void light(long g, float lux) {
    if (!valid(g) || !lightAvailable) return;
    long now = clock.now();
    if (!Float.isFinite(lux) || lux < 0) {
      lightAt = -1;
      cachedLightAt = -1;
      darkAt = -1;
      dark = false;
      publish();
      return;
    }
    cachedLight = lux;
    cachedLightAt = now;
    if (!ready() || method != DiscoveryResult.Method.LIGHT) return;
    if (lightAt >= 0 && now - lightAt > config.lightFreshMs) {
      darkAt = -1;
      dark = false;
    }
    lightAt = now;
    if (baseline < 0) {
      if (lux > config.minimumBaseline) {
        baseline = lux;
        tooDark = false;
      } else tooDark = true;
      dark = false;
      darkAt = -1;
    } else {
      boolean next = lux <= baseline * config.lightFraction;
      if (next && !dark) darkAt = now;
      if (!next) darkAt = -1;
      dark = next;
    }
    tick();
  }

  public void holdStart() {
    if (ready()) {
      holdAt = clock.now();
      publish();
    }
  }

  public void holdCancel() {
    holdAt = -1;
    publish();
  }

  public void tick() {
    if (!active || result != null) return;
    long now = clock.now();
    if (!headingFallback && headingAt >= 0 && now - headingAt > config.headingFreshMs) {
      aligned = false;
      heading = DiscoveryState.HeadingStatus.STALE;
      headingAt = -1;
      cancelProgress();
    }
    while (!pulses.isEmpty() && now - pulses.peekFirst() > config.shakeWindowMs)
      pulses.removeFirst();
    if (lightAt >= 0 && now - lightAt > config.lightFreshMs) {
      darkAt = -1;
      dark = false;
    }
    if (ready()
        && method == DiscoveryResult.Method.LIGHT
        && darkAt >= 0
        && lightAt >= 0
        && now - lightAt <= config.lightFreshMs
        && now - darkAt >= config.coverMs) complete(DiscoveryResult.Method.LIGHT);
    if (ready() && holdAt >= 0 && now - holdAt >= config.holdMs)
      complete(DiscoveryResult.Method.LONG_PRESS);
    publish();
  }

  private void complete(DiscoveryResult.Method m) {
    if (result != null) return;
    holdAt = -1;
    result =
        new DiscoveryResult(
            generation,
            method == DiscoveryResult.Method.LIGHT
                ? "synthetic_cover_demo"
                : "digital_information_software",
            m,
            m == DiscoveryResult.Method.LONG_PRESS
                ? DiscoveryResult.Source.LONG_PRESS
                : (simulated
                    ? DiscoveryResult.Source.SIMULATED
                    : DiscoveryResult.Source.REAL_SENSOR),
            cameraFallback,
            headingFallback);
    listener.onCompletion(result);
  }
}
