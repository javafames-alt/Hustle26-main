package morfeov.coliseum.render.hands;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public final class HandNoiseRenderer implements AutoCloseable {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();
    private static final BindGroupLayout LAYOUT = HandsFx.layout(b ->
        HandsFx.ubo(HandsFx.sampler(b, "HandDepth"), "HandShaderUniforms"));
    private static final ColorTargetState BLEND = new ColorTargetState(BlendFunction.TRANSLUCENT);

    private static final RenderPipeline NOISE = HandsFx.pipeline("noise", "noise", LAYOUT, BLEND);
    private static final RenderPipeline CAUSTIC = HandsFx.pipeline("caustic", "caustic", LAYOUT, BLEND);
    private static final RenderPipeline GLOW = HandsFx.pipeline("glow", "glow", LAYOUT, BLEND);

    private GpuBuffer uniforms;

    public void render(RenderTarget output, GpuTextureView handDepth, String mode, float[] color,
                       float intensity, float speed, float smoke, float width) {
        if (uniforms == null) uniforms = HandsFx.createUniforms("HandShader UBO", UNIFORM_SIZE);
        float time = HandsFx.time();
        HandsFx.writeUniforms(uniforms, UNIFORM_SIZE, b -> b
            .putVec4(color[0], color[1], color[2], color[3])
            .putVec4(time, intensity, speed, 0.55f)
            .putVec4(smoke, 0.84f, 1.35f, width));
        RenderPipeline pipeline = switch (mode) {
            case "Caustic" -> CAUSTIC;
            case "Glow" -> GLOW;
            default -> NOISE;
        };
        HandsFx.pass("HandShader / " + mode, pipeline, output.getColorTextureView(), pass -> {
            pass.setUniform("HandShaderUniforms", uniforms);
            pass.setUniform("HandDepth", handDepth, HandsFx.nearest());
        });
    }

    @Override
    public void close() {
        if (uniforms != null) uniforms.close();
        uniforms = null;
    }
}
