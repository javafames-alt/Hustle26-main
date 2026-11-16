package morfeov.coliseum.module.impl.combat.aura.rotation;

import java.util.Random;
import net.minecraft.util.Mth;

public final class SpookyMotion {
    private final Random random;
    private long started, tuningAt, tuningInterval, heldUntil, alignedAt;
    private boolean initialized, aligned, wasAttackReady;
    private float pulse, heldYaw, heldPitch, yawVelocity, pitchVelocity;
    private float speed, errorScale, acceleration, coupling, deadzone, alignedBlend;
    private float height, offsetX, offsetZ, targetHeight, targetOffsetX, targetOffsetZ;

    public SpookyMotion(Random random) { this.random = random; }
    public float height() { if (height == 0) height = targetHeight = range(.26f, .69f); return height; }
    public float offsetX() { return offsetX; }
    public float offsetZ() { return offsetZ; }

    public float[] step(long now, float yaw, float pitch, float targetYaw, float targetPitch,
                        boolean attackReady, boolean mace, boolean rayHits, boolean intersecting,
                        int swingTicks, float gcd) {
        if (!initialized) { initialized = true; started = now; tune(now); }
        if (now - tuningAt > tuningInterval) tune(now);
        boolean snap = false;
        height();
        if (attackReady && !wasAttackReady) {
            pulse = range(1.9f, 2.3f);
            heldUntil = now + (long)range(200, 250);
            targetHeight = range(.37f, .67f);
            targetOffsetX = range(-.12f, .12f); targetOffsetZ = range(-.12f, .12f);
        }
        wasAttackReady = attackReady;
        height = Mth.lerp(.22f, height, targetHeight);
        offsetX = Mth.lerp(.22f, offsetX, targetOffsetX);
        offsetZ = Mth.lerp(.22f, offsetZ, targetOffsetZ);
        if (pulse > range(.43f, .75f)) { snap = true; pulse = range(.13f, .25f); }
        if (snap) { heldYaw = targetYaw; heldPitch = targetPitch; }
        else if (now < heldUntil) { targetYaw = heldYaw; targetPitch = heldPitch; }
        if (rayHits) { if (!aligned) { aligned = true; alignedAt = now; } }
        else aligned = false;
        float alignment = Mth.clamp(((aligned ? now - alignedAt : 0) - 250f) / 300f, 0, 1);
        alignedBlend = alignment >= alignedBlend ? alignment : alignedBlend + (alignment - alignedBlend) * 4;
        float dy = Mth.wrapDegrees(targetYaw - yaw), dp = targetPitch - pitch;
        float error = (float)Math.hypot(dy, dp);
        float turnSpeed = speed * Mth.clamp(error / errorScale, 0, 1)
            * Mth.lerp(Mth.clamp((now - started) / 220f, 0, 1), .78f, 1)
            * (1 - Mth.clamp(alignedBlend, 0, 1) * (mace ? .35f : 1))
            * (snap ? 1.25f : 1) * (mace ? 3.2f : 1)
            * (swingTicks > range(.2f, .4f) && !mace ? .5f : 1);
        float accel = acceleration * (snap ? 2.8f : .85f) * (mace ? 2.6f : 1);
        yawVelocity += Mth.clamp(turnSpeed * (error < .001f ? 0 : dy / error) * 1.25f / (intersecting ? 2 : 1)
                - yawVelocity, -accel * (mace ? .6f : 1.2f), accel * (mace ? .6f : 1.2f));
        pitchVelocity += Mth.clamp(turnSpeed * (error < .001f ? 0 : dp / error) + yawVelocity * coupling
                - pitchVelocity, -accel * .68f, accel * .68f);
        yawVelocity = Mth.clamp(yawVelocity, -Math.abs(dy), Math.abs(dy));
        pitchVelocity = Mth.clamp(pitchVelocity, -Math.abs(dp) - Math.abs(yawVelocity * coupling),
                Math.abs(dp) + Math.abs(yawVelocity * coupling));
        if (error < deadzone) yawVelocity = pitchVelocity = 0;
        float yawCap = Math.min(Math.abs(yawVelocity), Math.max(gcd, Math.abs(dy) / Math.max(1, divisor(mace ? .6f : 1, mace ? 1 : 1.5f)))) * (mace ? 3.4f : 1);
        float pitchCap = Math.min(Math.abs(pitchVelocity), Math.max(gcd, Math.abs(dp) / Math.max(1, divisor(mace ? 1 : 2.5f, mace ? 1.8f : 4)))) * (mace ? 3.4f : 1);
        float yawStep = quantize(Mth.clamp(dy, -yawCap, yawCap), gcd);
        float pitchStep = quantize(Mth.clamp(dp, -pitchCap, pitchCap), gcd);
        return new float[]{yawStep, Mth.clamp(pitch + pitchStep, -90, 90) - pitch};
    }

    private void tune(long now) {
        tuningAt = now; tuningInterval = (long)range(25, 50);
        speed = range(142, 200); errorScale = range(34, 54); acceleration = range(7, 14);
        coupling = range(.04f, .12f) * (random.nextBoolean() ? 1 : -1); deadzone = range(.4f, .9f);
    }
    private float range(float min, float max) { return min + (max - min) * random.nextFloat(); }
    private float divisor(float min, float max) {
        return switch (random.nextInt(4)) {
            case 0 -> min + (max - min) * (random.nextFloat() + random.nextFloat()) / 2;
            case 1 -> {
                double a = random.nextDouble(), b = random.nextDouble(), noise = random.nextGaussian() * .02f;
                yield (float)(min + (max - min) * Math.pow(a, 1 + random.nextDouble() * .7)
                    * (b * .8 + .1) * (Math.log1p(a * 3) * .5 + .5) + noise);
            }
            default -> range(min, max);
        };
    }
    public static float quantize(float value, float gcd) {
        return Float.isFinite(gcd) && gcd > .000001f ? Math.round(value / gcd) * gcd : value;
    }
    public void reset() {
        initialized = aligned = wasAttackReady = false;
        started = tuningAt = tuningInterval = heldUntil = alignedAt = 0;
        pulse = heldYaw = heldPitch = yawVelocity = pitchVelocity = alignedBlend = 0;
        height = offsetX = offsetZ = targetHeight = targetOffsetX = targetOffsetZ = 0;
    }
}
