package com.example.firststep.map;

/** Standard Web Mercator tile coordinates; this is never an arrival/verification source. */
public final class Mercator {
    private Mercator() { }
    public static double x(double longitude, int zoom) { return (longitude + 180) / 360 * (1 << zoom) * 256; }
    public static double y(double latitude, int zoom) {
        double lat=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,latitude)));
        return (1-Math.log(Math.tan(lat)+1/Math.cos(lat))/Math.PI)/2*(1<<zoom)*256;
    }
    public static double longitude(double x,int zoom) { return x/((1<<zoom)*256.0)*360-180; }
    public static double latitude(double y,int zoom) { return Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*y/((1<<zoom)*256.0))))); }
    public static double wrapX(double x,int zoom) { double world=(1<<zoom)*256.0;return ((x%world)+world)%world; }
}
