package morfeov.coliseum.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import morfeov.coliseum.render.hands.HandsFx;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Separable Gaussian backdrop blur, composited only inside the GUI's rounded surfaces. */
public final class FigmaBackdropBlur implements AutoCloseable {
    public static final FigmaBackdropBlur INSTANCE = new FigmaBackdropBlur();
    private static final int STYLE_BYTES = BackdropStyle.BYTES;
    private static final BindGroupLayout BLUR_LAYOUT = HandsFx.layout(b -> {
        HandsFx.sampler(b, "InputSampler");
        HandsFx.ubo(b, "BlurStyle");
    });
    private static final BindGroupLayout COMPOSITE_LAYOUT = HandsFx.layout(b -> {
        HandsFx.sampler(b, "OriginalSampler");
        HandsFx.sampler(b, "BlurredSampler");
        HandsFx.ubo(b, "SurfaceStyle");
        HandsFx.ubo(b, "GlassStyle");
    });
    private static final RenderPipeline BLUR = pipeline("figma_blur", BLUR_LAYOUT);
    private static final BindGroupLayout BACKGROUND_LAYOUT = HandsFx.layout(b -> HandsFx.ubo(b, "MenuBackgroundData"));
    private static final RenderPipeline BACKGROUND = pipeline("menu_glass_background", BACKGROUND_LAYOUT);
    private GpuBuffer backgroundStyle;
    private static final RenderPipeline COMPOSITE = pipeline("figma_backdrop", COMPOSITE_LAYOUT);
    private TextureTarget original, horizontal, blurred;
    private GpuBuffer blurX, blurY, surfaces, glassStyle;
    private final long animationStart = System.nanoTime();

    private static RenderPipeline pipeline(String name, BindGroupLayout layout) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("coliseum", "pipeline/gui/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("coliseum", "core/" + name))
            .withBindGroupLayout(layout).withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.empty()).withCull(false).build();
    }

    public void render(RenderTarget output, ClickGuiScreen screen) {
        var options = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.ClickGui.class);
        if (options != null && options.shaderBackground.enabled() && output.getColorTextureView() != null) {
            if (backgroundStyle == null) backgroundStyle = HandsFx.createUniforms("ClickGUI nebula background", 16);
            float time = (System.nanoTime() - animationStart) / 1_000_000_000f * screen.glassShaderSpeed();
            HandsFx.writeUniforms(backgroundStyle, 16, b -> b.putVec4(output.width, output.height, time, .9f * screen.backdropOpacity()));
            HandsFx.pass("ClickGUI / nebula background", BACKGROUND, output.getColorTextureView(),
                pass -> pass.setUniform("MenuBackgroundData", backgroundStyle));
        }
        if (!screen.blurEnabled()) return;
        float[][] rectangles = screen.backdropSurfaces();
        render(output, screen.width, screen.height, rectangles, screen.backdropRadius(), screen.blurStrength(), screen.themeBlur() ? .14f * screen.backdropOpacity() : 0,
            screen.glassShaderMode(), screen.glassShaderStrength(), screen.glassShaderSpeed(), screen.backdropOpacity());
    }

    public void render(RenderTarget output, int guiWidth, int guiHeight, float[][] rectangles, float radius, float strength, float tint) {
        render(output, guiWidth, guiHeight, rectangles, radius, strength, tint, 0, 0, 1, 1);
    }

    private void render(RenderTarget output, int guiWidth, int guiHeight, float[][] rectangles, float radius,
                        float strength, float tint, int mode, float shaderStrength, float speed, float opacity) {
        if (rectangles.length == 0) return;
        if (guiWidth <= 0 || guiHeight <= 0) return;
        if (output.getColorTexture() == null || output.width < 2 || output.height < 2) return;
        double guiPerPixel = Math.max(guiWidth / (double)output.width, guiHeight / (double)output.height);
        BackdropRegion compositeRegion = BackdropRegion.of(guiWidth, guiHeight, rectangles, output.width, output.height, 2 * guiPerPixel);
        if (compositeRegion.isEmpty()) return;
        ensureTargets(output.width, output.height);
        // The reference retains scenery detail; 15px is the blur extent, not Gaussian sigma.
        float sigma = strength * output.width / 1920f;
        double filterPixel = Math.max(output.width / (double)horizontal.width, output.height / (double)horizontal.height);
        // Composite samples the vertical target bilinearly; that pass in turn
        // samples horizontal within +/-3 sigma. Include both footprints so
        // no sample can touch an unwritten texel left over from an older frame.
        BackdropRegion verticalRegion = BackdropRegion.of(guiWidth, guiHeight, rectangles, blurred.width, blurred.height,
            (2 * filterPixel + 2) * guiPerPixel);
        BackdropRegion horizontalRegion = BackdropRegion.of(guiWidth, guiHeight, rectangles, horizontal.width, horizontal.height,
            (3 * Math.abs(sigma) + 4 * filterPixel + 4) * guiPerPixel);
        if (verticalRegion.isEmpty() || horizontalRegion.isEmpty()) return;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            output.getColorTexture(), original.getColorTexture(), 0, 0, 0, 0, 0, output.width, output.height);
        HandsFx.writeUniforms(blurX, 16, b -> b.putVec4(1f / output.width, 0, sigma, 0));
        HandsFx.writeUniforms(blurY, 16, b -> b.putVec4(0, 1f / output.height, sigma, 0));
        var sampler = HandsFx.linear();
        HandsFx.pass("ClickGUI / horizontal blur", BLUR, horizontal.getColorTextureView(), pass -> {
            horizontalRegion.apply(pass, horizontal.height);
            pass.setUniform("BlurStyle", blurX);
            pass.setUniform("InputSampler", original.getColorTextureView(), sampler);
        });
        HandsFx.pass("ClickGUI / vertical blur", BLUR, blurred.getColorTextureView(), pass -> {
            verticalRegion.apply(pass, blurred.height);
            pass.setUniform("BlurStyle", blurY);
            pass.setUniform("InputSampler", horizontal.getColorTextureView(), sampler);
        });
        float time = (System.nanoTime() - animationStart) / 1_000_000_000f * speed;
        HandsFx.writeUniforms(glassStyle, 16, b -> b.putVec4(time, mode, shaderStrength, Math.clamp(opacity, 0, 1)));
        float[] style = BackdropStyle.pack(guiWidth, guiHeight, rectangles, radius, FigmaGuiRenderer.accent(), tint);
        HandsFx.writeUniforms(surfaces, STYLE_BYTES, b -> {
            for (int i = 0; i < style.length; i += 4) b.putVec4(style[i], style[i + 1], style[i + 2], style[i + 3]);
        });
        HandsFx.pass("ClickGUI / rounded backdrop composite", COMPOSITE, output.getColorTextureView(), pass -> {
            compositeRegion.apply(pass, output.height);
            pass.setUniform("SurfaceStyle", surfaces);
            pass.setUniform("GlassStyle", glassStyle);
            pass.setUniform("OriginalSampler", original.getColorTextureView(), sampler);
            pass.setUniform("BlurredSampler", blurred.getColorTextureView(), sampler);
        });
    }

    private void ensureTargets(int width, int height) {
        var optimization = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Optimization.class);
        int divisor = optimization != null && optimization.fastGuiBlur() ? 4 : 2;
        int blurWidth = Math.max(1, width / divisor), blurHeight = Math.max(1, height / divisor);
        if (original != null && original.width == width && original.height == height
                && horizontal.width == blurWidth && horizontal.height == blurHeight) return;
        close();
        original = new TextureTarget("ClickGUI original", width, height, GpuFormat.RGBA8_UNORM, null);
        horizontal = new TextureTarget("ClickGUI blur X", blurWidth, blurHeight, GpuFormat.RGBA8_UNORM, null);
        blurred = new TextureTarget("ClickGUI blur Y", blurWidth, blurHeight, GpuFormat.RGBA8_UNORM, null);
        blurX = HandsFx.createUniforms("ClickGUI blur X", 16);
        blurY = HandsFx.createUniforms("ClickGUI blur Y", 16);
        glassStyle = HandsFx.createUniforms("ClickGUI glass animation", 16);
        surfaces = HandsFx.createUniforms("ClickGUI surfaces", STYLE_BYTES);
    }

    @Override public void close() {
        if (backgroundStyle != null) backgroundStyle.close();
        backgroundStyle = null;
        if (original != null) original.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (blurred != null) blurred.destroyBuffers();
        if (blurX != null) blurX.close();
        if (blurY != null) blurY.close();
        if (surfaces != null) surfaces.close();
        if (glassStyle != null) glassStyle.close();
        original = horizontal = blurred = null;
        blurX = blurY = surfaces = glassStyle = null;
    }
}
