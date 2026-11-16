package morfeov.coliseum.module.impl.combat.aura.rotation.neuro;

import morfeov.coliseum.module.impl.combat.rotation.RotationComponent;
import net.minecraft.util.Mth;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Arrays;

public final class ColiseumSolver {
    private float[] state;
    private float sinceAttack = 20.0f;
    private float frozenTicks;
    private float lastTargetYaw;
    private float lastTargetPitch;
    private float errorYaw;
    private float errorPitch;
    private float previousErrorYaw;
    private float previousErrorPitch;
    private float lastDeltaYaw;
    private float lastDeltaPitch;
    private float previousDeltaYaw;
    private float previousDeltaPitch;
    private float sizeYaw = 5.0f;
    private float sizePitch = 15.0f;
    private double distance = 3.0;
    private boolean onTarget;

    public boolean initialized() {
        return this.state != null;
    }

    public void initialize(ColiseumModel model, float yaw, float pitch, float targetYaw, float targetPitch) {
        this.state = model.newState();
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        this.frozenTicks = 0.0f;
        this.previousDeltaPitch = 0.0f;
        this.previousDeltaYaw = 0.0f;
        this.lastDeltaPitch = 0.0f;
        this.lastDeltaYaw = 0.0f;
        this.errorYaw = this.previousErrorYaw = Mth.wrapDegrees(targetYaw - yaw);
        this.errorPitch = this.previousErrorPitch = targetPitch - pitch;
    }

    public void reset() {
        this.state = null;
        this.frozenTicks = 0.0f;
    }

    public void onAttack() {
        this.sinceAttack = 0.0f;
    }

    public void tick() {
        this.sinceAttack = Math.min(this.sinceAttack + 1.0f, 20.0f);
    }

    public boolean onTarget() {
        return this.onTarget;
    }

    public boolean step(ColiseumModel model, float yaw, float pitch, float targetYaw, float targetPitch,
                        float boundYaw, float boundPitch, double dist, float quantileErr, int freezeCut,
                        float temp, int candidates, float multiplier, boolean guideToAim, float[] out) {
        if (model == null) return false;
        if (this.state == null) {
            this.initialize(model, yaw, pitch, targetYaw, targetPitch);
        }
        this.sizeYaw = boundYaw;
        this.sizePitch = boundPitch;
        this.distance = dist;
        float gcd = Math.max(0.0001f, RotationComponent.getGCD());
        float dy = Mth.wrapDegrees(targetYaw - this.lastTargetYaw);
        float dp = targetPitch - this.lastTargetPitch;
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        float curErrYaw = Mth.wrapDegrees(targetYaw - yaw);
        float curErrPitch = targetPitch - pitch;
        // Models with a learned point are trained on independent ticks with explicit history.
        if (model.hasAim()) Arrays.fill(this.state, 0.0f);
        float[] output = model.forward(this.features(model, dy, dp), this.state);
        float bestStepYaw = 0.0f;
        float bestStepPitch = 0.0f;
        boolean forceUnfreeze = this.frozenTicks >= (float) Math.min(freezeCut, model.freezeCut());
        if (guideToAim) {
            int idx = 1 + 6 * model.sampleMixture(output, 0.5f);
            bestStepYaw = towardAim(curErrYaw, multiplier * model.sampleDelta(output[idx + 1], output[idx + 3], true, 0, temp), gcd);
            bestStepPitch = towardAim(curErrPitch, multiplier * model.sampleDelta(output[idx + 2], output[idx + 4], false, 0, temp), gcd);
        } else if (forceUnfreeze || ThreadLocalRandom.current().nextFloat() >= ColiseumModel.freezeProbability(output[0])) {
            float minLoss = Float.MAX_VALUE;
            for (int i = candidates; i > 0; --i) {
                int idx = 1 + 6 * model.sampleMixture(output, ThreadLocalRandom.current().nextFloat());
                float corr = ColiseumModel.correlation(output[idx + 5]);
                float uncorr = (float) Math.sqrt(Math.max(0.0f, 1.0f - corr * corr));
                float g1 = gaussian();
                float g2 = corr * g1 + uncorr * gaussian();
                float candYaw = quantize(multiplier * model.sampleDelta(output[idx + 1], output[idx + 3], true, g1, temp), gcd);
                float candPitch = quantize(multiplier * model.sampleDelta(output[idx + 2], output[idx + 4], false, g2, temp), gcd);
                float loss = Math.abs((float) Math.hypot(Mth.wrapDegrees(curErrYaw - candYaw) / this.sizeYaw, (curErrPitch - candPitch) / this.sizePitch) - quantileErr);
                if (loss < minLoss) {
                    minLoss = loss;
                    bestStepYaw = candYaw;
                    bestStepPitch = candPitch;
                }
            }
        }
        if (!guideToAim && forceUnfreeze && bestStepYaw == 0.0f && bestStepPitch == 0.0f) {
            if (Math.abs(curErrYaw) >= Math.abs(curErrPitch)) {
                bestStepYaw = Math.copySign(gcd, curErrYaw);
            } else {
                bestStepPitch = Math.copySign(gcd, curErrPitch);
            }
        }
        this.frozenTicks = bestStepYaw == 0.0f && bestStepPitch == 0.0f
                ? Math.min(this.frozenTicks + 1.0f, model.freezeCut()) : 0.0f;
        float clampedPitch = Mth.clamp(pitch + bestStepPitch, -90.0f, 90.0f);
        bestStepPitch = clampedPitch - pitch;
        this.previousDeltaYaw = this.lastDeltaYaw;
        this.previousDeltaPitch = this.lastDeltaPitch;
        this.lastDeltaYaw = bestStepYaw;
        this.lastDeltaPitch = bestStepPitch;
        this.previousErrorYaw = this.errorYaw;
        this.previousErrorPitch = this.errorPitch;
        this.errorYaw = Mth.wrapDegrees(targetYaw - (yaw + bestStepYaw));
        this.errorPitch = targetPitch - clampedPitch;
        this.onTarget = Math.abs(this.errorYaw) <= this.sizeYaw && Math.abs(this.errorPitch) <= this.sizePitch;
        out[0] = bestStepYaw;
        out[1] = bestStepPitch;
        return true;
    }

    private static float towardAim(float error, float predicted, float gcd) {
        float remaining = Math.abs(error);
        float minimumStep = Math.min(12.0f, Math.max(gcd, remaining * 0.25f));
        float step = Math.max(minimumStep, Math.copySign(1.0f, error) * predicted);
        float maxStep = (float) Math.floor(remaining / gcd) * gcd;
        return Math.copySign(Math.min(maxStep, quantize(step, gcd)), error);
    }

    private float[] features(ColiseumModel model, float dy, float dp) {
        return new float[]{
                normalize(this.errorYaw),
                normalize(this.errorPitch),
                normalize(Mth.wrapDegrees(this.errorYaw - this.previousErrorYaw)),
                normalize(this.errorPitch - this.previousErrorPitch),
                normalize(dy),
                normalize(dp),
                normalize(this.lastDeltaYaw),
                normalize(this.lastDeltaPitch),
                normalize(this.previousDeltaYaw),
                normalize(this.previousDeltaPitch),
                normalize(this.errorYaw / this.sizeYaw),
                normalize(this.errorPitch / this.sizePitch),
                (float) Math.log(Math.max(this.distance, 0.05) + 0.5) / 2.0f,
                (float) Math.log(this.sizeYaw) / 3.0f,
                this.onTarget ? 1.0f : 0.0f,
                this.sinceAttack / 20.0f,
                this.frozenTicks / (float) model.freezeCut()
        };
    }

    private static float normalize(float f) {
        return (float) (Math.log((double) f + Math.sqrt((double) (f * f) + 1.0)) / 3.0);
    }

    private static float gaussian() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }

    private static float quantize(float val, float step) {
        return (float) Math.round(val / step) * step;
    }
}
