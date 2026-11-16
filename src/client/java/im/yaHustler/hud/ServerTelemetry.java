package im.yaHustler.hud;

/** Timing is taken from server time packets, never inferred from client FPS. */
public final class ServerTelemetry {
    private static long lastTime=-1,lastNanos; private static double tps=Double.NaN;
    private ServerTelemetry(){}
    public static void time(long ticks){long now=System.nanoTime();if(lastTime>=0&&ticks>lastTime&&now>lastNanos){double measured=Math.min(20,(ticks-lastTime)*1e9/(now-lastNanos));tps=Double.isNaN(tps)?measured:tps*.7+measured*.3;}lastTime=ticks;lastNanos=now;}
    public static double tps(){return System.nanoTime()-lastNanos>10_000_000_000L?Double.NaN:tps;}
    public static void reset(){lastTime=-1;lastNanos=0;tps=Double.NaN;}
}
