package im.yaHustler.render.weather;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import im.yaHustler.render.hands.HandsFx;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Procedural shader sky for Weather «Свой»: a camera-centred dome drawn in place of the vanilla sky pass.
 * All look (stars, meteors, moon, clouds, aurora) is computed per pixel in weather_sky.fsh from SkyData.
 */
public final class SkyDomeRenderer implements AutoCloseable {
    public static final int NIGHT = 0, DAY = 1, SUNSET = 2, AURORA = 3;
    /** Look parameters; written to the SkyData uniform block each frame. */
    public static final class Parameters {
        public int preset = NIGHT;
        public float clouds = .5f, cloudSpeed = 1, stars = 1, meteors = .5f, moonSize = 1, aurora = 1, milkyWay = 1;
    }
    private static final int SEGMENTS = 48, RINGS = 24, STRIDE = 16;
    private static final float RADIUS = 16;
    private static final int VERTICES = SEGMENTS * RINGS * 6;
    private ByteBuffer dome;
    private GpuBuffer matrices, data;
    private final Matrix4f viewProjection = new Matrix4f();
    private long started, last;
    private double cloudTime;

    public void render(Matrix4f projection, Matrix4f view, Parameters settings) {
        if (projection == null || view == null || !projection.isFinite() || !view.isFinite()) return;
        if (dome == null) { dome = build(); started = System.nanoTime(); }
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(.25, (now - last) / 1e9);
        last = now;
        cloudTime += dt * settings.cloudSpeed;
        float time = (float) (((now - started) / 1e9) % 3600.0);
        if (matrices == null) matrices = HandsFx.createUniforms("Weather sky matrix", 64);
        if (data == null) data = HandsFx.createUniforms("Weather sky data", 64);
        // Rotation-only view: the dome always stays centred on the camera.
        viewProjection.set(projection).mul(new Matrix4f(view).setTranslation(0, 0, 0));
        HandsFx.writeUniforms(matrices, 64, b -> b.putMat4f(viewProjection));
        float clouds = (float) (cloudTime % 2000.0);
        HandsFx.writeUniforms(data, 64, b -> b
            .putVec4(time, clouds, settings.preset, settings.clouds)
            .putVec4(settings.stars, settings.meteors, settings.moonSize, settings.aurora)
            .putVec4(settings.milkyWay, 0, 0, 0)
            .putVec4(0, 0, 0, 0));
        dome.position(0).limit(VERTICES * STRIDE);
        WeatherPass.draw(WeatherPipelines.SKY, dome, STRIDE, VERTICES, p -> {
            p.setUniform("SnowfallMatrices", matrices);
            p.setUniform("SkyData", data);
        });
    }

    private static ByteBuffer build() {
        ByteBuffer out = MemoryUtil.memAlloc(VERTICES * STRIDE);
        for (int ring = 0; ring < RINGS; ring++) {
            double p0 = -Math.PI / 2 + Math.PI * ring / RINGS, p1 = -Math.PI / 2 + Math.PI * (ring + 1) / RINGS;
            for (int seg = 0; seg < SEGMENTS; seg++) {
                double a0 = Math.PI * 2 * seg / SEGMENTS, a1 = Math.PI * 2 * (seg + 1) / SEGMENTS;
                vertex(out, p0, a0); vertex(out, p0, a1); vertex(out, p1, a1);
                vertex(out, p0, a0); vertex(out, p1, a1); vertex(out, p1, a0);
            }
        }
        return out.flip();
    }
    private static void vertex(ByteBuffer out, double pitch, double yaw) {
        double h = Math.cos(pitch);
        out.putFloat((float) (h * Math.cos(yaw) * RADIUS)).putFloat((float) (Math.sin(pitch) * RADIUS)).putFloat((float) (h * Math.sin(yaw) * RADIUS));
        out.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1);
    }

    @Override public void close() {
        if (dome != null) { MemoryUtil.memFree(dome); dome = null; }
        if (matrices != null) { matrices.close(); matrices = null; }
        if (data != null) { data.close(); data = null; }
        last = 0;
    }
}
