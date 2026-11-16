package morfeov.coliseum.hud;

/** Damped drag response in GUI pixels/second, independent of the mouse polling rate. */
public final class HudDragMotion {
    private double angle, velocity, target;
    private double displacement, elapsed;

    public void move(double dx, double dy, double seconds) {
        displacement += dx + dy * .35;
        elapsed += Math.clamp(seconds, 1d / 8000, .1);
    }
    public float step(double seconds, boolean held, boolean enabled) {
        if (!enabled) { angle = velocity = target = displacement = elapsed = 0; return 0; }
        // Accumulate events between frames: subpixel/rounded mouse events must not cancel the lean.
        if (elapsed > 0) target = Math.clamp(displacement / elapsed * .012, -8, 8);
        displacement = elapsed = 0;
        double dt = Math.clamp(seconds, 0, .1), omega = 20;
        if (!held) target = 0;
        double offset = angle - target, c = velocity + omega * offset, decay = Math.exp(-omega * dt);
        angle = target + (offset + c * dt) * decay;
        velocity = (velocity - omega * c * dt) * decay;
        target *= Math.exp(-12 * dt);
        return (float)Math.toRadians(angle);
    }
}
