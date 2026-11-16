package morfeov.coliseum.module.impl.combat.rotation;

import morfeov.coliseum.module.impl.combat.aura.AimPart;
import morfeov.coliseum.module.impl.combat.aura.Rotation;
import morfeov.coliseum.module.impl.combat.aura.rotation.SpookyTimeRotation;
import morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RotationComponent {
    private static float rwVelYaw;
    private static float rwVelPitch;
    private static double rwJitterPhase;
    private static int rwTargetEntityId = Integer.MIN_VALUE;
    private static Vec3 rwCurrentAimOffset;
    private static Vec3 rwTargetAimOffset;
    private static long rwLastPointSwitchMs;
    private static final long RW_POINT_HOLD_MS = 280L;

    private RotationComponent() {}

    public static void reset() {
        resetReallyWorld();
        SpookyTimeRotation.getInstance().reset();
        NeuroRotation.getInstance().reset();
    }

    public static void cancelPullAway() { SpookyTimeRotation.getInstance().reset(); }

    public static void onAttack() {
        onReallyWorldAttack();
        NeuroRotation.getInstance().onAttack();
    }

    public static Vec3 getAimPoint(LivingEntity target, Rotation mode) {
        if (target == null) return Vec3.ZERO;
        return switch (mode) {
            case ReallyWorld -> getReallyWorldAimPoint(target);
            case SpookyTime -> SpookyTimeRotation.getInstance().aimPoint(target);
            case DataSet -> NeuroRotation.getInstance().aimPoint(target);
            case HvH -> target.getBoundingBox().getCenter();
        };
    }

    public static float[] step(Rotation mode, AimPart observation) {
        return switch (mode) {
            case ReallyWorld -> reallyWorld(observation, false);
            case SpookyTime -> SpookyTimeRotation.getInstance().step(observation, getGCD());
            case DataSet -> dataSet(observation);
            case HvH -> reallyWorld(observation, true);
        };
    }

    public static void resetReallyWorld() {
        rwVelYaw = 0.0f;
        rwVelPitch = 0.0f;
        rwTargetEntityId = Integer.MIN_VALUE;
        rwCurrentAimOffset = null;
        rwTargetAimOffset = null;
        rwLastPointSwitchMs = 0L;
        rwJitterPhase = 0.0;
    }

    public static void onReallyWorldAttack() {
        rwVelPitch += (float) (ThreadLocalRandom.current().nextDouble() * 0.15 - 0.05);
    }

    public static Vec3 getReallyWorldAimPoint(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null) return null;
        int targetId = target.getId();
        long now = System.currentTimeMillis();
        if (targetId != rwTargetEntityId) {
            rwTargetEntityId = targetId;
            rwCurrentAimOffset = null;
            rwTargetAimOffset = null;
            rwLastPointSwitchMs = 0L;
        }

        double height = target.getBbHeight();
        double width = target.getBbWidth();
        Vec3 eye = mc.player.getEyePosition();

        if (rwTargetAimOffset == null || now - rwLastPointSwitchMs > RW_POINT_HOLD_MS) {
            rwLastPointSwitchMs = now;
            List<Vec3> points = new ArrayList<>(5);
            points.add(new Vec3(0.0, height * 0.85, 0.0));
            points.add(new Vec3(0.0, height * 0.62, 0.0));
            points.add(new Vec3(0.0, height * 0.40, 0.0));
            double sideOffset = width * 0.22;
            points.add(new Vec3(sideOffset, height * 0.60, 0.0));
            points.add(new Vec3(-sideOffset, height * 0.60, 0.0));

            Vec3 best = null;
            double bestScore = Double.MAX_VALUE;
            Vec3 targetOrigin = target.position();
            for (Vec3 pt : points) {
                Vec3 worldPt = targetOrigin.add(pt);
                double d = worldPt.distanceToSqr(eye);
                double jitter = ThreadLocalRandom.current().nextDouble() * 0.15;
                double score = d + jitter;
                if (score < bestScore) {
                    bestScore = score;
                    best = pt;
                }
            }
            rwTargetAimOffset = best != null ? best : new Vec3(0.0, height * 0.62, 0.0);
            if (rwCurrentAimOffset == null) {
                rwCurrentAimOffset = rwTargetAimOffset;
            }
        }

        if (rwCurrentAimOffset != null && rwTargetAimOffset != null) {
            double rate = 0.18;
            rwCurrentAimOffset = new Vec3(
                    rwCurrentAimOffset.x + (rwTargetAimOffset.x - rwCurrentAimOffset.x) * rate,
                    rwCurrentAimOffset.y + (rwTargetAimOffset.y - rwCurrentAimOffset.y) * rate,
                    rwCurrentAimOffset.z + (rwTargetAimOffset.z - rwCurrentAimOffset.z) * rate
            );
        }

        Vec3 basePos = target.position();
        return basePos.add(rwCurrentAimOffset != null ? rwCurrentAimOffset : new Vec3(0.0, height * 0.62, 0.0));
    }

    private static float[] reallyWorld(AimPart observation, boolean fastApproach) {
        Minecraft mc = Minecraft.getInstance();
        if (observation.target == null || mc.player == null) {
            resetReallyWorld();
            return new float[2];
        }
        LivingEntity target = observation.target;
        LocalPlayer player = mc.player;

        if (player.isFallFlying()) {
            Vec3 aimPoint = getReallyWorldAimPoint(target);
            Vec3 diff = aimPoint.subtract(player.getEyePosition());
            double horizontalDist = Math.max(0.05, Math.hypot(diff.x, diff.z));
            float targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
            float targetPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(diff.y, horizontalDist)), -89.5f, 89.5f);
            float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
            float pitchDelta = targetPitch - player.getXRot();
            return new float[]{yawDelta, pitchDelta};
        }

        Vec3 aimPoint = observation.aimPoint != null && !observation.aimPoint.equals(Vec3.ZERO)
                ? observation.aimPoint : getReallyWorldAimPoint(target);

        Vec3 eye = player.getEyePosition();
        Vec3 diff = aimPoint.subtract(eye);
        Vec3 targetVel = target.getDeltaMovement();
        if (targetVel.lengthSqr() > 0.001) {
            diff = diff.add(targetVel.scale(0.35));
        }

        double horizontalDist = Math.max(0.05, Math.hypot(diff.x, diff.z));
        float idealYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float idealPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(diff.y, horizontalDist)), -89.5f, 89.5f);

        float errYaw = Mth.wrapDegrees(idealYaw - player.getYRot());
        float errPitch = idealPitch - player.getXRot();
        float errDist = (float) Math.hypot(errYaw, errPitch);

        AABB box = target.getBoundingBox().inflate(0.06);
        boolean rayHits = box.clip(eye, eye.add(player.getViewVector(1.0f).scale(4.5))).isPresent();

        float speedProfile;
        if (errDist > 25.0f) {
            speedProfile = 0.40f;
        } else if (errDist > 10.0f) {
            speedProfile = 0.34f;
        } else if (errDist > 3.0f) {
            speedProfile = 0.26f;
        } else {
            speedProfile = rayHits ? 0.16f : 0.22f;
        }

        float approachBoost = fastApproach && !rayHits && errDist > 10.0f ? 1.2f : 1.0f;
        speedProfile *= approachBoost;
        float friction = 0.48f;
        rwVelYaw = rwVelYaw * friction + errYaw * speedProfile;
        rwVelPitch = rwVelPitch * friction + errPitch * speedProfile;

        rwJitterPhase += 0.35;
        float jitterYaw = (float) (Math.sin(rwJitterPhase) * 0.08);
        float jitterPitch = (float) (Math.cos(rwJitterPhase * 1.3) * 0.05);

        float stepYaw = rwVelYaw + jitterYaw;
        float stepPitch = rwVelPitch + jitterPitch;

        float maxYawSpeed = (rayHits ? 24.0f : 36.0f) * approachBoost;
        float maxPitchSpeed = (rayHits ? 14.0f : 20.0f) * approachBoost;
        stepYaw = Mth.clamp(stepYaw, -maxYawSpeed, maxYawSpeed);
        stepPitch = Mth.clamp(stepPitch, -maxPitchSpeed, maxPitchSpeed);

        if (Math.abs(stepYaw) > Math.abs(errYaw) && Math.signum(stepYaw) == Math.signum(errYaw)) {
            stepYaw = errYaw;
        }
        if (Math.abs(stepPitch) > Math.abs(errPitch) && Math.signum(stepPitch) == Math.signum(errPitch)) {
            stepPitch = errPitch;
        }

        float gcd = getGCD();
        if (gcd > 0.0001f) {
            float qYaw = Math.round(stepYaw / gcd) * gcd;
            float qPitch = Math.round(stepPitch / gcd) * gcd;
            if (qYaw == 0.0f && Math.abs(errYaw) > gcd * 0.5f) {
                qYaw = Math.signum(errYaw) * gcd;
            }
            if (qPitch == 0.0f && Math.abs(errPitch) > gcd * 0.5f) {
                qPitch = Math.signum(errPitch) * gcd;
            }
            stepYaw = qYaw;
            stepPitch = qPitch;
        }

        return new float[]{stepYaw, stepPitch};
    }

    public static boolean canReallyWorldAttack(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null || !target.isAlive()) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        AABB box = target.getBoundingBox().inflate(0.06);
        Vec3 reach = eye.add(look.scale(4.5));
        return box.clip(eye, reach).isPresent();
    }

    private static float[] dataSet(AimPart observation) {
        Minecraft mc = Minecraft.getInstance();
        if (observation.target == null || mc.player == null) {
            return new float[2];
        }
        return NeuroRotation.getInstance().step(
                observation.target, mc.player.getYRot(), mc.player.getXRot());
    }

    public static float getGCD() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return 0.005f;
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double f1 = f * f * f * 8.0;
        return (float) (f1 * 0.15);
    }
}
