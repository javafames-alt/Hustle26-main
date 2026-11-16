package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.module.impl.themes.Theme;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

final class Ghosts {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("hustle263", "textures/esp/ghost.png");
    private static final int SPIRALS = 3;
    private static final int PER_SPIRAL = 20;
    private static final int TOTAL = SPIRALS * PER_SPIRAL;
    private static final int MAX_FRAGMENTS = 240;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int HURT = 0xffff5255;

    private final List<Fragment> fragments = new ArrayList<>(MAX_FRAGMENTS);
    private final Random random = new Random();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();

    private LivingEntity target;
    private int dissolvedId = Integer.MIN_VALUE;
    private boolean showing;
    private long animationStart;
    private float appear, inner, appearFrom, innerFrom, clock;
    private float[] lastShape = new float[0];
    private int lastShapeCount;

    void reset() {
        target = null;
        showing = false;
        appear = inner = 0.0f;
        fragments.clear();
        lastShapeCount = 0;
    }

    void render(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector, LivingEntity picked, float dt) {
        long now = System.currentTimeMillis();
        boolean present = picked != null && picked.isAlive() && !picked.isRemoved();
        if (present) {
            target = picked;
            if (picked.getId() == dissolvedId) dissolvedId = Integer.MIN_VALUE;
        }
        if (dead(target) && target.getId() != dissolvedId) {
            burst(target, now);
            dissolvedId = target.getId();
            present = false;
        }
        animate(present, now);

        Camera camera = mc.gameRenderer.mainCamera();
        Quaternionf rotation = camera.rotation();
        right.set(1, 0, 0).rotate(rotation);
        up.set(0, 1, 0).rotate(rotation);
        Vec3 cam = camera.position();

        float[] sprites = new float[(TOTAL + MAX_FRAGMENTS) * 8];
        int count = 0;

        if (target != null && appear > 0.01f && inner > 0.001f) {
            clock += dt * 1000.0f;
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            double bx = Mth.lerp(partialTick, target.xOld, target.getX());
            double by = Mth.lerp(partialTick, target.yOld, target.getY());
            double bz = Mth.lerp(partialTick, target.zOld, target.getZ());
            float animation = clock / 120.0f;
            float baseAlpha = Math.min(0.85f, inner * 1.6f);
            int color = hurtColor(Theme.accent(), target);

            for (int spiral = 0; spiral < SPIRALS; spiral++) {
                int offset = spiral * 3;
                int twist = offset * offset;
                for (int p = 0; p < PER_SPIRAL; p++) {
                    int index = spiral * PER_SPIRAL + p;
                    float phase = animation + p * 0.1f;
                    float assembly = assembly(inner, index);
                    double x = bx + 0.8 * Math.sin(phase + twist) + scatter(index, 0, 1.75, assembly);
                    double y = by + 0.5 + 0.3 * Math.sin(animation + p * 0.2f) + 0.2 * offset
                        + scatter(index, 1, 1.35, assembly) + (1.0f - assembly) * 0.35f;
                    double z = bz + 0.8 * Math.cos(phase - twist) + scatter(index, 2, 1.75, assembly);

                    float scale = inner * (0.005f + p / 2000.0f);
                    float tail = (float) Math.pow(p / 11.0f, 1.3);
                    float alpha = Math.clamp(baseAlpha * tail * appear, 0.0f, 1.0f);
                    float size = 50.0f * scale * (0.55f + assembly * 0.45f);
                    if (alpha <= 0.004f || size <= 0.002f) continue;
                    count = put(sprites, count, x - cam.x, y - cam.y, z - cam.z, size, color, alpha, phase);
                }
            }
            lastShape = sprites.clone();
            lastShapeCount = count;
        } else if (!present && appear <= 0.01f) {
            target = null;
            lastShapeCount = 0;
        }

        count = updateFragments(sprites, count, dt, now, cam);
        if (count == 0) return;

        int total = count;
        float rx = right.x, ry = right.y, rz = right.z;
        float ux = up.x, uy = up.y, uz = up.z;
        RenderType type = RenderTypes.energySwirl(TEXTURE, 0.0f, 0.0f);
        collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
            for (int i = 0; i < total; i++) {
                int o = i * 8;
                float cx = sprites[o], cy = sprites[o + 1], cz = sprites[o + 2];
                float half = sprites[o + 3] * 0.5f;
                int color = Float.floatToRawIntBits(sprites[o + 4]);
                float alpha = sprites[o + 5];
                float cos = Mth.cos(sprites[o + 6]) * half;
                float sin = Mth.sin(sprites[o + 6]) * half;

                float ax = rx * cos + ux * sin, ay = ry * cos + uy * sin, az = rz * cos + uz * sin;
                float bx = ux * cos - rx * sin, by = uy * cos - ry * sin, bz = uz * cos - rz * sin;

                float r = (color >> 16 & 0xff) / 255.0f * alpha;
                float g = (color >> 8 & 0xff) / 255.0f * alpha;
                float b = (color & 0xff) / 255.0f * alpha;

                vertex(pose, vc, cx - ax - bx, cy - ay - by, cz - az - bz, 0, 1, r, g, b, alpha);
                vertex(pose, vc, cx + ax - bx, cy + ay - by, cz + az - bz, 1, 1, r, g, b, alpha);
                vertex(pose, vc, cx + ax + bx, cy + ay + by, cz + az + bz, 1, 0, r, g, b, alpha);
                vertex(pose, vc, cx - ax + bx, cy - ay + by, cz - az + bz, 0, 0, r, g, b, alpha);
            }
        });
    }

    private static void vertex(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    private static int put(float[] out, int count, double x, double y, double z,
                           float size, int color, float alpha, float spin) {
        if (count * 8 + 8 > out.length) return count;
        int o = count * 8;
        out[o] = (float) x;
        out[o + 1] = (float) y;
        out[o + 2] = (float) z;
        out[o + 3] = size;
        out[o + 4] = Float.intBitsToFloat(color);
        out[o + 5] = alpha;
        out[o + 6] = spin;
        return count + 1;
    }

    private void animate(boolean present, long now) {
        if (present != showing) {
            showing = present;
            animationStart = now;
            appearFrom = appear;
            innerFrom = inner;
        }
        float goal = showing ? 1.0f : 0.0f;
        long elapsed = Math.max(0L, now - animationStart);
        float appearT = Math.clamp(elapsed / (showing ? 650.0f : 450.0f), 0.0f, 1.0f);
        float innerT = Math.clamp(elapsed / (showing ? 850.0f : 500.0f), 0.0f, 1.0f);
        float appearEase = showing ? outExpo(appearT) : outQuad(appearT);
        float innerEase = showing ? inOutQuad(innerT) : outQuad(innerT);
        appear = appearFrom + (goal - appearFrom) * appearEase;
        inner = innerFrom + (goal - innerFrom) * innerEase;
    }

    private static float assembly(float progress, int index) {
        float position = index / (float) (TOTAL - 1);
        float delay = position * 0.22f;
        float t = Math.clamp((progress - delay) / (1.0f - delay), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static double scatter(int seed, int axis, double distance, float assembly) {
        int hash = seed * 0x9E3779B9 + axis * 0x7F4A7C15;
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        float signed = ((hash >>> 8) & 0xFFFF) / 32767.5f - 1.0f;
        return signed * distance * (1.0f - assembly);
    }

    private static float outExpo(float t) {
        return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float outQuad(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    private static float inOutQuad(float t) {
        return t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 2.0) / 2.0f;
    }

    private static int hurtColor(int color, LivingEntity entity) {
        if (entity.hurtTime <= 0) return color;
        float k = Math.clamp(entity.hurtTime / 10.0f, 0.0f, 1.0f);
        k = 1.0f - (1.0f - k) * (1.0f - k);
        return mix(color, HURT, k);
    }

    private static int mix(int from, int to, float k) {
        int a = Math.round(Mth.lerp(k, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(k, from >> 16 & 0xff, to >> 16 & 0xff));
        int g = Math.round(Mth.lerp(k, from >> 8 & 0xff, to >> 8 & 0xff));
        int b = Math.round(Mth.lerp(k, from & 0xff, to & 0xff));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0f);
    }

    private void burst(LivingEntity entity, long now) {
        fragments.clear();
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        int color = Theme.accent() | 0xff000000;

        if (lastShapeCount == 0) {
            AABB box = entity.getBoundingBox();
            for (int i = 0; i < 90; i++) {
                Vec3 pos = new Vec3(
                    box.minX + (box.maxX - box.minX) * random.nextDouble(),
                    box.minY + (box.maxY - box.minY) * random.nextDouble(),
                    box.minZ + (box.maxZ - box.minZ) * random.nextDouble());
                spawn(pos, center, range(0.025f, 0.07f), color, now, 1.0f);
            }
            return;
        }

        int copies = Math.max(1, Math.min(3, MAX_FRAGMENTS / lastShapeCount));
        for (int i = 0; i < lastShapeCount && fragments.size() < MAX_FRAGMENTS; i++) {
            int o = i * 8;
            Vec3 pos = new Vec3(lastShape[o] + cam.x, lastShape[o + 1] + cam.y, lastShape[o + 2] + cam.z);
            float size = lastShape[o + 3];
            int shapeColor = Float.floatToRawIntBits(lastShape[o + 4]);
            float alpha = lastShape[o + 5];
            for (int c = 0; c < copies && fragments.size() < MAX_FRAGMENTS; c++)
                spawn(pos, center, Math.max(0.018f, size * 0.5f * range(0.18f, 0.42f)), shapeColor, now, Math.max(0.15f, alpha));
        }
        lastShapeCount = 0;
    }

    private void spawn(Vec3 pos, Vec3 center, float half, int color, long now, float alphaScale) {
        Vec3 radial = pos.subtract(center);
        double flat = Math.sqrt(radial.x * radial.x + radial.z * radial.z);
        double dx = flat > 1.0E-5 ? radial.x / flat : 0.0;
        double dz = flat > 1.0E-5 ? radial.z / flat : 0.0;
        float outward = range(0.35f, 1.15f);

        Fragment f = new Fragment();
        f.x = pos.x + range(-0.025f, 0.025f);
        f.y = pos.y + range(-0.025f, 0.025f);
        f.z = pos.z + range(-0.025f, 0.025f);
        f.vx = dx * outward + range(-0.24f, 0.24f);
        f.vy = range(0.28f, 1.05f) + Math.max(0.0, radial.y) * 0.04;
        f.vz = dz * outward + range(-0.24f, 0.24f);
        f.half = Math.min(0.18f, half);
        f.color = color | 0xff000000;
        f.born = now;
        f.life = 1200L + random.nextInt(1200);
        f.alphaScale = alphaScale;
        f.phase = random.nextFloat() * Mth.TWO_PI;
        f.twinkle = range(4.0f, 8.0f);
        fragments.add(f);
    }

    private int updateFragments(float[] out, int count, float dt, long now, Vec3 cam) {
        Iterator<Fragment> it = fragments.iterator();
        float drag = (float) Math.exp(-1.65f * dt);
        while (it.hasNext()) {
            Fragment f = it.next();
            long age = now - f.born;
            if (age >= f.life) {
                it.remove();
                continue;
            }
            f.phase += f.twinkle * dt;
            f.vx = f.vx * drag + Math.cos(f.phase) * 0.045f * dt;
            f.vz = f.vz * drag + Math.sin(f.phase * 0.87f) * 0.045f * dt;
            f.vy += 0.22f * dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.z += f.vz * dt;

            float progress = Math.clamp(age / (float) f.life, 0.0f, 1.0f);
            float fadeIn = Math.clamp(age / 90.0f, 0.0f, 1.0f);
            float fadeOut = (1.0f - progress) * (1.0f - progress);
            float twinkle = 0.72f + 0.28f * Mth.sin(f.phase);
            float alpha = Math.clamp(fadeIn * fadeOut * twinkle * f.alphaScale * 1.25f, 0.0f, 1.0f);
            float size = f.half * (1.0f + progress * 0.65f) * 2.0f;
            count = put(out, count, f.x - cam.x, f.y - cam.y, f.z - cam.z, size, f.color, alpha, f.phase);
        }
        return count;
    }

    private float range(float min, float max) {
        return min + (max - min) * random.nextFloat();
    }

    private static final class Fragment {
        double x, y, z, vx, vy, vz;
        float half, alphaScale, phase, twinkle;
        int color;
        long born, life;
    }
}
