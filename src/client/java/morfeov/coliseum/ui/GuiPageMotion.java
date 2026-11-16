package morfeov.coliseum.ui;

/** A short content transition; the sidebar and search remain stationary and interactive. */
public final class GuiPageMotion {
    public record Frame(float opacity, float x, float y) {
        public double localX(double pointer) { return pointer - x; }
        public double localY(double pointer) { return pointer - y; }
    }
    private long started = -1;
    private long leaving = -1;
    private Frame leaveFrom;
    private int direction = 1;
    public void start(long now, int direction) { started = now; leaving = -1; this.direction = direction < 0 ? -1 : 1; }
    /** Capture the displayed pose so interrupted transitions never jump back to full opacity. */
    public void leave(long now, int direction) {
        if (leaving >= 0) return;
        leaveFrom = sample(now);
        leaving = now;
        this.direction = direction < 0 ? -1 : 1;
    }
    public boolean readyToEnter(long now) { return leaving >= 0 && now - leaving >= 100_000_000L; }
    public int direction() { return direction; }
    public Frame sample(long now) {
        if (leaving >= 0) {
            float e = GuiMotion.smooth(GuiMotion.progress(now, leaving, 100_000_000L));
            return new Frame(leaveFrom.opacity * (1 - e), leaveFrom.x - 8 * direction * e, leaveFrom.y - 2 * e);
        }
        float e = started < 0 ? 1 : GuiMotion.ease(GuiMotion.progress(now, started, 240_000_000L));
        if (e >= 1) return new Frame(1, 0, 0);
        return new Frame(e, 14 * direction * (1 - e), 5 * (1 - e));
    }
}
