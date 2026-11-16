package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.module.impl.themes.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class SwordTargetEspRenderer {
    private static final int SWORD_COUNT = 7;
    private static final float BASE_SCALE = 0.12F;
    private static final float OPACITY_SCALE = 0.5F;
    private static final float ATTACK_SCALE = 0.17664F;
    private static final int ATTACK_COLOR = 0xffff0004;
    private static final long APPEAR_MS = 380L;
    private static final long DISAPPEAR_MS = 240L;
    private static final long ATTACK_MS = 300L;
    private static final long GEOMETRY_LERP_MS = 380L;
    private static final float PITCH = -90.0F;

    private final Matrix4f[] instanceMatrices = new Matrix4f[SWORD_COUNT];
    private final int[] instanceColors = new int[SWORD_COUNT];
    private final double[] orbitX = new double[SWORD_COUNT];
    private final double[] orbitZ = new double[SWORD_COUNT];
    private final float[] orbitYaw = new float[SWORD_COUNT];

    private final Vector3f scratch0 = new Vector3f();
    private final Vector3f scratch1 = new Vector3f();
    private final Vector3f scratch2 = new Vector3f();

    private final TargetTransition<LivingEntity> targetTransition = new TargetTransition<>();
    private final TimedPulse attackPulse = new TimedPulse();
    private final PreviousValueTransition<TargetGeometry> previousGeometry = new PreviousValueTransition<>();
    private TargetGeometry lastGeometry;

    SwordTargetEspRenderer() {
        for (int i = 0; i < SWORD_COUNT; i++) {
            instanceMatrices[i] = new Matrix4f();
        }
    }

    void reset() {
        targetTransition.reset();
        attackPulse.reset();
        previousGeometry.reset();
        lastGeometry = null;
    }

    void onAttack() {
        attackPulse.trigger();
    }

    void render(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector, LivingEntity currentTarget, float partialTick, boolean hitRed, float alphaFactor) {
        if (mc.player == null || mc.level == null) return;

        updateTarget(currentTarget, partialTick);
        if (targetTransition.isEmpty()) return;

        float fade = appearanceFade();
        LivingEntity entity = targetTransition.current();
        if (fade <= 0.001f || entity == null) return;

        TargetGeometry geometry = captureGeometry(entity, partialTick);
        if (geometry != null) {
            lastGeometry = geometry;
        } else {
            geometry = lastGeometry;
        }

        if (geometry == null) {
            reset();
            return;
        }

        geometry = interpolatePreviousGeometry(geometry);
        long now = System.currentTimeMillis();
        float scaleMul = appearanceScale();
        float breathe = 1.0f + Mth.sin((float) now * 0.004f) * 0.025f;
        double radius = Math.max(geometry.width(), geometry.depth()) * 0.5 + 0.2;
        double spin = ((float) now * 0.0021f) % (Mth.PI * 2.0);
        double orbitY = geometry.position().y + geometry.height() / 1.4 - 0.06;
        Vec3 camera = mc.gameRenderer.mainCamera().position();

        int closest = 0;
        double closestDist = Double.MAX_VALUE;

        for (int i = 0; i < SWORD_COUNT; i++) {
            double angle = spin + (Mth.PI * 2.0) * i / (double) SWORD_COUNT;
            double cos = Math.cos(angle);
            double sinA = Math.sin(angle);
            double r = radius + Mth.sin((float) now * 0.003f + i * 0.7f) * 0.025;
            double x = geometry.position().x + cos * r;
            double z = geometry.position().z + sinA * r;
            orbitX[i] = x;
            orbitZ[i] = z;
            orbitYaw[i] = (float) Math.toDegrees(-Math.atan2(cos, -sinA));
            double dist = camera.distanceToSqr(x, orbitY, z);
            if (dist < closestDist) {
                closestDist = dist;
                closest = i;
            }
        }

        boolean attacking = hitRed && attackPulse.isActive(now, ATTACK_MS);
        float attackProgress = attacking ? attackPulse.progress(now, ATTACK_MS) : 0.0f;
        int primary = Theme.accent();
        int secondary = scaleRgb(primary, 0.45f);

        for (int ix = 0; ix < SWORD_COUNT; ix++) {
            float stagger = staggerAppear(ix);
            float instanceFade = fade * stagger;
            float scale = BASE_SCALE * scaleMul * breathe;
            int color = pulseColor(primary, secondary, now, ix);
            float bob = Mth.sin((float) now * 0.005f + ix * 0.65f) * 0.032f;
            float roll = Mth.sin((float) now * 0.004f + ix) * 2.5f;

            if (attacking && ix == closest) {
                float half = attackProgress < 0.5f ? attackProgress / 0.5f : (attackProgress - 0.5f) / 0.5f;
                float eased = easeInOutSine(half);
                scale = attackProgress < 0.5f ? Mth.lerp(eased, scale, ATTACK_SCALE) : Mth.lerp(eased, ATTACK_SCALE, scale);
                color = attackProgress < 0.5f ? lerpColor(primary, ATTACK_COLOR, eased) : lerpColor(ATTACK_COLOR, primary, eased);
            }

            color = applyOpacity(color, instanceFade, OPACITY_SCALE * alphaFactor);
            instanceColors[ix] = color;

            fillMatrix(instanceMatrices[ix],
                orbitX[ix] - camera.x,
                orbitY + bob - camera.y,
                orbitZ[ix] - camera.z,
                scale, -scale, scale,
                orbitYaw[ix], PITCH, roll);
        }

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            for (int ix = 0; ix < SWORD_COUNT; ix++) {
                emitSword(pose, vc, instanceMatrices[ix], instanceColors[ix]);
            }
        });
    }

    private void emitSword(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc, Matrix4f model, int color) {
        float r = (color >> 16 & 0xff) / 255.0f;
        float g = (color >> 8 & 0xff) / 255.0f;
        float b = (color & 0xff) / 255.0f;
        float a = (color >>> 24) / 255.0f;
        if (a <= 0.001f) return;

        float[] positions = SwordTargetEspMesh.POSITIONS;
        int[] indices = SwordTargetEspMesh.INDICES;

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i] * 3;
            int i1 = indices[i + 1] * 3;
            int i2 = indices[i + 2] * 3;
            if (i2 + 2 < positions.length) {
                model.transformPosition(positions[i0], positions[i0 + 1], positions[i0 + 2], scratch0);
                model.transformPosition(positions[i1], positions[i1 + 1], positions[i1 + 2], scratch1);
                model.transformPosition(positions[i2], positions[i2 + 1], positions[i2 + 2], scratch2);

                vc.addVertex(pose, scratch0.x, scratch0.y, scratch0.z).setColor(r, g, b, a);
                vc.addVertex(pose, scratch1.x, scratch1.y, scratch1.z).setColor(r, g, b, a);
                vc.addVertex(pose, scratch2.x, scratch2.y, scratch2.z).setColor(r, g, b, a);
                vc.addVertex(pose, scratch2.x, scratch2.y, scratch2.z).setColor(r, g, b, a);
            }
        }
    }

    private static void fillMatrix(Matrix4f matrix, double x, double y, double z,
                                   float scaleX, float scaleY, float scaleZ,
                                   float yaw, float pitch, float roll) {
        matrix.identity()
            .translate((float) x, (float) y, (float) z)
            .rotateY((float) Math.toRadians(yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll))
            .scale(scaleX, scaleY, scaleZ);
    }

    private static int pulseColor(int primary, int secondary, long now, int index) {
        float mix = 0.5F + 0.5F * Mth.sin((float) now * 0.0042F + index * 0.9F);
        return lerpColor(primary, secondary, mix * 0.48F);
    }

    private float appearanceFade() {
        if (targetTransition.phase() == TransitionPhase.IDLE) {
            return 1.0F;
        } else if (targetTransition.phase() == TransitionPhase.APPEARING) {
            float progress = targetTransition.progress(APPEAR_MS);
            if (progress >= 1.0F) {
                targetTransition.finishAppearance();
            }
            return easeInOutSine(progress);
        } else {
            float progress = targetTransition.progress(DISAPPEAR_MS);
            if (progress >= 1.0F) {
                reset();
                return 0.0F;
            } else {
                return 1.0F - smoothStep(progress);
            }
        }
    }

    private TargetGeometry captureGeometry(LivingEntity entity, float tickDelta) {
        if (entity != null && entity.isAlive()) {
            AABB box = entity.getBoundingBox();
            Minecraft mc = Minecraft.getInstance();
            double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
            double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
            return new TargetGeometry(new Vec3(x, y, z), entity.getBbHeight(), box.getXsize(), box.getZsize());
        }
        return null;
    }

    private float appearanceScale() {
        if (targetTransition.phase() == TransitionPhase.IDLE) {
            return 1.0F;
        } else if (targetTransition.phase() == TransitionPhase.APPEARING) {
            return easeOut(targetTransition.progress(APPEAR_MS), 1.28F);
        } else {
            return Mth.lerp(smoothStep(targetTransition.progress(DISAPPEAR_MS)), 1.0F, 0.78F);
        }
    }

    private void updateTarget(LivingEntity target, float tickDelta) {
        TargetChange<LivingEntity> change = targetTransition.updateTarget(target);
        if (change.changed()) {
            TargetGeometry oldGeometry = lastGeometry;
            TargetGeometry newGeometry = captureGeometry(targetTransition.current(), tickDelta);
            if (oldGeometry != null && newGeometry != null) {
                previousGeometry.remember(oldGeometry);
            }
        }
    }

    private float staggerAppear(int index) {
        if (targetTransition.phase() != TransitionPhase.APPEARING) {
            return 1.0F;
        } else {
            float offset = index * 0.035F;
            float progress = targetTransition.progress(APPEAR_MS);
            float local = Math.max(0.0F, Math.min(1.0F, (progress - offset) / Math.max(0.001F, 1.0F - offset)));
            return easeInOutSine(local);
        }
    }

    private TargetGeometry interpolatePreviousGeometry(TargetGeometry geometry) {
        if (!previousGeometry.hasValue()) {
            return geometry;
        } else {
            float progress = previousGeometry.progress(GEOMETRY_LERP_MS);
            if (progress >= 1.0F) {
                previousGeometry.clear();
                return geometry;
            } else {
                float eased = easeOut(progress, 1.12F);
                TargetGeometry previous = previousGeometry.get();
                Vec3 start = previous.position();
                Vec3 end = geometry.position();
                return new TargetGeometry(
                    new Vec3(Mth.lerp(eased, start.x, end.x), Mth.lerp(eased, start.y, end.y), Mth.lerp(eased, start.z, end.z)),
                    Mth.lerp(eased, previous.height(), geometry.height()),
                    Mth.lerp(eased, previous.width(), geometry.width()),
                    Mth.lerp(eased, previous.depth(), geometry.depth())
                );
            }
        }
    }

    static int scaleRgb(int color, float factor) {
        int a = color >>> 24;
        if (a == 0) a = 255;
        int r = Math.min(255, Math.round((color >> 16 & 0xff) * factor));
        int g = Math.min(255, Math.round((color >> 8 & 0xff) * factor));
        int b = Math.min(255, Math.round((color & 0xff) * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int lerpColor(int start, int end, float progress) {
        float t = clamp01(progress);
        int a = Math.round(Mth.lerp(t, start >>> 24, end >>> 24));
        int r = Math.round(Mth.lerp(t, start >> 16 & 0xff, end >> 16 & 0xff));
        int g = Math.round(Mth.lerp(t, start >> 8 & 0xff, end >> 8 & 0xff));
        int b = Math.round(Mth.lerp(t, start & 0xff, end & 0xff));
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int applyOpacity(int color, float opacity, float scale) {
        int alpha = Math.round((color >>> 24) * clamp01(opacity) * clamp01(scale));
        return (color & 0x00ffffff) | (alpha << 24);
    }

    static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float easeInOutSine(float progress) {
        float t = clamp01(progress);
        return (1.0f - (float) Math.cos(Math.PI * t)) * 0.5f;
    }

    private static float easeOut(float progress, float exponent) {
        float t = clamp01(progress);
        return 1.0f - (float) Math.pow(1.0f - t, Math.max(0.01f, exponent));
    }

    private static float smoothStep(float progress) {
        float t = clamp01(progress);
        return t * t * (3.0f - 2.0f * t);
    }

    record TargetGeometry(Vec3 position, double height, double width, double depth) {}
    record TargetChange<T>(T previous, T current, boolean changed, boolean disappearing) {}
    enum TransitionPhase { IDLE, APPEARING, DISAPPEARING }

    static final class TargetTransition<T> {
        private long phaseStartedAt;
        private TransitionPhase phase = TransitionPhase.IDLE;
        private T current;

        TransitionPhase phase() { return phase; }
        T current() { return current; }
        boolean isEmpty() { return current == null && phase == TransitionPhase.IDLE; }

        TargetChange<T> updateTarget(T target) {
            long now = System.currentTimeMillis();
            if (target != null && target != current) {
                T previous = current;
                current = target;
                phase = TransitionPhase.APPEARING;
                phaseStartedAt = now;
                return new TargetChange<>(previous, current, true, false);
            } else if (target == null && current != null && phase != TransitionPhase.DISAPPEARING) {
                phase = TransitionPhase.DISAPPEARING;
                phaseStartedAt = now;
                return new TargetChange<>(current, current, false, true);
            }
            return new TargetChange<>(current, current, false, false);
        }

        void finishAppearance() { phase = TransitionPhase.IDLE; }
        void reset() { current = null; phaseStartedAt = 0L; phase = TransitionPhase.IDLE; }

        float progress(long durationMillis) {
            return durationMillis <= 0L ? 1.0f : clamp01((float) (System.currentTimeMillis() - phaseStartedAt) / (float) durationMillis);
        }
    }

    static final class TimedPulse {
        private long startedAt;
        void reset() { startedAt = 0L; }
        void trigger() { startedAt = System.currentTimeMillis(); }
        boolean isActive(long now, long durationMillis) {
            long elapsed = now - startedAt;
            return elapsed >= 0L && elapsed < durationMillis;
        }
        float progress(long now, long durationMillis) {
            return clamp01((float) (now - startedAt) / (float) durationMillis);
        }
    }

    static final class PreviousValueTransition<T> {
        private boolean active;
        private long startedAt;
        private T previousValue;

        void remember(T value) {
            if (value != null) {
                previousValue = value;
                startedAt = System.currentTimeMillis();
                active = true;
            }
        }
        void reset() { clear(); }
        void clear() { previousValue = null; startedAt = 0L; active = false; }
        T get() { return previousValue; }
        boolean hasValue() { return active && previousValue != null; }
        float progress(long durationMillis) {
            return durationMillis <= 0L ? 1.0f : clamp01((float) (System.currentTimeMillis() - startedAt) / (float) durationMillis);
        }
    }
}
