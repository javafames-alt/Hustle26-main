package morfeov.coliseum.module.impl.combat.aura;

import morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation;
import morfeov.coliseum.module.impl.combat.rotation.RotationComponent;
import morfeov.coliseum.port.RotationHandler;
import morfeov.coliseum.port.SilentComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class AimController {
    private static int enableRampTick;
    private static final int rampTicks = 4;
    private static float observedLastYaw = Float.NaN;
    private static float observedLastPitch = Float.NaN;
    private static float observedYawSpeed = 32.0f;
    private static float observedPitchSpeed = 12.0f;
    private static int observedSamples;
    private static final int observedMinSamples = 24;

    private AimController() {}

    public static void apply(AimPart observation, Rotation mode) {
        apply(observation, mode, false);
    }

    public static void apply(AimPart observation, Rotation mode, boolean clientLook) {
        if (observation == null) return;
        if (NeuroRotation.getInstance().recorder().isRecording()) return;

        if (mode == Rotation.ReallyWorld) {
            if (enableRampTick < rampTicks) enableRampTick++;
            float ramp = smoothStep01(enableRampTick / (float) rampTicks);
            observation = new AimPart(
                    observation.deltaYaw * ramp,
                    observation.deltaPitch * ramp,
                    observation.distance,
                    observation.aimingAtTarget,
                    observation.airborne,
                    observation.target,
                    observation.aimPoint,
                    observation.attackRange,
                    observation.moving,
                    observation.aimPart
            );
        }

        float[] step = RotationComponent.step(mode, observation);
        applyDelta(step[0], step[1], clientLook, mode);
    }

    public static void reset() {
        RotationComponent.reset();
        enableRampTick = 0;
        observedLastYaw = Float.NaN;
        observedLastPitch = Float.NaN;
    }

    public static boolean hasLearnedSensitivity() {
        return observedSamples >= observedMinSamples;
    }

    public static float getLearnedYawSpeed() {
        return observedYawSpeed;
    }

    public static float getLearnedPitchSpeed() {
        return observedPitchSpeed;
    }

    public static void tickObserveSensitivity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || SilentComponent.isActive()) {
            observedLastYaw = Float.NaN;
            observedLastPitch = Float.NaN;
            return;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        if (!Float.isNaN(observedLastYaw)) {
            float dy = Math.abs(Mth.wrapDegrees(yaw - observedLastYaw));
            float dp = Math.abs(pitch - observedLastPitch);

            if (dy > 0.3f && dy < 90.0f) {
                float alpha = dy > observedYawSpeed ? 0.25f : 0.015f;
                observedYawSpeed += alpha * (dy - observedYawSpeed);
                observedSamples++;
            }
            if (dp > 0.2f && dp < 60.0f) {
                float alpha = dp > observedPitchSpeed ? 0.25f : 0.015f;
                observedPitchSpeed += alpha * (dp - observedPitchSpeed);
            }
        }

        observedLastYaw = yaw;
        observedLastPitch = pitch;
    }

    private static void applyDelta(float yawDelta, float pitchDelta, boolean clientLook, Rotation mode) {
        if (Math.abs(yawDelta) < 0.0001f && Math.abs(pitchDelta) < 0.0001f) return;
        if (clientLook) {
            RotationHandler.applyClient(yawDelta, pitchDelta);
        } else {
            RotationHandler.apply(yawDelta, pitchDelta);
        }
        if (mode == Rotation.DataSet || mode == Rotation.HvH) {
            SilentComponent.interpolateDataSetStep(Minecraft.getInstance().player);
        }
    }

    private static float smoothStep01(float value) {
        float clamped = Mth.clamp(value, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }
}
