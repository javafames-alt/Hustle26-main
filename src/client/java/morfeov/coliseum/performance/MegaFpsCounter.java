package morfeov.coliseum.performance;

/** Decorative HUD/F3 display that follows measured performance without abrupt jumps. */
public final class MegaFpsCounter {
    static final long INTERVAL_NANOS = 200_000_000L;
    private long updated, started, lastFrame;
    private double displayed, frameNanos;
    private int value;
    private boolean initialized, hasFrame;

    /** Called once per world frame, independently of how many counters are visible. */
    public void recordFrame(long now) {
        if (hasFrame) {
            long elapsed = now - lastFrame;
            if (elapsed > 0 && elapsed < 2_000_000_000L) {
                double measured = Math.clamp(elapsed, 100_000L, 250_000_000L);
                double blend = -Math.expm1(-measured / 800_000_000.0);
                frameNanos = frameNanos == 0 ? measured : frameNanos + (measured - frameNanos) * blend;
            }
        }
        hasFrame = true;
        lastFrame = now;
    }

    public int sample(long now, int actualFps) {
        double fps = hasFrame && frameNanos > 0 && now - lastFrame < 500_000_000L
                ? 1_000_000_000.0 / frameNanos : Math.max(1, actualFps > 0 ? actualFps : 60);
        // Compress real performance into a decorative range. The mapping is
        // monotonic: heavier frames lower the target, never randomise it.
        double target = 1500 + 1500 * fps / (fps + 90);
        if (!initialized) {
            displayed = target;
            value = (int)Math.round(displayed);
            initialized = true;
            started = updated = now;
        } else if (now - updated >= INTERVAL_NANOS) {
            double seconds = Math.min(.25, (now - updated) / 1_000_000_000.0);
            // A two-point drift keeps stable scenes alive without unrelated jumps.
            target += 2 * Math.sin((now - started) / 1_000_000_000.0 * .9);
            double change = (target - displayed) * -Math.expm1(-seconds / 1.2);
            // At most ten points per normal display update, even after a stall.
            displayed = Math.clamp(displayed + Math.clamp(change, -50 * seconds, 50 * seconds), 1500, 3000);
            value = (int)Math.round(displayed);
            updated = now;
        }
        return value;
    }

    public void reset() { initialized = hasFrame = false; frameNanos = 0; }
}
