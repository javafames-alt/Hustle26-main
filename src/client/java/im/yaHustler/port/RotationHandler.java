package im.yaHustler.port;

import im.yaHustler.rotation.Look;
import net.minecraft.util.Mth;

public final class RotationHandler implements IMinecraft {
    private RotationHandler() {}

    public static void apply(float yaw, float pitch) {
        if (mc.player == null) return;
        if (!Look.active()) Look.setActive(true);
        SilentComponent.rememberRenderAngles(mc.player);
        float nextYaw = mc.player.getYRot() + yaw;
        float nextPitch = Mth.clamp(mc.player.getXRot() + pitch, -90.0f, 90.0f);
        mc.player.setYRot(nextYaw);
        mc.player.setXRot(nextPitch);
        mc.player.yHeadRot = nextYaw;
    }

    public static void applyExact(float yaw, float pitch) {
        apply(yaw, pitch);
    }

    public static void applyClient(float yaw, float pitch) {
        if (mc.player == null) return;
        if (Look.active()) Look.setActive(false);
        SilentComponent.rememberRenderAngles(mc.player);
        float nextYaw = mc.player.getYRot() + yaw;
        float nextPitch = Mth.clamp(mc.player.getXRot() + pitch, -90.0f, 90.0f);
        mc.player.setYRot(nextYaw);
        mc.player.setXRot(nextPitch);
        mc.player.yHeadRot = nextYaw;
        Look.track(nextYaw, nextPitch);
    }
}
