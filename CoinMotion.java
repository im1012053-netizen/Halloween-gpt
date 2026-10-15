package com.halloweenhunt;

/** Pure animation math shared with the offline timing checks. Coordinates stay below the crosshair. */
public final class CoinMotion {
    public static final int DURATION_TICKS = 60;
    public static final double DURATION_SECONDS = 3.0;
    public record Frame(double x, double y, double z, float spin, float faceTurn, float handLift, boolean revealed) {}
    private CoinMotion() {}
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    public static Frame frame(double seconds, boolean heads) {
        double air = clamp((seconds - 0.25) / 1.75);
        double smooth = air * air * (3 - 2 * air);
        double lift = Math.sin(Math.PI * air) * 0.27;
        double flick = seconds >= 0 && seconds < 0.25 ? Math.sin(Math.PI * seconds / 0.25) * 0.065 : 0;
        double catchMove = seconds >= 1.85 && seconds < 2.4 ? Math.sin(Math.PI * (seconds - 1.85) / 0.55) * 0.045 : 0;
        return new Frame(0.47, -0.57 + lift + flick + catchMove, -1.12,
                (float)(1080 * smooth), (float)((heads ? 0 : 180) * smooth), (float)(flick + catchMove), seconds >= 2.4);
    }
}

