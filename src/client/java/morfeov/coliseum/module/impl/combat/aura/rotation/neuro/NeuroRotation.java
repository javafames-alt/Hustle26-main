package morfeov.coliseum.module.impl.combat.aura.rotation.neuro;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class NeuroRotation {
    private static final NeuroRotation instance = new NeuroRotation();
    private final ColiseumSolver solver = new ColiseumSolver();
    private final ColiseumSolver returnSolver = new ColiseumSolver();
    private final ColiseumRecorder recorder = new ColiseumRecorder();
    private int targetId = -1;
    private int returnTicks;
    private long motionStart, lastAimTime;
    private boolean attacked;

    private NeuroRotation() {}

    public static NeuroRotation getInstance() {
        return instance;
    }

    public ColiseumRecorder recorder() {
        return recorder;
    }

    public boolean canUse() {
        return !recorder.isRecording() && ColiseumModel.active() != null;
    }

    public void reset() {
        solver.reset();
        returnSolver.reset();
        targetId = -1;
        returnTicks = 0;
        motionStart = lastAimTime = 0;
        attacked = false;
    }

    public void targetLost() {
        solver.reset();
        targetId = -1;
        motionStart = lastAimTime = 0;
        attacked = false;
    }

    public void onAttack() {
        attacked = true;
    }

    public float[] step(LivingEntity target, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        ColiseumModel model = ColiseumModel.active();
        if (mc.player == null || target == null || model == null || recorder.isRecording()) {
            reset();
            return new float[2];
        }
        AABB box = target.getBoundingBox();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 delta = aimOffset(box, eye, model);
        double flat = Math.max(Math.hypot(delta.x, delta.z), 0.05);
        float ty = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float tp = (float) -Math.toDegrees(Math.atan2(delta.y, flat));
        float sy = Math.max((float) Math.toDegrees(Math.atan2(box.getXsize() / 2.0, flat)), 0.5f);
        float sp = Math.max((float) Math.toDegrees(Math.atan2(box.getYsize() / 2.0, flat)), 0.5f);
        if (targetId != target.getId() || !solver.initialized()) {
            solver.initialize(model, yaw, pitch, ty, tp);
            targetId = target.getId();
        }
        long now = System.nanoTime();
        if (motionStart == 0 || now - lastAimTime > 250_000_000L) motionStart = now;
        lastAimTime = now;
        returnTicks = 0;
        float[] out = new float[2];
        float multiplier = mc.player.isUnderWater() && mc.getCurrentServer() != null
                && mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).contains("holyworld") ? 0.8f : 1.1f;
        solver.step(model, yaw, pitch, ty, tp, sy, sp, distanceToBox(eye, box),
                0.45f * model.sampleError(ThreadLocalRandom.current().nextFloat()), 12, 0.7f, 4, multiplier, model.hasAim(), out);
        if (attacked) {
            solver.onAttack();
            attacked = false;
        } else {
            solver.tick();
        }
        return out;
    }

    public Vec3 aimPoint(LivingEntity target) {
        var player = Minecraft.getInstance().player;
        AABB box = target.getBoundingBox();
        if (player == null) return box.getCenter();
        Vec3 eye = player.getEyePosition();
        return eye.add(aimOffset(box, eye, ColiseumModel.active()));
    }

    MotionTrace.Point cameraMotion() {
        long now = System.nanoTime();
        if (motionStart == 0 || now - lastAimTime > 250_000_000L
                || morfeov.coliseum.rotation.Look.active() || Minecraft.getInstance().gui.screen() != null) {
            return MotionTrace.ZERO;
        }
        ColiseumModel model = ColiseumModel.active();
        return model == null ? MotionTrace.ZERO : model.motionAt(now - motionStart);
    }

    private static Vec3 aimOffset(AABB box, Vec3 eye, ColiseumModel model) {
        Vec3 delta = box.getCenter().subtract(eye);
        if (model != null && model.hasAim()) {
            double flatToCenter = Math.hypot(delta.x, delta.z);
            double rightX = flatToCenter > 1e-8 ? delta.z / flatToCenter : 1.0;
            double rightZ = flatToCenter > 1e-8 ? -delta.x / flatToCenter : 0.0;
            double halfWidth = Math.min(box.getXsize() / Math.max(Math.abs(rightX), 1e-8),
                    box.getZsize() / Math.max(Math.abs(rightZ), 1e-8)) * 0.5;
            double side = model.aimSide() * halfWidth;
            delta = delta.add(side * rightX, model.aimHeight() * box.getYsize() * 0.5, side * rightZ);
        }
        return delta;
    }

    public float[] returnRotation(float yaw, float pitch, float targetYaw, float targetPitch) {
        ColiseumModel model = ColiseumModel.active();
        if (model == null || ++returnTicks > 30 || (Math.abs(Mth.wrapDegrees(targetYaw - yaw)) <= 3.0f && Math.abs(targetPitch - pitch) <= 3.0f)) {
            returnSolver.reset();
            return null;
        }
        float[] out = new float[2];
        returnSolver.step(model, yaw, pitch, targetYaw, targetPitch, 5.0f, 15.0f, 3.0, 0.0f, 12, 0.7f, 4, 1.1f, false, out);
        returnSolver.tick();
        return new float[]{yaw + out[0], Mth.clamp(pitch + out[1], -90.0f, 90.0f)};
    }

    public static double distanceToBox(Vec3 eye, AABB box) {
        double x = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
        double y = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
        double z = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
        return Math.sqrt(x * x + y * y + z * z);
    }
}
