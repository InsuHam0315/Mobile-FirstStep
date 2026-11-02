package com.example.firststep.sensors;

/** Removes an estimated gravity vector and counts distinct, separated movement pulses. */
public final class MotionChallenge {
    private final float[] gravity = new float[3];
    private boolean initialized, armed = true;
    private long initializedAt, previousAt = -1, lastPulse = -1;
    private int count;
    public int reading(float x, float y, float z, long now) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return count;
        if (!initialized) {
            gravity[0]=x;gravity[1]=y;gravity[2]=z;initialized=true;initializedAt=now;previousAt=now;
            return count;
        }
        if (now < previousAt || now-previousAt > 2000) { reset(); return reading(x,y,z,now); }
        float dt=(now-previousAt)/1000f;previousAt=now;
        float alpha=(float)Math.exp(-dt/.5f);
        float[] values={x,y,z};float squared=0;
        for(int i=0;i<3;i++) { gravity[i]=alpha*gravity[i]+(1-alpha)*values[i];float linear=values[i]-gravity[i];squared+=linear*linear; }
        float movement=(float)Math.sqrt(squared);
        if(now-initializedAt<700) return count;
        if(movement<1.2f) armed=true;
        if(armed && movement>=3 && (lastPulse<0 || now-lastPulse>=500)) {
            count=Math.min(5,count+1);armed=false;lastPulse=now;
        }
        return count;
    }
    public void reset() { initialized=false;armed=true;count=0;previousAt=-1;lastPulse=-1; }
}
