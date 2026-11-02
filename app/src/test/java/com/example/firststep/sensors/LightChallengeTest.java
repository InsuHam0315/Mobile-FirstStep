package com.example.firststep.sensors;

import org.junit.Test;
import static org.junit.Assert.*;

public class LightChallengeTest {
    @Test public void unchangedSensorReadingStillCompletesTimedStages() {
        LightChallenge game = new LightChallenge();
        game.reading(100); game.tick(0);
        assertEquals(LightChallenge.Stage.COVER, game.tick(1500));
        game.reading(10); game.tick(1550);
        assertEquals(LightChallenge.Stage.RECOVER, game.tick(2350));
        game.reading(90); game.tick(2400);
        assertEquals(LightChallenge.Stage.COMPLETE, game.tick(3200));
    }
    @Test public void briefCoverDoesNotAccumulateAcrossInterruptions() {
        LightChallenge game = new LightChallenge();
        game.reading(100); game.tick(0); game.tick(1500);
        game.reading(10); game.tick(1550); game.tick(2200);
        game.reading(100); game.tick(2250);
        game.reading(10); game.tick(2300);
        assertEquals(LightChallenge.Stage.COVER, game.tick(3000));
        assertEquals(LightChallenge.Stage.RECOVER, game.tick(3100));
    }
    @Test public void darkBaselineRequiresFallbackRatherThanFalseSuccess() {
        LightChallenge game = new LightChallenge();
        game.reading(0); game.tick(0);
        assertEquals(LightChallenge.Stage.TOO_DARK, game.tick(1500));
        game.reading(1000);
        assertEquals(LightChallenge.Stage.TOO_DARK, game.tick(10000));
    }
    @Test public void missingInvalidReadingsCannotPassCalibration() {
        LightChallenge game = new LightChallenge();
        game.reading(Float.NaN); game.reading(-10); game.tick(0);
        assertEquals(LightChallenge.Stage.CALIBRATING, game.tick(5000));
    }
}
