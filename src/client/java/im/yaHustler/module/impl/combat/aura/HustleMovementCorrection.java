package im.yaHustler.module.impl.combat.aura;

public final class HustleMovementCorrection {
    private HustleMovementCorrection() {}

    public static float[] correct(float rotationYaw, float referenceYaw, float forward, float strafe, boolean gliding) {
        if (forward == 0 && strafe == 0) return new float[2];
        double wanted = direction(gliding ? rotationYaw : referenceYaw, forward, strafe);
        float bestForward = 0, bestStrafe = 0;
        double bestError = Double.POSITIVE_INFINITY;
        for (int f = -1; f <= 1; f++) {
            for (int s = -1; s <= 1; s++) {
                if (f == 0 && s == 0) continue;
                double error = Math.abs(wrap(wanted - direction(rotationYaw, f, s)));
                if (error < bestError) {
                    bestError = error;
                    bestForward = f;
                    bestStrafe = s;
                }
            }
        }
        return new float[]{bestForward, bestStrafe};
    }

    private static double direction(float yaw, float forward, float strafe) {
        if (forward < 0) yaw += 180;
        float factor = forward < 0 ? -0.5f : forward > 0 ? 0.5f : 1.0f;
        if (strafe > 0) yaw -= 90 * factor;
        if (strafe < 0) yaw += 90 * factor;
        return wrap(yaw);
    }

    private static double wrap(double angle) {
        angle %= 360;
        return angle >= 180 ? angle - 360 : angle < -180 ? angle + 360 : angle;
    }
}
