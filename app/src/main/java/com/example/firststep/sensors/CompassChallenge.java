package com.example.firststep.sensors;

/** Pure heading hold challenge. Accuracy follows Android SensorManager status values 0..3. */
public final class CompassChallenge {
    public static final float TOLERANCE_DEGREES = 12f;
    public static final long REQUIRED_HOLD_MS = 1500L;
    public static final long MAX_SAMPLE_GAP_MS = 500L;
    private final float targetDegrees;
    private long heldMs;
    private long lastSampleMs = -1L;
    private boolean complete;

    public CompassChallenge(float targetDegrees) {
        float normalized = CompassMath.normalizeDegrees(targetDegrees);
        if (Float.isNaN(normalized)) throw new IllegalArgumentException("Target heading must be finite");
        this.targetDegrees = normalized;
    }

    /**
     * Process a sensor sample. Samples below MEDIUM accuracy (status 2) clear the hold.
     * A timestamp gap over 500 ms is treated as interrupted tracking and starts over.
     */
    public State update(float headingDegrees, int accuracy, long realtimeMs) {
        if (complete) return state("방향을 유지했어요.");
        float normalized = CompassMath.normalizeDegrees(headingDegrees);
        if (Float.isNaN(normalized) || accuracy <= 1) {
            resetHold();
            return state("방향 신호가 불안정해요. 휴대전화를 천천히 움직여 보세요.");
        }
        if (CompassMath.shortestDistanceDegrees(normalized, targetDegrees) > TOLERANCE_DEGREES) {
            heldMs = 0L;
            lastSampleMs = -1L;
            return state("표시된 방향에 맞춰 휴대전화를 천천히 돌려 주세요.");
        }
        if (lastSampleMs >= 0L) {
            long gap = realtimeMs - lastSampleMs;
            if (gap < 0L || gap > MAX_SAMPLE_GAP_MS) heldMs = 0L;
            else {
                heldMs = Math.min(REQUIRED_HOLD_MS, heldMs + gap);
            }
        }
        lastSampleMs = realtimeMs;
        if (heldMs >= REQUIRED_HOLD_MS) {
            complete = true;
            return state("방향을 유지했어요.");
        }
        return state("좋아요. 방향을 잠시 유지해 주세요.");
    }

    public void reset() {
        heldMs = 0L;
        lastSampleMs = -1L;
        complete = false;
    }

    private void resetHold() {
        heldMs = 0L;
        lastSampleMs = -1L;
    }

    private State state(String message) {
        return new State(Math.min(1f, heldMs / (float) REQUIRED_HOLD_MS), complete, message);
    }

    public static final class State {
        public final float progress;
        public final boolean complete;
        public final String message;
        private State(float progress, boolean complete, String message) {
            this.progress = progress;
            this.complete = complete;
            this.message = message;
        }
    }
}
