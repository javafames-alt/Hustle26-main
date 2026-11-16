package morfeov.coliseum.render.hands;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

public final class HandFireRenderer implements AutoCloseable {
    private static final int BLUR_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final BindGroupLayout MASK_LAYOUT = HandsFx.layout(b -> HandsFx.sampler(b, "HandDepthSampler"));
    private static final BindGroupLayout BLUR_LAYOUT = HandsFx.layout(b -> {
        HandsFx.sampler(b, "InputSampler");
        HandsFx.ubo(b, "HandFireBlur");
    });

    private static final RenderPipeline MASK = HandsFx.pipeline("fire_mask", "fire_mask", MASK_LAYOUT, ColorTargetState.DEFAULT);
    private static final RenderPipeline BLUR = HandsFx.pipeline("fire_blur", "fire_blur", BLUR_LAYOUT, ColorTargetState.DEFAULT);
    private static final RenderPipeline SEED = HandsFx.pipeline("hands_fire_seed", "hands_fire_seed", HandsFx.layout(b -> {
        HandsFx.sampler(b, "MaskSampler"); HandsFx.sampler(b, "AppearanceSampler"); HandsFx.ubo(b, "FireSeed");
    }), ColorTargetState.DEFAULT);
    private static final RenderPipeline DISPLACE = HandsFx.pipeline("hands_trail_displace", "hands_trail_displace", HandsFx.layout(b -> {
        HandsFx.sampler(b, "TrailSampler"); HandsFx.ubo(b, "TrailDisplace");
    }), ColorTargetState.DEFAULT);
    private static final RenderPipeline FLAME = HandsFx.pipeline("hands_trail_flame", "hands_trail_flame", HandsFx.layout(b -> {
        HandsFx.sampler(b, "BloomSampler"); HandsFx.ubo(b, "TrailFlame");
    }), new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA));
    private static final RenderPipeline FIRE_COMPOSITE = HandsFx.pipeline("hands_fire_composite", "hands_fire_composite", HandsFx.layout(b -> {
        HandsFx.sampler(b, "TrailSampler"); HandsFx.sampler(b, "MaskSampler"); HandsFx.ubo(b, "FireComposite");
    }), new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA));

    private int sourceWidth, sourceHeight;
    private TextureTarget mask, appearance, blurTemp, glow, seed;
    private final TextureTarget[] history = new TextureTarget[2];
    private GpuBuffer horizontalUniforms, verticalUniforms;
    private GpuBuffer seedUniforms, displaceUniforms, flameUniforms, compositeUniforms;
    private Object level;
    private int readIndex;
    private boolean historyValid;
    private long lastFrameNanos;

    public void render(RenderTarget output, GpuTextureView handDepth, float[] color, boolean itemColors,
                       float fade, float rise, float turbulence, float flicker, float glowStrength) {
        Minecraft mc = Minecraft.getInstance();
        if (output == null || handDepth == null || output.getColorTexture() == null
            || output.width < 2 || output.height < 2 || color[3] <= 0.001f) {
            historyValid = false;
            return;
        }
        long now = System.nanoTime();
        if (level != mc.level || lastFrameNanos == 0 || now - lastFrameNanos > 250_000_000L) historyValid = false;
        level = mc.level;
        ensureTargets(output.width, output.height);
        ensureUniforms();

        float dt = historyValid ? clamp((now - lastFrameNanos) / 1.0E9f, 0.001f, 0.05f) : 1.0f / 60.0f;
        boolean valid = historyValid;
        float time = (now % 100_000_000_000L) / 1_000_000_000f;
        HandsFx.writeUniforms(seedUniforms, 16, b -> b.putVec4(itemColors ? 1 : 0, 0, 0, 0));
        HandsFx.writeUniforms(displaceUniforms, 48, b -> b.putVec2(0, rise * dt).putVec2(mask.width, mask.height)
            .putVec4(fade, time, dt, turbulence).putVec4(flicker, valid ? 1 : 0, 0, 0));
        HandsFx.writeUniforms(flameUniforms, 48, b -> b.putVec4(glowStrength, itemColors ? 1 : 0, 0, dt)
            .putVec4(color[0], color[1], color[2], 1).putVec4(color[0], color[1], color[2], 1));
        HandsFx.writeUniforms(compositeUniforms, 16, b -> b.putVec4(color[3], 0, 0, 0));
        GpuSampler sampler = HandsFx.linear();

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            output.getColorTexture(), appearance.getColorTexture(), 0, 0, 0, 0, 0, output.width, output.height);

        HandsFx.pass("HandShader Fire / mask", MASK, mask.getColorTextureView(),
            pass -> pass.setUniform("HandDepthSampler", handDepth, HandsFx.nearest()));

        HandsFx.pass("Hands Fire / seed", SEED, seed.getColorTextureView(), pass -> {
            pass.setUniform("MaskSampler", mask.getColorTextureView(), sampler);
            pass.setUniform("AppearanceSampler", appearance.getColorTextureView(), sampler);
            pass.setUniform("FireSeed", seedUniforms);
        });

        float radius = 1.0f + clamp(glowStrength, 0.0f, 2.0f) * 1.9f;
        int fw = mask.width, fh = mask.height;
        HandsFx.writeUniforms(horizontalUniforms, BLUR_SIZE, b -> b.putVec4(radius / fw, 0.0f, 0.0f, 0.0f));
        HandsFx.writeUniforms(verticalUniforms, BLUR_SIZE, b -> b.putVec4(0.0f, radius / fh, 0.0f, 0.0f));
        HandsFx.pass("HandShader Fire / glow x", BLUR, blurTemp.getColorTextureView(), pass -> {
            pass.setUniform("HandFireBlur", horizontalUniforms);
            pass.setUniform("InputSampler", seed.getColorTextureView(), sampler);
        });
        HandsFx.pass("HandShader Fire / glow y", BLUR, glow.getColorTextureView(), pass -> {
            pass.setUniform("HandFireBlur", verticalUniforms);
            pass.setUniform("InputSampler", blurTemp.getColorTextureView(), sampler);
        });
        TextureTarget previous = history[readIndex];
        TextureTarget next = history[1 - readIndex];
        HandsFx.pass("Hands Fire / displacement", DISPLACE, next.getColorTextureView(), pass -> {
            pass.setUniform("TrailSampler", previous.getColorTextureView(), sampler);
            pass.setUniform("TrailDisplace", displaceUniforms);
        });
        HandsFx.pass("Hands Fire / flame", FLAME, next.getColorTextureView(), pass -> {
            pass.setUniform("BloomSampler", glow.getColorTextureView(), sampler);
            pass.setUniform("TrailFlame", flameUniforms);
        });
        HandsFx.pass("Hands Fire / composite", FIRE_COMPOSITE, output.getColorTextureView(), pass -> {
            pass.setUniform("MaskSampler", mask.getColorTextureView(), sampler);
            pass.setUniform("TrailSampler", next.getColorTextureView(), sampler);
            pass.setUniform("FireComposite", compositeUniforms);
        });

        readIndex = 1 - readIndex;
        historyValid = true;
        lastFrameNanos = now;
    }

    private void ensureTargets(int width, int height) {
        if (mask != null && sourceWidth == width && sourceHeight == height) return;
        releaseTargets();
        sourceWidth = width;
        sourceHeight = height;

        int divisor = Math.max(2, (height + 719) / 720);
        int w = Math.max(1, (width + divisor - 1) / divisor);
        int h = Math.max(1, (height + divisor - 1) / divisor);
        mask = target("mask", w, h);
        seed = target("seed", w, h);
        appearance = target("appearance", width, height);
        history[0] = target("history-0", w, h);
        history[1] = target("history-1", w, h);
        blurTemp = target("blur-x", w, h);
        glow = target("glow", w, h);
    }

    private static TextureTarget target(String name, int width, int height) {
        return new TextureTarget("HandShader Fire / " + name, width, height, GpuFormat.RGBA8_UNORM, null);
    }

    private void ensureUniforms() {
        if (horizontalUniforms == null) horizontalUniforms = HandsFx.createUniforms("HandShader Fire blur X", BLUR_SIZE);
        if (verticalUniforms == null) verticalUniforms = HandsFx.createUniforms("HandShader Fire blur Y", BLUR_SIZE);
        if (seedUniforms == null) seedUniforms = HandsFx.createUniforms("Hands Fire seed", 16);
        if (displaceUniforms == null) displaceUniforms = HandsFx.createUniforms("Hands Fire displacement", 48);
        if (flameUniforms == null) flameUniforms = HandsFx.createUniforms("Hands Fire flame", 48);
        if (compositeUniforms == null) compositeUniforms = HandsFx.createUniforms("Hands Fire composite", 16);
    }

    private void releaseTargets() {
        if (mask != null) mask.destroyBuffers();
        if (seed != null) seed.destroyBuffers();
        if (appearance != null) appearance.destroyBuffers();
        for (TextureTarget t : history) if (t != null) t.destroyBuffers();
        if (blurTemp != null) blurTemp.destroyBuffers();
        if (glow != null) glow.destroyBuffers();
        mask = appearance = blurTemp = glow = seed = null;
        history[0] = history[1] = null;
        sourceWidth = sourceHeight = 0;
        readIndex = 0;
        historyValid = false;
    }

    @Override
    public void close() {
        releaseTargets();
        if (horizontalUniforms != null) horizontalUniforms.close();
        if (verticalUniforms != null) verticalUniforms.close();
        if (seedUniforms != null) seedUniforms.close();
        if (displaceUniforms != null) displaceUniforms.close();
        if (flameUniforms != null) flameUniforms.close();
        if (compositeUniforms != null) compositeUniforms.close();
        seedUniforms = displaceUniforms = flameUniforms = compositeUniforms = null;
        horizontalUniforms = verticalUniforms = null;
        level = null;
        lastFrameNanos = 0;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
