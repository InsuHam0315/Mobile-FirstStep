package com.example.firststep.sensors;
import org.junit.Test;
import static org.junit.Assert.*;
public class MotionChallengeTest {
    @Test public void gravityAndSteadyHandDoNotCount() {
        MotionChallenge m=new MotionChallenge();
        for(long t=0;t<4000;t+=20) assertEquals(0,m.reading(0,0,9.81f,t));
    }
    @Test public void horizontalPulseCountsWithoutViolentShake() {
        MotionChallenge m=new MotionChallenge();
        for(long t=0;t<1000;t+=20) m.reading(0,0,9.81f,t);
        assertEquals(1,m.reading(4,0,9.81f,1000));
        assertEquals(1,m.reading(4,0,9.81f,1020));
        for(long t=1040;t<2000;t+=20) m.reading(0,0,9.81f,t);
        assertEquals(2,m.reading(4,0,9.81f,2000));
    }
    @Test public void interruptedMeasurementRestarts() {
        MotionChallenge m=new MotionChallenge();m.reading(0,0,9.81f,0);
        assertEquals(0,m.reading(4,0,9.81f,5000));
    }
}
