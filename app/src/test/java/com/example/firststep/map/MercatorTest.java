package com.example.firststep.map;
import org.junit.Test;
import static org.junit.Assert.*;
public class MercatorTest {
    @Test public void campusCoordinatesRoundTrip() {
        for(int z=3;z<=19;z++) {
            assertEquals(35.9456011,Mercator.latitude(Mercator.y(35.9456011,z),z),1e-7);
            assertEquals(126.6802365,Mercator.longitude(Mercator.x(126.6802365,z),z),1e-7);
        }
    }
    @Test public void polesClampedAndWorldWraps() {
        assertTrue(Double.isFinite(Mercator.y(90,16)));
        assertEquals(2047,Mercator.wrapX(-1,3),0);
        assertEquals(0,Mercator.wrapX(2048,3),0);
    }
}
