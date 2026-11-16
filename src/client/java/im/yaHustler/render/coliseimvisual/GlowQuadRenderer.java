package im.yaHustler.render.coliseimvisual;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.joml.Matrix4f;

public final class GlowQuadRenderer {
    private static final String DEAD_CHAMS = "dead_chams";
    private static final String PRIVATE_GLOW = "private_glow";
    private static final String SAMPLER0_ = "Sampler0";


    private GlowQuadRenderer() {
    }

    public static void appendBillboardQuad(BufferBuilder vertices, Matrix4f transform, float size, int color) {
        float halfSize = size / 2.0F;
        vertices.addVertex(transform, -halfSize, halfSize, 0.0F).setUv(0.0F, 1.0F).setColor(color);
        vertices.addVertex(transform, halfSize, halfSize, 0.0F).setUv(1.0F, 1.0F).setColor(color);
        vertices.addVertex(transform, halfSize, -halfSize, 0.0F).setUv(1.0F, 0.0F).setColor(color);
        vertices.addVertex(transform, -halfSize, -halfSize, 0.0F).setUv(0.0F, 0.0F).setColor(color);
    }

    public static RenderPipeline createGlowPipeline(String location, CompareOp depthTest) {
        return VisualPipelineBuilder.builder(VisualPipelineBuilder.SNIPPET)
            .withLocation(location)
            .withVertexShader(VisualResources.shader(DEAD_CHAMS))
            .withFragmentShader(VisualResources.shader(PRIVATE_GLOW))
            .withDepthTest(depthTest)
            .withDepthWrite(false)
            .withCull(false)
            .withBlend(BlendFunction.OVERLAY)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
            .withSampler(SAMPLER0_)
            .buildPipeline();
    }
}
