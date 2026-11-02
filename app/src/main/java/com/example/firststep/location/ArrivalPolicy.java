package com.example.firststep.location;

/** A fresh, accurate sequence of fixes is required; cached arrival is never persisted. */
public final class ArrivalPolicy {
    private long enteredAt = -1;
    private long lastSampleAt = -1;
    private boolean arrived;
    public boolean evaluate(float distanceMeters, float accuracyMeters, long ageMillis, long nowMillis) {
        // A long gap between callbacks is not evidence of continuous presence.
        if (lastSampleAt >= 0 && (nowMillis - lastSampleAt > 5_000 || nowMillis < lastSampleAt)) reset();
        lastSampleAt = nowMillis;
        if (!Float.isFinite(distanceMeters) || distanceMeters < 0 || !Float.isFinite(accuracyMeters)
                || accuracyMeters < 0 || accuracyMeters > 25 || ageMillis < 0 || ageMillis > 10_000) {
            reset(); return false;
        }
        if (distanceMeters > 60) { reset(); return false; }
        if (arrived) return true;
        if (distanceMeters > 40) { enteredAt = -1; return false; }
        if (enteredAt < 0 || nowMillis < enteredAt) enteredAt = nowMillis;
        arrived = nowMillis - enteredAt >= 3_000;
        return arrived;
    }
    public void reset() { enteredAt = -1; lastSampleAt = -1; arrived = false; }
}
