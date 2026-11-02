package com.example.firststep.engine;

/** Pure hold-to-complete state. Call update once for each frame/tick. */
public final class HoldProgress {
    private long elapsedMs;
    public double update(boolean pressed, long deltaMs, long requiredMs) {
        if (!pressed) elapsedMs = 0;
        else elapsedMs = Math.min(Math.max(0L, requiredMs), elapsedMs + Math.max(0L, deltaMs));
        return requiredMs <= 0 ? 1.0 : Math.min(1.0, elapsedMs / (double) requiredMs);
    }
    public boolean isComplete(long requiredMs) { return requiredMs <= 0 || elapsedMs >= requiredMs; }
    public void reset() { elapsedMs = 0; }
}
