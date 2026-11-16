package morfeov.coliseum.port;
public final class TimerSpeed {
    private static float speed=1;
    public static void setSpeed(float next) { speed=Float.isFinite(next)?Math.clamp(next,.05f,10):1; }
    public static void reset() { speed=1; }
    public static float multiplier() { return speed; }
}
