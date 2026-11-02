package com.example.firststep.sensors;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompassMathTest {
    @Test public void rejectsUndefinedGeographicBearings() {
        assertTrue(Float.isNaN(CompassMath.bearingDegrees(35,126,35,126)));
        assertTrue(Float.isNaN(CompassMath.bearingDegrees(0,0,0,180)));
        assertTrue(Float.isNaN(CompassMath.bearingDegrees(Double.NaN,0,1,1)));
        assertTrue(Float.isNaN(CompassMath.bearingDegrees(91,0,1,1)));
    }
    @Test public void wrapsAnglesAcrossNorth() {
        assertEquals(359f, CompassMath.normalizeDegrees(-1f), 0.001f);
        assertEquals(2f, CompassMath.shortestDistanceDegrees(359f, 1f), 0.001f);
        assertEquals(180f, CompassMath.shortestDistanceDegrees(0f, 180f), 0.001f);
    }

    @Test public void computesCardinalBearings() {
        assertEquals(90f, CompassMath.bearingDegrees(0, 0, 0, 1), 0.001f);
        assertEquals(0f, CompassMath.bearingDegrees(0, 0, 1, 0), 0.001f);
        assertEquals(270f, CompassMath.bearingDegrees(0, 0, 0, -1), 0.001f);
    }
}
