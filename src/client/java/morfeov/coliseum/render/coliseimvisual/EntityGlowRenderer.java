package morfeov.coliseum.render.coliseimvisual;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class EntityGlowRenderer implements AutoCloseable, MediaSessionHost {
    private static final String IN_SAMPLER = "InSampler";
    private static final String BLOOM_SAMPLER = "BloomSampler";
    private static final String MASK_SAMPLER = "MaskSampler";
    private static final String ESP_OUTER_GLOW = "EspOuterGlow";
    private static final String ESP_BLUR = "EspBlur";
    private static final String HORIZONTAL_UNIFORM_LABEL = "entityEspOtherBlurHorizontalUniforms";
    private static final String VERTICAL_UNIFORM_LABEL = "entityEspOtherBlurVerticalUniforms";
    private static final String GLOW_UNIFORM_LABEL = "entityEspOtherGlowUniforms";
    private static final String HORIZONTAL_TARGET_LABEL = "entity-esp-other-blur-horizontal";
    private static final String VERTICAL_TARGET_LABEL = "entity-esp-other-blur-vertical";
    private static final String BLUR_PASS_LABEL = "entityEspOtherBlur";
    private static final String GLOW_PASS_LABEL = "entityEspOtherGlow";
    private static final String ENTITY_ESP_OTHER_MASK = "entityEspOtherMask";
    private static final String MASK_LOCATION = "coliseum/coliseimvisual/entity_esp_other_mask";
    private static final String HANDS_FULLSCREEN = "hands_fullscreen";
    private static final String ENTITY_ESP_MASK = "entity_esp_mask";
    private static final String BLUR_LOCATION = "coliseum/coliseimvisual/entity_esp_other_blur";
    private static final String ENTITY_ESP_GAUSSIAN_BLUR = "entity_esp_gaussian_blur";
    private static final String GLOW_LOCATION = "coliseum/coliseimvisual/entity_esp_other_glow";
    private static final String ENTITY_ESP_OUTER_GLOW = "entity_esp_outer_glow";

    private static final RenderPipeline MASK_PIPELINE = VisualPipelineBuilder.builder()
        .withLocation(EntityGlowRenderer.MASK_LOCATION)
        .withVertexShader(VisualResources.shader(EntityGlowRenderer.HANDS_FULLSCREEN))
        .withFragmentShader(VisualResources.shader(EntityGlowRenderer.ENTITY_ESP_MASK))
        .withDepthTest(CompareOp.ALWAYS_PASS)
        .withDepthWrite(false)
        .withCull(false)
        .withoutBlend()
        .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
        .withSampler(EntityGlowRenderer.IN_SAMPLER)
        .buildPipeline();
    private static final RenderPipeline BLUR_PIPELINE = VisualPipelineBuilder.builder()
        .withLocation(EntityGlowRenderer.BLUR_LOCATION)
        .withVertexShader(VisualResources.shader(EntityGlowRenderer.HANDS_FULLSCREEN))
        .withFragmentShader(VisualResources.shader(EntityGlowRenderer.ENTITY_ESP_GAUSSIAN_BLUR))
        .withDepthTest(CompareOp.ALWAYS_PASS)
        .withDepthWrite(false)
        .withCull(false)
        .withoutBlend()
        .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
        .withUniform(EntityGlowRenderer.ESP_BLUR, UniformType.UNIFORM_BUFFER)
        .withSampler(EntityGlowRenderer.IN_SAMPLER)
        .buildPipeline();
    private static final RenderPipeline GLOW_PIPELINE = VisualPipelineBuilder.builder()
        .withLocation(EntityGlowRenderer.GLOW_LOCATION)
        .withVertexShader(VisualResources.shader(EntityGlowRenderer.HANDS_FULLSCREEN))
        .withFragmentShader(VisualResources.shader(EntityGlowRenderer.ENTITY_ESP_OUTER_GLOW))
        .withDepthTest(CompareOp.ALWAYS_PASS)
        .withDepthWrite(false)
        .withCull(false)
        .withBlend(BlendFunction.LIGHTNING)
        .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
        .withUniform(EntityGlowRenderer.ESP_OUTER_GLOW, UniformType.UNIFORM_BUFFER)
        .withSampler(EntityGlowRenderer.BLOOM_SAMPLER)
        .withSampler(EntityGlowRenderer.MASK_SAMPLER)
        .buildPipeline();
    private static TextureTarget blurTargetA;
    private static TextureTarget blurTargetB;
    private static GpuBuffer horizontalBlurUniforms;
    private static GpuBuffer verticalBlurUniforms;
    private static GpuBuffer glowUniforms;

    private static void writeGlowUniforms(CommandEncoder encoder, RenderTarget mask, int color, float strength, float outlineWidth, boolean fill, float fillOpacity, int secondaryColor, float secondaryStrength) {
        glowUniforms = ensureUniformBuffer(glowUniforms, 80, GLOW_UNIFORM_LABEL);
        float red = redChannel(color);
        float green = greenChannel(color);
        float blue = blueChannel(color);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = Std140Builder.onStack(stack, 80)
                .putVec4(strength, fill ? 1.0F : 0.0F, outlineWidth, fillOpacity)
                .putVec4(red, green, blue, 1.0F)
                .putVec4(red * 0.5F, green * 0.5F, blue * 0.5F, secondaryStrength)
                .putVec4(redChannel(secondaryColor), greenChannel(secondaryColor), blueChannel(secondaryColor), alphaChannel(secondaryColor))
                .putVec4(mask.width, mask.height, secondaryStrength, 0.0F)
                .get();
            encoder.writeToBuffer(glowUniforms.slice(), uniformData);
        }
    }

    private static GpuBuffer ensureUniformBuffer(GpuBuffer buffer, int size, String label) {
        return buffer != null && !buffer.isClosed() ? buffer : RenderSystem.getDevice().createBuffer(() -> label, 136, size);
    }

    private static float blueChannel(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private static boolean hasColorTarget(RenderTarget target) {
        return target != null && target.getColorTextureView() != null;
    }

    private static void ensureBlurTargets(int width, int height) {
        if (blurTargetA == null || blurTargetB == null) {
            blurTargetA = new TextureTarget(HORIZONTAL_TARGET_LABEL, width, height, GpuFormat.RGBA8_UNORM, null);
            blurTargetB = new TextureTarget(VERTICAL_TARGET_LABEL, width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (blurTargetA.width != width || blurTargetA.height != height) {
            blurTargetA.resize(width, height);
            blurTargetB.resize(width, height);
        }
    }

    public static void renderEntityGlow(RenderTarget mask, RenderTarget target, int color, float radius, float strength, float outlineWidth, boolean fill, float fillOpacity, int secondaryColor, float secondaryStrength) {
        if (hasColorTarget(mask) && hasColorTarget(target)) {
            int blurWidth = Math.max(1, mask.width / 2);
            int blurHeight = Math.max(1, mask.height / 2);
            float blurRadius = Math.clamp(radius, 1.0F, 12.0F);
            ensureBlurTargets(blurWidth, blurHeight);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            writeBlurUniforms(encoder, blurWidth, blurHeight, blurRadius);
            writeGlowUniforms(encoder, mask, color, strength, outlineWidth, fill, fillOpacity, secondaryColor, secondaryStrength);
            try (RenderPass pass = encoder.createRenderPass(() -> ENTITY_ESP_OTHER_MASK, blurTargetA.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(MASK_PIPELINE));
                pass.setUniform(IN_SAMPLER, mask.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3, 1, 0, 0);
            }

            drawBlurPass(encoder, blurTargetA, blurTargetB, horizontalBlurUniforms);
            drawBlurPass(encoder, blurTargetB, blurTargetA, verticalBlurUniforms);
            try (RenderPass pass = encoder.createRenderPass(() -> GLOW_PASS_LABEL, target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(GLOW_PIPELINE));
                pass.setUniform(BLOOM_SAMPLER, blurTargetA.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform(MASK_SAMPLER, mask.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setUniform(ESP_OUTER_GLOW, glowUniforms);
                pass.draw(3, 1, 0, 0);
            }
        }
    }

    private static void drawBlurPass(CommandEncoder encoder, RenderTarget source, RenderTarget target, GpuBuffer uniforms) {
        try (RenderPass pass = encoder.createRenderPass(() -> BLUR_PASS_LABEL, target.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(BLUR_PIPELINE));
            pass.setUniform(IN_SAMPLER, source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform(ESP_BLUR, uniforms);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static float greenChannel(int color) {
        return (color >>> 8 & 0xFF) / 255.0F;
    }

    private static float alphaChannel(int color) {
        return (color >>> 24 & 0xFF) / 255.0F;
    }

    @Override
    public void close() {
        closeShared();
    }

    public static void closeShared() {
        if (blurTargetA != null) {
            blurTargetA.destroyBuffers();
            blurTargetA = null;
        }

        if (blurTargetB != null) {
            blurTargetB.destroyBuffers();
            blurTargetB = null;
        }

        if (horizontalBlurUniforms != null) {
            horizontalBlurUniforms.close();
            horizontalBlurUniforms = null;
        }

        if (verticalBlurUniforms != null) {
            verticalBlurUniforms.close();
            verticalBlurUniforms = null;
        }

        if (glowUniforms != null) {
            glowUniforms.close();
            glowUniforms = null;
        }
    }

    public static void renderSimpleGlow(RenderTarget mask, RenderTarget target, int color, float radius, float strength, float outlineWidth) {
        renderEntityGlow(mask, target, color, radius, strength, outlineWidth, false, 0.0F, 0, 0.0F);
    }

    private static void writeBlurUniforms(CommandEncoder encoder, int width, int height, float radius) {
        horizontalBlurUniforms = ensureUniformBuffer(horizontalBlurUniforms, 32, HORIZONTAL_UNIFORM_LABEL);
        verticalBlurUniforms = ensureUniformBuffer(verticalBlurUniforms, 32, VERTICAL_UNIFORM_LABEL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer horizontalData = Std140Builder.onStack(stack, 32).putVec2(1.0F / width, 1.0F / height).putVec2(1.0F, 0.0F).putFloat(radius).align(16).get();
            encoder.writeToBuffer(horizontalBlurUniforms.slice(), horizontalData);
            ByteBuffer verticalData = Std140Builder.onStack(stack, 32).putVec2(1.0F / width, 1.0F / height).putVec2(0.0F, 1.0F).putFloat(radius).align(16).get();
            encoder.writeToBuffer(verticalBlurUniforms.slice(), verticalData);
        }
    }

    private static float redChannel(int color) {
        return (color >>> 16 & 0xFF) / 255.0F;
    }
}
