package im.yaHustler.render.weather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import im.yaHustler.render.hands.HandsFx;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Static wet tint on upward-facing surfaces. The original scene detail stays intact. */
public final class WetSurfaceRenderer implements AutoCloseable {
    public static final class Parameters {
        public float wetness = 1, puddleCoverage = .75f, maxDistance = 80;
    }
    private GpuBuffer uniforms;
    public void render(Matrix4f projection, Matrix4f view, Parameters settings) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget scene = mc.gameRenderer.mainRenderTarget();
        if (settings.wetness <= 0 || scene == null || scene.getDepthTextureView() == null || scene.getColorTextureView() == null
                || scene.width < 2 || scene.height < 2 || Math.abs(projection.determinant()) < 1e-8f) return;
        if (uniforms == null) uniforms = HandsFx.createUniforms("Weather wet surface material", 160);
        var camera = mc.gameRenderer.mainCamera().position();
        HandsFx.writeUniforms(uniforms, 160, b -> b.putMat4f(new Matrix4f(projection).invert()).putMat4f(new Matrix4f(view).invert())
            .putVec4(settings.wetness, settings.puddleCoverage, settings.maxDistance, 0)
            .putVec4((float)(camera.x % 8192), (float)(camera.y % 8192), (float)(camera.z % 8192), 0));
        HandsFx.pass("Weather wet surface", WeatherPipelines.WET, scene.getColorTextureView(), p -> {
            p.setUniform("DepthSampler", scene.getDepthTextureView(), HandsFx.nearest());
            p.setUniform("WetSurfaceData", uniforms);
        });
    }
    @Override public void close() {
        if (uniforms != null) { uniforms.close(); uniforms = null; }
    }
}
