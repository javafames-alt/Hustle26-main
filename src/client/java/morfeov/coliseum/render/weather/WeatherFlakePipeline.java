package morfeov.coliseum.render.weather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import morfeov.coliseum.render.hands.HandsFx;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;
import java.nio.ByteBuffer;

public final class WeatherFlakePipeline implements AutoCloseable {
    private GpuBuffer matrices;
    public void draw(RenderTarget target, AbstractTexture texture, Matrix4f vp, ByteBuffer data, int count) {
        draw(target, texture, vp, data, count, WeatherPipelines.FLAKE);
    }
    public void draw(RenderTarget target, AbstractTexture texture, Matrix4f vp, ByteBuffer data, int count,
                     com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline) {
        if (count == 0 || target.getDepthTextureView() == null) return;
        if (matrices == null) matrices = HandsFx.createUniforms("Weather snowfall matrix", 64);
        HandsFx.writeUniforms(matrices, 64, b -> b.putMat4f(vp));
        WeatherPass.draw(pipeline, data, 24, count, p -> {
            p.setUniform("SnowfallMatrices", matrices);
            if (texture != null) p.setUniform("FlakeSampler", texture.getTextureView(), texture.getSampler());
        });
    }
    @Override public void close() { if (matrices != null) { matrices.close(); matrices = null; } }
}
