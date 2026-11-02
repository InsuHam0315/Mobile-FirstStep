package com.example.firststep.sensors;

/** Platform-independent circular angle helpers. Degrees increase clockwise from north. */
public final class CompassMath {
    private CompassMath() { }

    /** Normalize a degree value to [0, 360). */
    public static float normalizeDegrees(float degrees) {
        if (Float.isNaN(degrees) || Float.isInfinite(degrees)) return Float.NaN;
        float value = degrees % 360f;
        return value < 0f ? value + 360f : value;
    }

    /** Smallest absolute circular distance, in [0, 180]. */
    public static float shortestDistanceDegrees(float first, float second) {
        float a = normalizeDegrees(first), b = normalizeDegrees(second);
        if (Float.isNaN(a) || Float.isNaN(b)) return Float.NaN;
        float difference = Math.abs(a - b);
        return Math.min(difference, 360f - difference);
    }

    /** Initial true bearing between geographic points, in [0, 360); undefined inputs return NaN. */
    public static float bearingDegrees(double latitudeA, double longitudeA,
                                       double latitudeB, double longitudeB) {
        if (!Double.isFinite(latitudeA) || !Double.isFinite(longitudeA)
                || !Double.isFinite(latitudeB) || !Double.isFinite(longitudeB)
                || Math.abs(latitudeA)>90 || Math.abs(latitudeB)>90
                || Math.abs(longitudeA)>180 || Math.abs(longitudeB)>180) return Float.NaN;
        double latA = Math.toRadians(latitudeA), latB = Math.toRadians(latitudeB);
        double deltaLon = Math.toRadians(longitudeB - longitudeA);
        double y = Math.sin(deltaLon) * Math.cos(latB);
        double x = Math.cos(latA) * Math.sin(latB)
                - Math.sin(latA) * Math.cos(latB) * Math.cos(deltaLon);
        if(x*x+y*y<1e-20) return Float.NaN;
        return normalizeDegrees((float) Math.toDegrees(Math.atan2(y, x)));
    }

    public static float normalize(float degrees) { return normalizeDegrees(degrees); }
    public static float angularDistance(float first, float second) {
        return shortestDistanceDegrees(first, second);
    }
}
