package com.example.firststep.location;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArrivalPolicyTest {
    @Test public void callbackGapCannotProveContinuousDwell() {
        ArrivalPolicy p = new ArrivalPolicy();
        assertFalse(p.evaluate(20, 5, 0, 0));
        assertFalse(p.evaluate(20, 5, 0, 60_000));
        assertTrue(p.evaluate(20, 5, 0, 63_000));
    }
    @Test public void requiresDwellAndUsesExitHysteresis() {
        ArrivalPolicy p = new ArrivalPolicy();
        assertFalse(p.evaluate(20, 5, 100, 1000));
        assertFalse(p.evaluate(20, 5, 100, 3999));
        assertTrue(p.evaluate(20, 5, 100, 4000));
        assertTrue(p.evaluate(55, 5, 100, 5000));
        assertFalse(p.evaluate(61, 5, 100, 6000));
    }
    @Test public void staleOrInaccurateFixRevokesArrival() {
        ArrivalPolicy p = new ArrivalPolicy(); p.evaluate(0, 1, 0, 0);
        assertTrue(p.evaluate(0, 1, 0, 3000));
        assertFalse(p.evaluate(0, 1, 10001, 4000));
        assertFalse(p.evaluate(0, 26, 0, 8000));
        assertFalse(p.evaluate(Float.NaN, 1, 0, 9000));
    }
    @Test public void leavingEntryRadiusRestartsDwell() {
        ArrivalPolicy p = new ArrivalPolicy(); p.evaluate(30, 1, 0, 0);
        assertFalse(p.evaluate(41, 1, 0, 2000));
        assertFalse(p.evaluate(30, 1, 0, 3000));
        assertFalse(p.evaluate(30, 1, 0, 5000));
        assertTrue(p.evaluate(30, 1, 0, 6000));
    }
}
