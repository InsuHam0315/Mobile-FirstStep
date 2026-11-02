package com.example.firststep.sensors;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompassChallengeTest {
    private static final int MEDIUM_ACCURACY = 2;
    private static final int HIGH_ACCURACY = 3;

    @Test public void holdsAcrossNorthBoundaryForRequiredDuration() {
        CompassChallenge challenge = new CompassChallenge(0f);
        assertFalse(challenge.update(359f, HIGH_ACCURACY, 0).complete);
        assertFalse(challenge.update(1f, HIGH_ACCURACY, 500).complete);
        assertFalse(challenge.update(359f, HIGH_ACCURACY, 1000).complete);
        CompassChallenge.State finished = challenge.update(0f, HIGH_ACCURACY, 1500);
        assertTrue(finished.complete);
        assertEquals(1f, finished.progress, 0.001f);
    }

    @Test public void toleranceBoundaryCountsButNoiseOutsideResetsHold() {
        CompassChallenge challenge = new CompassChallenge(90f);
        challenge.update(90f, MEDIUM_ACCURACY, 0);
        challenge.update(102f, MEDIUM_ACCURACY, 500); // exactly 12 degrees
        assertEquals(1000f / 1500f, challenge.update(90f, MEDIUM_ACCURACY, 1000).progress, 0.001f);
        CompassChallenge.State outside = challenge.update(102.1f, MEDIUM_ACCURACY, 1250);
        assertEquals(0f, outside.progress, 0.001f);
        assertFalse(outside.complete);
    }

    @Test public void leavingTargetRequiresFreshInRangeTimeBeforeProgress() {
        CompassChallenge challenge = new CompassChallenge(90f);
        challenge.update(120f, HIGH_ACCURACY, 0);
        assertEquals(0f, challenge.update(90f, HIGH_ACCURACY, 500).progress, 0.001f);
        assertEquals(500f / 1500f, challenge.update(90f, HIGH_ACCURACY, 1000).progress, 0.001f);
        assertEquals(1000f / 1500f, challenge.update(90f, HIGH_ACCURACY, 1500).progress, 0.001f);
        assertTrue(challenge.update(90f, HIGH_ACCURACY, 2000).complete);
    }

    @Test public void unreliableAccuracyAndStaleSamplesResetHold() {
        CompassChallenge challenge = new CompassChallenge(180f);
        challenge.update(180f, HIGH_ACCURACY, 100);
        assertEquals(400f / 1500f, challenge.update(180f, HIGH_ACCURACY, 500).progress, 0.001f);
        assertEquals(0f, challenge.update(180f, 1, 600).progress, 0.001f);

        challenge.update(180f, HIGH_ACCURACY, 1000);
        challenge.update(180f, HIGH_ACCURACY, 1500);
        CompassChallenge.State stale = challenge.update(180f, HIGH_ACCURACY, 2101);
        assertEquals(0f, stale.progress, 0.001f);
        assertFalse(stale.complete);
    }
}
