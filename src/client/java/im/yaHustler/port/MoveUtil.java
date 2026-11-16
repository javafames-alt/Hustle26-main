package im.yaHustler.port;

import im.yaHustler.port.event.EventInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class MoveUtil implements IMinecraft {
    private MoveUtil() {}

    public static boolean isMoving() {
        if (mc.player == null) return false;
        var k = mc.player.input.keyPresses;
        return k.forward() || k.backward() || k.left() || k.right();
    }

    public static double direction(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0) rotationYaw += 180.0f;
        float forward = 1.0f;
        if (moveForward < 0.0) forward = -0.5f;
        else if (moveForward > 0.0) forward = 0.5f;
        if (moveStrafing > 0.0) rotationYaw -= 90.0f * forward;
        if (moveStrafing < 0.0) rotationYaw += 90.0f * forward;
        return Math.toRadians(rotationYaw);
    }

    public static float getdir() {
        if (mc.player == null || !isMoving()) return -1.0f;
        var k = mc.player.input.keyPresses;
        float forward = (k.forward() ? 1.0f : 0.0f) - (k.backward() ? 1.0f : 0.0f);
        float strafe = (k.left() ? 1.0f : 0.0f) - (k.right() ? 1.0f : 0.0f);
        return (float) Math.toDegrees(direction(mc.player.getYRot(), forward, strafe));
    }

    public static double[] forward(double distance) {
        if (mc.player == null) return new double[]{0.0, 0.0};
        var k = mc.player.input.keyPresses;
        float f = (k.forward() ? 1.0f : 0.0f) - (k.backward() ? 1.0f : 0.0f);
        float f2 = (k.left() ? 1.0f : 0.0f) - (k.right() ? 1.0f : 0.0f);
        float yaw = mc.player.getYRot();
        if (f != 0.0f) {
            if (f2 > 0.0f) yaw += f > 0.0f ? -45.0f : 45.0f;
            else if (f2 < 0.0f) yaw += f > 0.0f ? 45.0f : -45.0f;
            f2 = 0.0f;
            f = f > 0.0f ? 1.0f : -1.0f;
        }
        double d2 = Math.sin(Math.toRadians(yaw + 90.0f));
        double d3 = Math.cos(Math.toRadians(yaw + 90.0f));
        return new double[]{f * distance * d3 + f2 * distance * d2, f * distance * d2 - f2 * distance * d3};
    }

    public static void setMotion(double speed) {
        if (mc.player == null) return;
        var k = mc.player.input.keyPresses;
        float forward = (k.forward() ? 1.0f : 0.0f) - (k.backward() ? 1.0f : 0.0f);
        float strafe = (k.left() ? 1.0f : 0.0f) - (k.right() ? 1.0f : 0.0f);
        float yaw = mc.player.getYRot();
        if (forward == 0.0f && strafe == 0.0f) {
            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
            return;
        }
        if (forward != 0.0f) {
            if (strafe > 0.0f) yaw += forward > 0.0f ? -45.0f : 45.0f;
            else if (strafe < 0.0f) yaw += forward > 0.0f ? 45.0f : -45.0f;
            strafe = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }
        double rad = Math.toRadians(yaw + 90.0f);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        mc.player.setDeltaMovement(forward * speed * cos + strafe * speed * sin, mc.player.getDeltaMovement().y, forward * speed * sin - strafe * speed * cos);
    }

    public static void fixMovement(EventInput event, float serverYaw, float cameraYaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();
        if (forward == 0.0f && strafe == 0.0f) return;

        double angle = Mth.wrapDegrees(Math.toDegrees(direction(cameraYaw, forward, strafe)));
        float closestForward = 0.0f;
        float closestStrafe = 0.0f;
        float closestDifference = Float.MAX_VALUE;

        for (float pf = -1.0f; pf <= 1.0f; pf += 1.0f) {
            for (float ps = -1.0f; ps <= 1.0f; ps += 1.0f) {
                if (pf == 0.0f && ps == 0.0f) continue;
                double pred = Mth.wrapDegrees(Math.toDegrees(direction(serverYaw, pf, ps)));
                float diff = (float) Math.abs(Mth.wrapDegrees(angle - pred));
                if (diff < closestDifference) {
                    closestDifference = diff;
                    closestForward = pf;
                    closestStrafe = ps;
                }
            }
        }
        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }

    public static void fixMovementFree(EventInput event, float rotationYaw) {
        if (mc.player == null) return;
        float z = event.getForward();
        float x = event.getStrafe();
        if (z == 0.0f && x == 0.0f) return;

        float deltaYaw = mc.player.getYRot() - rotationYaw;
        float rad = deltaYaw * 0.017453292f;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        event.setStrafe(Math.round(x * cos - z * sin));
        event.setForward(Math.round(z * cos + x * sin));
    }

    public static void fixMovementTargeted(EventInput event, LivingEntity target, float yaw) {
        if (mc.player == null || target == null) return;
        if (mc.player.isFallFlying()) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
            return;
        }
        float camYaw = Mth.wrapDegrees(yaw);
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double targetAngle = Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float bestStrafe = 0.0f;
        float smallestDiff = Float.MAX_VALUE;
        for (float s = -1.0f; s <= 1.0f; s += 0.5f) {
            double pred = Mth.wrapDegrees(Math.toDegrees(direction(camYaw, 1.0f, s)));
            float diff = (float) Math.abs(Mth.wrapDegrees(targetAngle - pred));
            if (diff < smallestDiff) {
                smallestDiff = diff;
                bestStrafe = Math.round(s);
            }
        }
        event.setForward(1.0f);
        event.setStrafe(bestStrafe);
    }
}
