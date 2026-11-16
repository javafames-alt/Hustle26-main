package im.yaHustler.module.impl.movement;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Readable form of Hustle 26.3 GrimGlide's replacement for vanilla elytra velocity. */
public final class GrimGlidePhysics {
    private GrimGlidePhysics() {}

    public static Vec3 velocity(Vec3 movement, Vec3 look, float pitchDegrees, float yawDegrees,
                                double gravity, boolean slowFalling, boolean recovering) {
        float pitch = pitchDegrees * (float) (Math.PI / 180.0);
        double lookHorizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizontalSpeed = movement.horizontalDistance();
        double effectiveGravity = movement.y <= 0.0 && slowFalling ? Math.min(gravity, 0.01) : gravity;
        double pitchLift = Mth.square(Math.cos(pitch));
        Vec3 velocity = movement.add(0.0, effectiveGravity * (-1.0 + pitchLift * 0.75), 0.0);

        if (velocity.y < 0.0 && lookHorizontalLength > 0.0) {
            double descentLift = velocity.y * -0.1 * pitchLift;
            velocity = velocity.add(look.x * descentLift / lookHorizontalLength, descentLift,
                look.z * descentLift / lookHorizontalLength);
        }
        if (pitch < 0.0f && lookHorizontalLength > 0.0) {
            double upwardLift = horizontalSpeed * -Mth.sin(pitch) * (double) 0.04f;
            velocity = velocity.add(-look.x * upwardLift / lookHorizontalLength, upwardLift * 3.2,
                -look.z * upwardLift / lookHorizontalLength);
        }
        if (lookHorizontalLength > 0.0) {
            velocity = velocity.add((look.x / lookHorizontalLength * horizontalSpeed - velocity.x) * 0.1,
                0.0, (look.z / lookHorizontalLength * horizontalSpeed - velocity.z) * 0.1);
        }

        if (!recovering) return velocity.scale((double) 0.3f);
        double yaw = Math.toRadians(yawDegrees);
        double forwardBoost = (double) 0.09f;
        return velocity.multiply((double) 0.99f, (double) 0.98f, (double) 0.99f)
            .add(-Math.sin(yaw) * forwardBoost, (double) 0.03f, Math.cos(yaw) * forwardBoost);
    }
}
