package im.yaHustler.module.impl.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;


public class AuraTargetPointTracker {
    private static final int SIMULATION_STEPS = 8;
    private static final double GRAVITY_LIFT_NORMAL = -1.0;
    private static final double GRAVITY_LIFT_SLOW = 0.75;
    private static final double MIN_GRAVITY = 0.01;
    private static final double DIVE_COEF_BASE = -0.1;
    private static final double LIFT_COEF_BASE = 0.04;
    private static final double LIFT_UP_FACTOR = 3.2;
    private static final double HORIZONTAL_DRAG = 0.1;
    private static final double DRAG_X = 0.99;
    private static final double DRAG_Y = 0.98;
    private static final double DRAG_Z = 0.99;

    private Vec3 predictedPoint = Vec3.ZERO;

    public Vec3 getPredictedPoint() {
        return predictedPoint;
    }

    public void reset() {
        predictedPoint = Vec3.ZERO;
    }

    public Vec3 predictByRotation(LivingEntity target, float leadDistance, float aimDistance) {
        if (target == null) {
            predictedPoint = Vec3.ZERO;
            return Vec3.ZERO;
        }
        Vec3 origin = target.position().add(0.0, 0.5, 0.0);
        Vec3 look = target.getLookAngle().normalize();
        this.predictedPoint = origin.add(look.scale(leadDistance));
        return origin.add(look.scale(aimDistance));
    }

    public Vec3 predictByVelocity(LivingEntity target, float leadDistance, float aimDistance) {
        if (target == null) {
            predictedPoint = Vec3.ZERO;
            return Vec3.ZERO;
        }
        Vec3 origin = target.position().add(0.0, 0.5, 0.0);
        Vec3 displacement = simulateGlidingFlight(target);
        if (displacement.lengthSqr() < 1.0e-4) {
            Vec3 look = target.getLookAngle().normalize();
            this.predictedPoint = origin.add(look.scale(leadDistance));
            return origin.add(look.scale(aimDistance));
        } else {
            double length = displacement.length();
            Vec3 direction = displacement.scale(1.0 / length);
            float factor = (float) (length / 8.0);
            this.predictedPoint = origin.add(direction.scale(leadDistance * factor));
            return origin.add(direction.scale(aimDistance * factor));
        }
    }

    private Vec3 simulateGlidingFlight(LivingEntity target) {
        SimulationParams params = computeParams(target);
        Vec3 velocity = target.getDeltaMovement();
        Vec3 totalDisplacement = Vec3.ZERO;

        for (int i = 0; i < SIMULATION_STEPS; i++) {
            velocity = stepSimulation(velocity, params);
            totalDisplacement = totalDisplacement.add(velocity);
        }

        return totalDisplacement;
    }

    private SimulationParams computeParams(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        float pitchRad = target.getXRot() * (float) (Math.PI / 180.0);
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        double cosPitchSq = Mth.square(Math.cos(pitchRad));
        boolean hasHoriz = horiz > 0.0;
        double effectiveGravity = 0.08;
        boolean slowFalling = target.hasEffect(MobEffects.SLOW_FALLING);
        double gravityFactor = GRAVITY_LIFT_NORMAL + cosPitchSq * GRAVITY_LIFT_SLOW;
        return new SimulationParams(
                hasHoriz ? look.x / horiz : 0.0,
                hasHoriz ? look.z / horiz : 0.0,
                hasHoriz,
                effectiveGravity * gravityFactor,
                Math.min(effectiveGravity, MIN_GRAVITY) * gravityFactor,
                slowFalling,
                DIVE_COEF_BASE * cosPitchSq,
                pitchRad < 0.0f && hasHoriz,
                -Mth.sin(pitchRad) * LIFT_COEF_BASE
        );
    }

    private Vec3 stepSimulation(Vec3 velocity, SimulationParams params) {
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        double horizSpeed = Math.sqrt(vx * vx + vz * vz);

        vy += params.slowFalling() && vy <= 0.0 ? params.gravLiftSlow() : params.gravLiftNormal();

        if (params.hasHoriz() && vy < 0.0) {
            double dive = vy * params.diveCoef();
            vx += params.lookX() * dive;
            vy += dive;
            vz += params.lookZ() * dive;
        }

        if (params.liftUp()) {
            double lift = horizSpeed * params.liftCoef();
            vx -= params.lookX() * lift;
            vy += lift * LIFT_UP_FACTOR;
            vz -= params.lookZ() * lift;
        }

        if (params.hasHoriz()) {
            vx += (params.lookX() * horizSpeed - vx) * HORIZONTAL_DRAG;
            vz += (params.lookZ() * horizSpeed - vz) * HORIZONTAL_DRAG;
        }

        return new Vec3(vx * DRAG_X, vy * DRAG_Y, vz * DRAG_Z);
    }

    private record SimulationParams(
            double lookX,
            double lookZ,
            boolean hasHoriz,
            double gravLiftNormal,
            double gravLiftSlow,
            boolean slowFalling,
            double diveCoef,
            boolean liftUp,
            double liftCoef
    ) {}
}
