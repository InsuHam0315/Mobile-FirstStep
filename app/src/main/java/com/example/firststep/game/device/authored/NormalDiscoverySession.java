package com.example.firststep.game.device.authored;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.discovery.DiscoveryResult;
import com.example.firststep.game.presentation.discovery.DiscoveryController;
import com.example.firststep.game.presentation.discovery.DiscoveryState;
import com.example.firststep.game.sync.SyncScope;
import java.util.Objects;
import java.util.UUID;

/** Main-thread session for a single, already admitted NORMAL discovery. */
public final class NormalDiscoverySession {
  public static final long RECEIPT_MAX_AGE_MS = 5000L;
  public interface Listener {
    void onState(DiscoveryState state);
    void onCompleted(Receipt receipt);
  }

  /** Opaque in-process evidence. It cannot be reconstructed from an Intent. */
  public static final class Receipt {
    public final DiscoveryResult.Method method;
    public final DiscoveryResult.Source source;
    public final boolean cameraFallback, headingFallback;
    private final NormalDiscoverySession issuer;
    private final long issuedAtMillis;
    private boolean consumed;

    private Receipt(NormalDiscoverySession issuer, DiscoveryResult result, long issuedAtMillis) {
      this.issuer = issuer;
      this.issuedAtMillis = issuedAtMillis;
      method = result.method;
      source = result.source;
      cameraFallback = result.cameraFallback;
      headingFallback = result.headingFallback;
    }
  }

  private final SyncScope scope;
  private final String nonce;
  private final float targetHeading;
  private final Listener listener;
  private final DiscoveryController.Clock clock;
  private final DiscoveryController controller;
  private long generation;
  private boolean active, started;
  private Receipt issued;

  public NormalDiscoverySession(SyncScope scope, String nonce, float targetHeading,
      DiscoveryController.Clock clock, Listener listener) {
    this.scope = Objects.requireNonNull(scope, "scope");
    if (scope.mode != Progress.Mode.NORMAL || !canonicalUuid(nonce)
        || !Float.isFinite(targetHeading) || targetHeading < 0 || targetHeading >= 360) {
      throw new IllegalArgumentException("Invalid NORMAL discovery identity or heading");
    }
    this.nonce = nonce;
    this.targetHeading = targetHeading;
    this.listener = Objects.requireNonNull(listener, "listener");
    this.clock = Objects.requireNonNull(clock, "clock");
    controller = new DiscoveryController(this.clock,
        new DiscoveryController.Listener() {
          @Override public void onState(DiscoveryState state) {
            if (active) NormalDiscoverySession.this.listener.onState(state);
          }
          @Override public void onCompletion(DiscoveryResult result) {
            if (!active || issued != null || result.arrivalSimulated
                || result.method == DiscoveryResult.Method.LIGHT
                || result.source == DiscoveryResult.Source.SIMULATED
                || !scope.buildingId.equals(result.targetId)) return;
            // A fresh trusted controller result becomes an opaque, one-use receipt.
            issued = new Receipt(NormalDiscoverySession.this, result, NormalDiscoverySession.this.clock.now());
            NormalDiscoverySession.this.listener.onCompleted(issued);
          }
        }, false);
  }

  private static boolean canonicalUuid(String value) {
    try { return value != null && UUID.fromString(value).toString().equals(value); }
    catch (IllegalArgumentException invalid) { return false; }
  }

  /** Caller must first establish trusted current arrival, and recheck it on consumption. */
  public synchronized void start() {
    if (started) throw new IllegalStateException("Session already used");
    started = true;
    active = true;
    generation = controller.resume();
    controller.trustedArrival();
    controller.select(DiscoveryResult.Method.SHAKE);
  }

  public synchronized void cancel() {
    active = false;
    controller.pause();
  }

  public synchronized DiscoveryState snapshot() { return controller.snapshot(); }
  public synchronized void camera(DiscoveryState.CameraStatus status) { controller.setCamera(generation, status); }
  public synchronized void cameraFallback() { controller.cameraFallback(); }
  public synchronized void headingFallback() { controller.headingFallback(); }
  public synchronized void headingUnavailable(boolean unreliable) { controller.headingUnavailable(generation, unreliable); }
  public synchronized void availability(boolean heading, boolean motion) {
    controller.sensorAvailability(generation, motion, false);
    if (!heading) controller.headingUnavailable(generation, false);
  }
  public synchronized void heading(float absoluteDegrees, boolean reliable) {
    controller.heading(generation, absoluteDegrees - targetHeading, reliable);
  }
  public synchronized void acceleration(float magnitude) { controller.acceleration(generation, magnitude); }
  public synchronized void holdStart() { controller.holdStart(); }
  public synchronized void holdCancel() { controller.holdCancel(); }
  public synchronized void tick() { controller.tick(); }

  /** The caller rechecks owner and arrival while holding its owner guard before invoking this. */
  public synchronized boolean consume(Receipt receipt, SyncScope expected, String expectedNonce) {
    if (!active || issued == null || receipt != issued || receipt.issuer != this || receipt.consumed
        || !nonce.equals(expectedNonce) || !sameScope(scope, expected)) return false;
    long now = clock.now();
    if (now < receipt.issuedAtMillis || now - receipt.issuedAtMillis > RECEIPT_MAX_AGE_MS) {
      cancel();
      return false;
    }
    receipt.consumed = true;
    active = false;
    controller.pause();
    return true;
  }

  private static boolean sameScope(SyncScope a, SyncScope b) {
    return b != null && a.mode == b.mode && a.sessionGeneration == b.sessionGeneration
        && a.projectId.equals(b.projectId) && a.uid.equals(b.uid)
        && a.datasetId.equals(b.datasetId) && a.buildingId.equals(b.buildingId);
  }
}
