package com.example.firststep.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class HoldProgressTest {
    @Test public void accumulatesCapsAndResetsWhenReleased() {
        HoldProgress hold = new HoldProgress();
        assertEquals(0.4, hold.update(true, 400, 1000), 0.001);
        assertFalse(hold.isComplete(1000));
        assertEquals(1.0, hold.update(true, 900, 1000), 0.001);
        assertTrue(hold.isComplete(1000));
        assertEquals(0.0, hold.update(false, 250, 1000), 0.001);
        assertFalse(hold.isComplete(1000));
    }
    @Test public void negativeTicksCannotReduceProgress() {
        HoldProgress hold = new HoldProgress();
        assertEquals(0.0, hold.update(true, -50, 1000), 0.001);
    }
}
