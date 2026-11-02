package com.example.firststep.sensors;

/** A timed light challenge. Ticks continue even when an on-change light sensor is silent. */
public final class LightChallenge {
    public enum Stage { CALIBRATING, TOO_DARK, COVER, RECOVER, COMPLETE }
    private Stage stage = Stage.CALIBRATING;
    private long startedAt = -1L, conditionSince = -1L;
    private float latest = Float.NaN, total, baseline;
    private int samples;

    public void reading(float lux) {
        if (Float.isNaN(lux) || Float.isInfinite(lux) || lux < 0) return;
        latest = lux;
        if (stage == Stage.CALIBRATING) { total += lux; samples++; }
    }

    public Stage tick(long now) {
        if (startedAt < 0) startedAt = now;
        if (stage == Stage.CALIBRATING) {
            if (now - startedAt >= 1500 && samples > 0) {
                baseline = total / samples;
                stage = baseline < 8 ? Stage.TOO_DARK : Stage.COVER;
                conditionSince = -1;
            }
        } else if (stage == Stage.COVER) {
            if (sustained(latest <= baseline * .35f, now)) {
                stage = Stage.RECOVER;
                conditionSince = -1;
            }
        } else if (stage == Stage.RECOVER && sustained(latest >= baseline * .75f, now)) {
            stage = Stage.COMPLETE;
        }
        return stage;
    }

    private boolean sustained(boolean condition, long now) {
        if (!condition) { conditionSince = -1; return false; }
        if (conditionSince < 0) conditionSince = now;
        return now - conditionSince >= 800;
    }
    public float getBaseline() { return baseline; }
}
