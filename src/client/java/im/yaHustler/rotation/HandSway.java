package im.yaHustler.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class HandSway {
    private static final float FOLLOW = 0.5f;
    private static final float LIMIT = 30.0f;
    private static float aimYaw, previousAimYaw, aimPitch, previousAimPitch;
    private static float trailYaw, previousTrailYaw, trailPitch, previousTrailPitch;
    private static int lastTick;
    private static boolean started;

    private HandSway() {}

    public static void reset() {
        started = false;
    }

    public static void update() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            started = false;
            return;
        }
        float yaw = Look.yaw();
        float pitch = Look.pitch();
        if (!started) {
            started = true;
            lastTick = player.tickCount;
            aimYaw = previousAimYaw = trailYaw = previousTrailYaw = yaw;
            aimPitch = previousAimPitch = trailPitch = previousTrailPitch = pitch;
            return;
        }
        int tick = player.tickCount;
        if (tick == lastTick) return;
        lastTick = tick;
        previousAimYaw = aimYaw;
        previousAimPitch = aimPitch;
        aimYaw = yaw;
        aimPitch = pitch;
        previousTrailYaw = trailYaw;
        previousTrailPitch = trailPitch;
        trailYaw += Mth.wrapDegrees(aimYaw - trailYaw) * FOLLOW;
        trailPitch += (aimPitch - trailPitch) * FOLLOW;
    }

    public static float yawOffset(float frameInterp) {
        if (!started) return 0.0f;
        float aim = previousAimYaw + Mth.wrapDegrees(aimYaw - previousAimYaw) * frameInterp;
        float trail = previousTrailYaw + Mth.wrapDegrees(trailYaw - previousTrailYaw) * frameInterp;
        return Mth.clamp(Mth.wrapDegrees(aim - trail), -LIMIT, LIMIT);
    }

    public static float pitchOffset(float frameInterp) {
        if (!started) return 0.0f;
        float aim = Mth.lerp(frameInterp, previousAimPitch, aimPitch);
        float trail = Mth.lerp(frameInterp, previousTrailPitch, trailPitch);
        return Mth.clamp(aim - trail, -LIMIT, LIMIT);
    }
}
