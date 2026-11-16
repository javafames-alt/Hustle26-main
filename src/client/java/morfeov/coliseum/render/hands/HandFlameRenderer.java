package morfeov.coliseum.render.hands;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/** Screen-space flame ribbons anchored to the current hand silhouette, with no previous-frame trails. */
public final class HandFlameRenderer implements AutoCloseable {
    private static final int STYLE_BYTES = 64;
    private static final int[] JUMPS = {64, 32, 16, 8, 4, 2, 1};
    private static final long STARTED = System.nanoTime();
    private static final RenderPipeline SEED = HandsFx.pipeline("flame_seed", "flame_seed",
        HandsFx.layout(b -> HandsFx.sampler(b, "HandDepthSampler")), ColorTargetState.DEFAULT);
    private static final RenderPipeline DISTANCE = HandsFx.pipeline("flame_distance", "flame_distance",
        HandsFx.layout(b -> {
            HandsFx.sampler(b, "SeedSampler");
            HandsFx.ubo(b, "FlameField");
        }), ColorTargetState.DEFAULT);
    private static final RenderPipeline COMPOSITE = HandsFx.pipeline("flame_composite", "flame_composite",
        HandsFx.layout(b -> {
            HandsFx.sampler(b, "HandDepthSampler");
            HandsFx.sampler(b, "DistanceSampler");
            HandsFx.ubo(b, "HandFlame");
        }), new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)));

    private final TextureTarget[] fields = new TextureTarget[2];
    private final GpuBuffer[] jumpUniforms = new GpuBuffer[JUMPS.length];
    private GpuBuffer style;
    private int sourceWidth, sourceHeight;

    public void render(RenderTarget output, GpuTextureView handDepth, float[] color,
                       float height, float speed, float wave, float glow) {
        if (output == null || handDepth == null || output.getColorTextureView() == null
                || output.width < 2 || output.height < 2 || color[3] <= .001f) return;
        ensureResources(output.width, output.height);
        HandsFx.pass("HandShader Flame / silhouette seeds", SEED, fields[0].getColorTextureView(),
            pass -> pass.setUniform("HandDepthSampler", handDepth, HandsFx.nearest()));
        int read = 0;
        for (int i = 0; i < JUMPS.length; i++) {
            int source = read, destination = 1 - read, index = i;
            HandsFx.pass("HandShader Flame / nearest silhouette", DISTANCE, fields[destination].getColorTextureView(), pass -> {
                pass.setUniform("SeedSampler", fields[source].getColorTextureView(), HandsFx.nearest());
                pass.setUniform("FlameField", jumpUniforms[index]);
            });
            read = destination;
        }
        HandsFx.writeUniforms(style, STYLE_BYTES, b -> b
            .putVec4(output.width, output.height, fields[0].width, fields[0].height)
            .putVec4(color[0], color[1], color[2], color[3])
            .putVec4((System.nanoTime() - STARTED) / 1e9f, height, speed, wave)
            .putVec4(glow, 0, 0, 0));
        int finalField = read;
        HandsFx.pass("HandShader Flame / flowing pink fire", COMPOSITE, output.getColorTextureView(), pass -> {
            pass.setUniform("HandDepthSampler", handDepth, HandsFx.nearest());
            pass.setUniform("DistanceSampler", fields[finalField].getColorTextureView(), HandsFx.nearest());
            pass.setUniform("HandFlame", style);
        });
    }

    private void ensureResources(int width, int height) {
        if (fields[0] != null && sourceWidth == width && sourceHeight == height) return;
        close();
        sourceWidth = width; sourceHeight = height;
        int divisor = Math.max(2, (height + 719) / 720);
        int w = Math.max(1, (width + divisor - 1) / divisor), h = Math.max(1, (height + divisor - 1) / divisor);
        for (int i = 0; i < fields.length; i++)
            fields[i] = new TextureTarget("HandShader Flame field " + i, w, h, GpuFormat.RGBA8_UNORM, null);
        style = HandsFx.createUniforms("HandShader Flame style", STYLE_BYTES);
        for (int i = 0; i < JUMPS.length; i++) {
            jumpUniforms[i] = HandsFx.createUniforms("HandShader Flame jump " + JUMPS[i], 16);
            int jump = JUMPS[i];
            HandsFx.writeUniforms(jumpUniforms[i], 16, b -> b.putVec4(w, h, jump, 0));
        }
    }

    @Override public void close() {
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] != null) fields[i].destroyBuffers();
            fields[i] = null;
        }
        for (int i = 0; i < jumpUniforms.length; i++) {
            if (jumpUniforms[i] != null) jumpUniforms[i].close();
            jumpUniforms[i] = null;
        }
        if (style != null) style.close();
        style = null;
        sourceWidth = sourceHeight = 0;
    }
}
