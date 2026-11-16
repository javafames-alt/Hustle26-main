package im.yaHustler.hud;

/** A readable pause at each end and smooth travel through the entire overflowing title. */
public final class TextMarquee {
    private String text = "";
    private float overflow;
    private long started;
    public float offset(String value, float overflow, long now) {
        overflow = Math.max(0, overflow);
        if (!text.equals(value) || Math.abs(this.overflow - overflow) > .1f) {
            text = value; this.overflow = overflow; started = now;
        }
        if (overflow == 0) return 0;
        double pause = 1.3, travel = Math.max(1, overflow / 18.0);
        double time = Math.max(0, (now - started) / 1e9) % (2 * pause + 2 * travel);
        if (time < pause) return 0;
        time -= pause;
        if (time < travel) return overflow * ease(time / travel);
        time -= travel;
        if (time < pause) return overflow;
        return overflow * (1 - ease((time - pause) / travel));
    }
    private static float ease(double progress) { return (float)(.5 - .5 * Math.cos(Math.PI * progress)); }
}
