package im.yaHustler.render.coliseimvisual;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Interpolated 60 Hz camera history, independent of the current frame rate. */
public final class CameraMotionHistory {
    private static final int CAPACITY = 128;
    private final Sample[] samples = new Sample[CAPACITY];
    private final Sample previous = new Sample();
    private final Matrix4f previousView = new Matrix4f();
    private int start, count;

    public void reset() { start = count = 0; }
    public Matrix4fc projection() { return previous.projection; }
    public Matrix4fc view() { return previousView; }
    public Vector3dc position() { return previous.position; }
    public float yaw() { return previous.yaw; }
    public float pitch() { return previous.pitch; }
    private Sample sample(int index) { return samples[(start + index) % CAPACITY]; }

    public boolean record(long now, Matrix4fc projection, Matrix4fc view,
                          double x, double y, double z, float yaw, float pitch) {
        if (!projection.isFinite() || !view.isFinite() || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                reset(); return false;
        }
        if (count > 0) {
                Sample last = sample(count - 1);
                if (now <= last.time || now - last.time > 250_000_000L || last.position.distanceSquared(x,y,z) > 64) reset();
        }
        int index = (start + count) % CAPACITY;
        Sample current = samples[index];
        if (current == null) samples[index] = current = new Sample();
        current.time = now;
        current.projection.set(projection);
        view.getNormalizedRotation(current.rotation);
        current.position.set(x,y,z);
        current.yaw = yaw; current.pitch = pitch;
        if (count == CAPACITY) start = (start + 1) % CAPACITY; else count++;
        if (count < 2) return false;
        long target = now - 16_666_666L;
        while (count > 2 && sample(1).time <= target) { start = (start + 1) % CAPACITY; count--; }
        Sample from = sample(0), to = sample(1);
        double t = Math.clamp((double)(target - from.time) / (to.time - from.time), 0, 1);
        from.projection.lerp(to.projection, (float)t, previous.projection);
        from.rotation.slerp(to.rotation, t, previous.rotation).normalize().get(previousView);
        from.position.lerp(to.position, t, previous.position);
        float yawDelta = to.yaw - from.yaw;
        yawDelta -= (float)Math.floor((yawDelta + 180) / 360) * 360;
        previous.yaw = from.yaw + yawDelta * (float)t;
        previous.pitch = from.pitch + (to.pitch - from.pitch) * (float)t;
        return true;
    }
    private static final class Sample {
        long time;
        final Matrix4f projection = new Matrix4f();
        final Quaterniond rotation = new Quaterniond();
        final Vector3d position = new Vector3d();
        float yaw, pitch;
    }
}
