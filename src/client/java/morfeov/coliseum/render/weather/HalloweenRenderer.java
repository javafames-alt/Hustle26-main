package morfeov.coliseum.render.weather;

import java.nio.ByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/**
 * Halloween atmosphere: floating 3D jack-o'-lanterns with flickering candles, drifting embers,
 * bats circling overhead and will-o'-wisps near the ground. Everything is depth-tested against the world.
 * Billboard materials (vertex color b): 255 ember, 128 bat (g = wing flap), 40 wisp (g = hue).
 */
public final class HalloweenRenderer implements AutoCloseable {
    /** Tunables supplied by the Weather module every frame. */
    public static final class Parameters {
        public float embers = 1, pumpkins = 1, pumpkinScale = 1, glow = 1, wind = 1;
        public boolean bats = true, wisps = true;
    }

    private static final int MAX_EMBERS = 480, MAX_BATS = 14, MAX_WISPS = 18, MAX_PUMPKINS = 24;
    private static final int MAX_BILLBOARDS = MAX_EMBERS + MAX_BATS + MAX_WISPS;
    private static final int SEED_COUNT = 512;
    private static final double[] SEEDS = new double[SEED_COUNT], PHASES = new double[SEED_COUNT],
        Z_SEEDS = new double[SEED_COUNT], Y_SEEDS = new double[SEED_COUNT];
    static {
        for (int i = 0; i < SEED_COUNT; i++) {
            SEEDS[i] = hash(i); PHASES[i] = hash(i + 731) * Math.PI * 2; Z_SEEDS[i] = hash(i + 97); Y_SEEDS[i] = hash(i + 241);
        }
    }
    private static final float EMBER = 1f, BAT = .5f, WISP = .16f;

    private final WeatherFlakePipeline pipeline = new WeatherFlakePipeline();
    private final WeatherFrustum visible = new WeatherFrustum();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f right = new Vector3f(), up = new Vector3f();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final float[][] pumpkins = new float[MAX_PUMPKINS][8];
    private final int[] order = new int[MAX_PUMPKINS];
    private ByteBuffer vertices, models;
    private long started;
    private double lastFrame, anchorX, anchorY, anchorZ;
    private boolean anchored;

    public void render(Camera camera, Matrix4f view, Matrix4f projection, Parameters settings) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || settings == null) return;
        if (vertices == null) {
            vertices = MemoryUtil.memAlloc(MAX_BILLBOARDS * 6 * 24);
            models = MemoryUtil.memAlloc(MAX_PUMPKINS * PumpkinMesh.VERTEX_COUNT * 24);
            started = System.nanoTime();
        }
        vertices.clear(); models.clear();
        double time = (System.nanoTime() - started) / 1e9; var origin = camera.position();
        visible.update(projection, view);
        inverse.set(view).invert();
        inverse.transformDirection(right.set(1, 0, 0)).normalize();
        inverse.transformDirection(up.set(0, 1, 0)).normalize();
        float wind = Math.max(0, settings.wind);

        // ------------------------------------------------------------ pumpkins
        // Positions are anchored to the world (fixed cells + terrain height), so jumping or walking
        // never drags the lanterns along with the camera; only a gentle bob/sway is animated.
        int pumpkinCount = 0;
        int wanted = Math.min(MAX_PUMPKINS, Math.max(0, Math.round(12 * settings.pumpkins)));
        float scale = Math.clamp(settings.pumpkinScale, .3f, 3f);
        for (int i = 0; i < wanted; i++) {
            int s = 300 + i;
            double seed = SEEDS[s], phase = PHASES[s];
            double baseX = wrap(seed * 30 - origin.x, 30), baseZ = wrap(Z_SEEDS[s] * 30 - origin.z, 30);
            double horizontal = Math.sqrt(baseX * baseX + baseZ * baseZ);
            if (horizontal >= 15 || horizontal <= 1.2) continue;
            int bx = (int) Math.floor(origin.x + baseX), bz = (int) Math.floor(origin.z + baseZ);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            double x = baseX + Math.sin(time * .21 + phase) * .35;
            double z = baseZ + Math.cos(time * .17 + phase) * .35;
            double y = ground + 1.4 + Y_SEEDS[s] * 3.5 + Math.sin(time * .6 + phase) * .18 - origin.y;
            if (Math.abs(y) > 24) continue;
            float size = (.26f + (float) seed * .18f) * scale;
            float dx = (float) x, dy = (float) y, dz = (float) z;
            if (!visible.particle(dx, dy, dz, size * 1.4f, 1)) continue;
            float alpha = Math.clamp((15 - (float) horizontal) / 3, 0, 1) * Math.clamp(((float) horizontal - 1.2f) / 1.5f, 0, 1);
            if (alpha <= .01f) continue;
            // Candle flicker: two incommensurate sines plus a fast shimmer.
            float flicker = .74f + .14f * (float) Math.sin(time * 7.3 + phase * 3) + .08f * (float) Math.sin(time * 13.1 + phase)
                + .04f * (float) Math.sin(time * 29.0 + seed * 40);
            float[] p = pumpkins[pumpkinCount];
            // Each lantern has its own fixed heading with a lazy sway (independent of the camera).
            p[0] = dx; p[1] = dy; p[2] = dz; p[3] = size;
            p[4] = (float) (phase * 2 + Math.sin(time * .25 + phase) * .35);
            p[5] = (float) Math.sin(time * .4 + phase) * .10f;
            p[6] = alpha; p[7] = Math.clamp(flicker * settings.glow, 0, 1);
            float depth = view.m02() * dx + view.m12() * dy + view.m22() * dz;
            int at = pumpkinCount;
            while (at > 0) { // stable back-to-front insertion, no allocations
                float[] previous = pumpkins[order[at - 1]];
                float previousDepth = view.m02() * previous[0] + view.m12() * previous[1] + view.m22() * previous[2];
                if (previousDepth <= depth) break;
                order[at] = order[at - 1]; at--;
            }
            order[at] = pumpkinCount++;
        }
        for (int i = 0; i < pumpkinCount; i++) {
            float[] p = pumpkins[order[i]];
            PumpkinMesh.append(models, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
        }

        // ------------------------------------------------------------ embers
        int embers = Math.min(MAX_EMBERS, Math.max(0, Math.round(240 * settings.embers)));
        for (int i = 0; i < embers; i++) {
            double seed = SEEDS[i], phase = PHASES[i];
            double x = wrap(seed * 36 + time * wind * .24 - origin.x, 36) + Math.sin(time * .45 + phase) * .65;
            double z = wrap(Z_SEEDS[i] * 36 + time * wind * .09 - origin.z, 36);
            double y = wrap(Y_SEEDS[i] * 24 + time * .45 - origin.y, 24) + Math.sin(time * .7 + phase) * .3;
            double distanceSquared = x * x + y * y + z * z;
            if (distanceSquared >= 17 * 17 || distanceSquared <= .7 * .7) continue;
            float size = .025f + (float) seed * .025f;
            float dx = (float) x, dy = (float) y, dz = (float) z;
            if (!visible.particle(dx, dy, dz, size, 1)) continue;
            float distance = (float) Math.sqrt(distanceSquared);
            float alpha = Math.clamp((17 - distance) / 4, 0, 1) * Math.clamp((distance - .7f) / 1.5f, 0, 1);
            if (alpha <= .01f || !openSky(level, origin.x + x, origin.y + y, origin.z + z)) continue;
            float brightness = .78f + .22f * (float) Math.sin(time * 1.7 + phase);
            quad(dx, dy, dz, size, (float) phase, alpha, brightness, 1, EMBER);
        }

        // ------------------------------------------------------------ bats
        // The flock circles a lazily-following anchor, so jumps barely move it.
        double frame = lastFrame == 0 ? 0 : Math.min(.25, time - lastFrame);
        lastFrame = time;
        if (!anchored || Math.abs(anchorX - origin.x) > 48 || Math.abs(anchorZ - origin.z) > 48 || Math.abs(anchorY - origin.y) > 32) {
            anchorX = origin.x; anchorY = origin.y; anchorZ = origin.z; anchored = true;
        } else {
            double follow = 1 - Math.exp(-frame * .6), rise = 1 - Math.exp(-frame * .25);
            anchorX += (origin.x - anchorX) * follow; anchorZ += (origin.z - anchorZ) * follow;
            anchorY += (origin.y - anchorY) * rise;
        }
        if (settings.bats) {
            for (int i = 0; i < MAX_BATS; i++) {
                int s = 360 + i;
                double seed = SEEDS[s], phase = PHASES[s];
                double radius = 5 + seed * 7, speed = (.25 + Z_SEEDS[s] * .3) * (i % 2 == 0 ? 1 : -1);
                double angle = phase + time * speed;
                double wobble = Math.sin(time * 1.9 + phase) * .6;
                double x = anchorX - origin.x + Math.cos(angle) * (radius + wobble);
                double z = anchorZ - origin.z + Math.sin(angle) * (radius + wobble);
                double y = anchorY - origin.y + 4 + Y_SEEDS[s] * 5 + Math.sin(time * 1.3 + phase) * .5;
                float size = .22f + (float) seed * .14f;
                float dx = (float) x, dy = (float) y, dz = (float) z;
                if (!visible.particle(dx, dy, dz, size, 1)) continue;
                if (!openSky(level, origin.x + x, origin.y + y, origin.z + z)) continue;
                float flap = .5f + .5f * (float) Math.sin(time * (14 + seed * 6) + phase);
                float roll = (float) (Math.sin(time * .8 + phase) * .25);
                quad(dx, dy, dz, size, roll, 1, 1, flap, BAT);
            }
        }

        // ------------------------------------------------------------ wisps
        if (settings.wisps) {
            for (int i = 0; i < MAX_WISPS; i++) {
                int s = 400 + i;
                double seed = SEEDS[s], phase = PHASES[s];
                double x = wrap(seed * 26 - origin.x, 26) + Math.sin(time * .13 + phase) * .8;
                double z = wrap(Z_SEEDS[s] * 26 - origin.z, 26) + Math.cos(time * .11 + phase) * .8;
                int wx = (int) Math.floor(origin.x + x), wz = (int) Math.floor(origin.z + z);
                if (!level.hasChunk(wx >> 4, wz >> 4)) continue;
                // Hover just above the terrain under the wisp (world-anchored, independent of jumps).
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) + .45 + Y_SEEDS[s] * 1.1
                    + Math.sin(time * .9 + phase) * .2 - origin.y;
                double distanceSquared = x * x + y * y + z * z;
                if (distanceSquared >= 14 * 14 || distanceSquared <= 1.2 * 1.2) continue;
                float size = .16f + (float) seed * .12f;
                float dx = (float) x, dy = (float) y, dz = (float) z;
                if (!visible.particle(dx, dy, dz, size, 1)) continue;
                float distance = (float) Math.sqrt(distanceSquared);
                float flat = (float) Math.max(Math.abs(x), Math.abs(z)); // reaches 13 exactly at the wrap seam
                float alpha = Math.clamp((12.5f - flat) / 3, 0, 1) * Math.clamp((distance - 1.2f) / 1.5f, 0, 1);
                if (alpha <= .01f || !openSky(level, origin.x + x, origin.y + y, origin.z + z)) continue;
                float pulse = .6f + .4f * (float) Math.sin(time * 2.3 + phase);
                quad(dx, dy, dz, size, 0, alpha * (.55f + .45f * pulse), pulse, (float) Z_SEEDS[s], WISP);
            }
        }

        models.flip(); vertices.flip();
        var target = mc.gameRenderer.mainRenderTarget();
        if (pumpkinCount > 0)
            pipeline.draw(target, null, visible.matrix(), models, pumpkinCount * PumpkinMesh.VERTEX_COUNT, WeatherPipelines.PUMPKIN);
        if (vertices.remaining() > 0)
            pipeline.draw(target, null, visible.matrix(), vertices, vertices.remaining() / 24, WeatherPipelines.HALLOWEEN);
    }

    private boolean openSky(ClientLevel level, double x, double y, double z) {
        pos.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && level.canSeeSky(pos);
    }

    /** Camera-facing quad rotated by {@code roll}; r = brightness, g = extra, b = material. */
    private void quad(float x, float y, float z, float size, float roll, float alpha, float brightness, float extra, float material) {
        float c = (float) Math.cos(roll) * size, s = (float) Math.sin(roll) * size;
        float rx = right.x * c + up.x * s, ry = right.y * c + up.y * s, rz = right.z * c + up.z * s;
        float ux = up.x * c - right.x * s, uy = up.y * c - right.y * s, uz = up.z * c - right.z * s;
        vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, alpha, brightness, extra, material);
        vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, alpha, brightness, extra, material);
        vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, alpha, brightness, extra, material);
        vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, alpha, brightness, extra, material);
        vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, alpha, brightness, extra, material);
        vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, alpha, brightness, extra, material);
    }
    private void vertex(float x, float y, float z, float u, float v, float alpha, float brightness, float extra, float material) {
        vertices.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        vertices.put(unit(brightness)).put(unit(extra)).put(unit(material)).put(unit(alpha));
    }
    private static byte unit(float value) { return (byte) Math.round(Math.clamp(value, 0, 1) * 255); }
    private static double wrap(double value, double period) { return value - Math.floor((value + period / 2) / period) * period; }
    private static double hash(int n) { long h = (n * 374761393L + 668265263L); h = (h ^ (h >> 13)) * 1274126177L; return ((h ^ (h >> 16)) & 0xffffff) / (double) 0xffffff; }
    @Override public void close() {
        pipeline.close();
        if (vertices != null) { MemoryUtil.memFree(vertices); vertices = null; }
        if (models != null) { MemoryUtil.memFree(models); models = null; }
        started = 0; lastFrame = 0; anchored = false;
    }
}
