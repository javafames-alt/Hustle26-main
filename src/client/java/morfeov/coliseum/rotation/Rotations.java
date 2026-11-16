package morfeov.coliseum.rotation;

import morfeov.coliseum.util.AuraUtil;
import morfeov.coliseum.util.MathUtil;
import morfeov.coliseum.port.SilentComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class Rotations {
    private enum State { AIM, RESET, IDLE }

    private static State state = State.IDLE;
    private static float resetSpeed;
    private static int lookMode, holdTicks, priority, elapsed;
    private static Object owner;

    private Rotations() {}

    public static boolean active() { return state != State.IDLE; }
    public static boolean isReleasing() { return state == State.RESET; }
    public static boolean owns(Object claimant) { return claimant != null && owner == claimant && state != State.IDLE; }

    public static boolean clear(Object claimant) {
        if (!owns(claimant)) return false;
        clear();
        return true;
    }

    public static void release() {
        if (state != State.IDLE) state = State.RESET;
        priority = 0;
        holdTicks = 0;
    }

    public static void clear() {
        state = State.IDLE;
        owner = null;
        priority = 0;
        holdTicks = 0;
        Look.setActive(false);
    }

    public static void aim(Rotation rotation, float speed, int mode, int prio) {
        aim(null, rotation, speed, mode, prio);
    }

    public static boolean aim(Object claimant, Rotation rotation, float speed, int mode, int prio) {
        if (priority > prio) return false;
        // Another controller can disable free look while the rotation state is still AIM/RESET.
        if (!Look.active()) Look.setActive(true);
        SilentComponent.rememberRenderAngles(Minecraft.getInstance().player);
        owner = claimant;
        resetSpeed = speed;
        lookMode = mode;
        holdTicks = switch (mode) {
            case 0 -> 1;
            case 1 -> 9;
            case 7 -> 30;
            default -> 10;
        };
        priority = prio;
        state = State.AIM;
        elapsed = 0;
        rotate(rotation, speed, true);
        return true;
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            if (state != State.IDLE) {
                state = State.IDLE;
                owner = null;
                Look.setActive(false);
            }
            return;
        }
        elapsed++;
        if (drifting()) rotate(idleRotation(player), resetSpeed, false);
        if (state == State.AIM && elapsed > holdTicks) state = State.RESET;
        if (state == State.RESET && rotate(current(player), resetSpeed, true)) {
            Look.setActive(false);
            state = State.IDLE;
            owner = null;
            priority = 0;
        }
    }

    public static void correctInput() {
        if (drifting()) MoveFix.request(Look.yaw(), 10);
    }

    private static boolean drifting() {
        return elapsed >= 2 && elapsed <= holdTicks && state != State.IDLE;
    }

    private static Rotation current(LocalPlayer player) {
        return new Rotation(player.getYRot() + Mth.wrapDegrees(Look.yaw() - player.getYRot()), Look.pitch());
    }

    private static Rotation idleRotation(LocalPlayer player) {
        if (lookMode == 1) {
            float yaw = AuraUtil.lerpAngle(player.getYRot(), Look.yaw(), MathUtil.random(0.1f, 0.45f));
            float pitch = AuraUtil.lerpAngle(player.getXRot(), Look.pitch(), MathUtil.random(0.1f, 0.45f));
            return new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
        }
        return new Rotation(Look.yaw(), Look.pitch());
    }

    private static boolean rotate(Rotation rotation, float speed, boolean restart) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return true;
        float yawDelta = Mth.wrapDegrees(rotation.yaw() - player.getYRot());
        float pitchDelta = rotation.pitch() - player.getXRot();
        float total = Math.abs(yawDelta) + Math.abs(pitchDelta);
        float yawSpeed = total == 0.0f ? 0.0f : Math.abs(yawDelta / total) * speed;
        float pitchSpeed = total == 0.0f ? 0.0f : Math.abs(pitchDelta / total) * speed;
        float newYaw = snapSensitivity(player.getYRot(), player.getYRot() + Mth.clamp(yawDelta, -yawSpeed, yawSpeed));
        float newPitch = Mth.clamp(snapSensitivity(player.getXRot(), player.getXRot() + Mth.clamp(pitchDelta, -pitchSpeed, pitchSpeed)), -90.0f, 90.0f);
        player.setYRot(newYaw);
        player.setXRot(newPitch);
        if (restart) elapsed = 0;
        return new Rotation(player.getYRot(), player.getXRot()).distanceTo(rotation) < speed;
    }

    public static float snapSensitivity(float from, float to) {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
        double gcd = sensitivity * sensitivity * sensitivity * 8.0;
        return (float) (from + Math.round((to - from) / gcd / 0.15) * gcd * 0.15);
    }
}
