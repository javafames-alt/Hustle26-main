package im.yaHustler.port;

import im.yaHustler.rotation.Look;
import im.yaHustler.rotation.Rotations;
import net.minecraft.client.player.LocalPlayer;

public final class SilentComponent implements IMinecraft {
    private static boolean hasLastAngle;
    private static float lastYaw;
    private static float lastPitch;
    private static float lastHeadYaw;
    private static float lastBodyYaw;
    private static DataSetStep dataSetStep;

    private record DataSetStep(LocalPlayer player, float yaw, float pitch, float headYaw,
                               float nextYaw, float nextPitch) {}

    private SilentComponent() {}

    public static boolean isActive() { return Look.active(); }
    public static boolean isReleasing() { return Rotations.isReleasing(); }
    public static float getFreeYaw() { return Look.yaw(); }
    public static float getFreePitch() { return Look.pitch(); }

    public static void rememberRenderAngles(LocalPlayer player) {
        if (player == null) return;
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        lastHeadYaw = player.yHeadRot;
        lastBodyYaw = player.yBodyRot;
        hasLastAngle = true;
    }

    public static void applyRenderInterpolation() {
        if (!hasLastAngle || !Look.active() || mc.player == null) return;
        mc.player.yRotO = lastYaw;
        mc.player.xRotO = lastPitch;
        mc.player.yHeadRotO = lastHeadYaw;
        mc.player.yBodyRotO = lastBodyYaw;
    }

    public static void interpolateDataSetStep(LocalPlayer player) {
        if (!hasLastAngle || player == null) return;
        dataSetStep = new DataSetStep(player, lastYaw, lastPitch, lastHeadYaw, player.getYRot(), player.getXRot());
        hasLastAngle = false;
    }

    public static void finishDataSetTick(LocalPlayer player) {
        DataSetStep step = dataSetStep;
        dataSetStep = null;
        if (step == null || step.player() != player
                || step.nextYaw() != player.getYRot() || step.nextPitch() != player.getXRot()) return;
        // The player tick copies the new angles over the previous ones before rendering.
        player.yRotO = step.yaw();
        player.xRotO = step.pitch();
        player.yHeadRotO = step.headYaw();
    }

    public static void clear() {
        hasLastAngle = false;
        dataSetStep = null;
        Look.setActive(false);
        Rotations.release();
    }

    public static void forceClear() {
        hasLastAngle = false;
        dataSetStep = null;
        Look.setActive(false);
        Rotations.clear();
    }

    /** End only the caller's silent rotation and align every render angle with the camera. */
    public static void releaseOwnedRotation(Object owner) {
        if (!Rotations.owns(owner)) return;
        hasLastAngle = false;
        Rotations.clear(owner);
        if (mc.player != null) {
            float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.yHeadRot = mc.player.yHeadRotO = yaw;
            mc.player.yBodyRot = mc.player.yBodyRotO = yaw;
        }
        im.yaHustler.rotation.HandSway.reset();
    }

    /** Give the real camera the final aura direction instead of its old free-look angle. */
    public static void finishAt(float yaw, float pitch) {
        if (mc.player == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) { forceClear(); return; }
        hasLastAngle = false;
        float nextYaw = mc.player.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot());
        float nextPitch = net.minecraft.util.Mth.clamp(pitch, -90, 90);
        // clear() disables Look; replace its stored angle before that restoration.
        Look.track(nextYaw, nextPitch);
        Rotations.clear();
        mc.player.setYRot(nextYaw); mc.player.setXRot(nextPitch);
        mc.player.yRotO = nextYaw; mc.player.xRotO = nextPitch;
        mc.player.yHeadRot = mc.player.yHeadRotO = nextYaw;
    }

    public static void startSmoothRelease() { clear(); }
    public static float applyGCD(float from, float to) { return Rotations.snapSensitivity(from, to); }
}
