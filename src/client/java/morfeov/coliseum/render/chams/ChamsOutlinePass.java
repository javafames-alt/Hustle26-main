package morfeov.coliseum.render.chams;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/**
 * Chams «Обводка»: an anti-aliased outline around the whole silhouette of a player
 * (body, armour, cape, cosmetics and 3D skin layers, all captured into one mask).
 *
 * <p>Passes: mask (meshes + depth) -> rows (horizontal distance to the silhouette) -> ring (exact euclidean
 * distance, depth tested against the scene unless «Сквозь стены») -> soft glow (float blur) -> composite.
 */
public final class ChamsOutlinePass {
   private static final int MAX_RADIUS = 24;
   private static final long STARTED = System.nanoTime();
   public static TextureTarget mask;
   public static TextureTarget rows;
   public static TextureTarget ring;
   public static TextureTarget glowHorizontal;
   public static TextureTarget glow;
   public static RenderPipeline rowsPipeline;
   public static RenderPipeline ringPipeline;
   public static RenderPipeline glowBlurPipeline;
   public static RenderPipeline compositePipeline;
   public static GpuBuffer rowsSettings;
   public static GpuBuffer ringSettings;
   public static GpuBuffer horizontalSettings;
   public static GpuBuffer verticalSettings;
   public static GpuBuffer compositeSettings;
   public static int width = -1;
   public static int height = -1;
   public static boolean active;
   public static boolean drawn;
   public static boolean failed;

   private ChamsOutlinePass() {
   }

   public static boolean ready() {
      return active && !failed && mask != null;
   }

   public static void beginFrame(boolean enabled) {
      active = enabled;
      drawn = false;
      if (!enabled) {
         releaseTargets();
         return;
      }
      if (failed) {
         return;
      }
      try {
         RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
         if (main == null || main.getDepthTexture() == null || main.width <= 0 || main.height <= 0) {
            active = false;
            return;
         }
         ensureTargets(main.width, main.height);
         initPipelines();
         RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(mask.getColorTexture(), new Vector4f(0.0F), mask.getDepthTexture(), 0.0);
      } catch (Throwable throwable) {
         fail(throwable);
      }
   }

   public static void render(int colorA, int colorB, int damage, boolean indication, float lineWidth, int style, float speed,
                             float glowStrength, float glowRadius, float fill, boolean throughWalls) {
      boolean pending = active && drawn;
      drawn = false;
      active = false;
      if (!pending || failed || mask == null) {
         return;
      }
      try {
         RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
         if (main == null || main.width != width || main.height != height) {
            return;
         }
         GpuTextureView scene = ChamsDepth.view();
         if (scene == null) {
            return;
         }
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
         GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
         // Widths are tuned for 1080p and scaled with the window so the outline looks the same at any resolution.
         float scale = Math.clamp(height / 1080.0F, 0.75F, 2.5F);
         float line = Math.clamp(lineWidth * scale, 1.0F, MAX_RADIUS - 2.0F);
         int radius = Math.min(MAX_RADIUS, (int) Math.ceil(line + 1.0F));

         ChamsGlowPass.write(encoder, rowsSettings, radius, 0.0F, 0.0F, 0.0F);
         try (RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_outline_rows", rows.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(rowsPipeline));
            pass.setUniform("ChamsOutlineRows", rowsSettings);
            pass.setUniform("MaskSampler", mask.getColorTextureView(), nearest);
            ChamsGlowPass.draw(pass);
         }

         ChamsGlowPass.write(encoder, ringSettings, line, radius, throughWalls ? 1.0F : 0.0F, 0.015F);
         try (RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_outline_ring", ring.getColorTextureView(), Optional.of(new Vector4f(0.0F)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(ringPipeline));
            pass.setUniform("ChamsOutlineRing", ringSettings);
            pass.setUniform("RowSampler", rows.getColorTextureView(), nearest);
            pass.setUniform("MaskSampler", mask.getColorTextureView(), nearest);
            pass.setUniform("MaskDepthSampler", mask.getDepthTextureView(), nearest);
            pass.setUniform("SceneDepthSampler", scene, nearest);
            ChamsGlowPass.draw(pass);
         }

         float strength = Math.clamp(glowStrength, 0.0F, 3.0F);
         boolean halo = strength > 0.001F;
         float gain = 0.0F;
         if (halo) {
            float blur = Math.clamp(glowRadius * scale, 2.0F, 96.0F);
            // A thin line loses most of its energy in a wide blur; normalise so the halo starts at ~1 next to the line.
            gain = 1.0F / Math.clamp(line / (blur / 3.0F * 2.5066F), 0.02F, 1.0F);
            ChamsGlowPass.write(encoder, horizontalSettings, 1.0F / width, 0.0F, blur, 0.0F);
            ChamsGlowPass.write(encoder, verticalSettings, 0.0F, 1.0F / height, blur, 0.0F);
            blur(encoder, ring.getColorTextureView(), glowHorizontal, horizontalSettings);
            blur(encoder, glowHorizontal.getColorTextureView(), glow, verticalSettings);
         }

         float time = (float) (((System.nanoTime() - STARTED) / 1.0E9) % 3600.0) * Math.max(0.0F, speed);
         try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(
               compositeSettings.slice(),
               Std140Builder.onStack(stack, 80)
                  .putVec4(red(colorA), green(colorA), blue(colorA), alpha(colorA))
                  .putVec4(red(colorB), green(colorB), blue(colorB), 1.0F)
                  .putVec4(red(damage), green(damage), blue(damage), indication ? 1.0F : 0.0F)
                  .putVec4(Math.clamp(fill, 0.0F, 1.0F), strength * gain, time, style)
                  .putVec4((float) width / height, line, 0.0F, 0.0F)
                  .get()
            );
         }
         try (RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_outline", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            pass.setUniform("ChamsOutline", compositeSettings);
            pass.setUniform("RingSampler", ring.getColorTextureView(), nearest);
            pass.setUniform("GlowSampler", halo ? glow.getColorTextureView() : ring.getColorTextureView(), linear);
            ChamsGlowPass.draw(pass);
         }
      } catch (Throwable throwable) {
         fail(throwable);
      }
   }

   private static void blur(CommandEncoder encoder, GpuTextureView source, TextureTarget destination, GpuBuffer settings) {
      try (RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_outline_glow", destination.getColorTextureView(), Optional.empty())) {
         pass.setPipeline(RenderSystem.getCompiledPipeline(glowBlurPipeline));
         pass.setUniform("ChamsBlurDirection", settings);
         pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         ChamsGlowPass.draw(pass);
      }
   }

   private static void initPipelines() {
      ChamsGlowPass.initShaderPipelines();
      if (rowsPipeline == null) {
         rowsPipeline = ChamsGlowPass.screenPipeline("chams_outline_rows", "ChamsOutlineRows", null, "MaskSampler");
         ringPipeline = ChamsGlowPass.screenPipeline(
            "chams_outline_ring", "ChamsOutlineRing", null, "RowSampler", "MaskSampler", "MaskDepthSampler", "SceneDepthSampler"
         );
         // Same gaussian as the glow mode, but into half-float targets so wide halos stay smooth (no banding).
         glowBlurPipeline = floatPipeline("chams_outline_glow", "chams_glow_blur", "ChamsBlurDirection", "InSampler");
         compositePipeline = ChamsGlowPass.screenPipeline(
            "chams_outline_composite", "ChamsOutline", new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA), "RingSampler", "GlowSampler"
         );
      }
      if (rowsSettings == null) {
         rowsSettings = ChamsGlowPass.settings("outline rows");
         ringSettings = ChamsGlowPass.settings("outline ring");
         horizontalSettings = ChamsGlowPass.settings("outline horizontal glow");
         verticalSettings = ChamsGlowPass.settings("outline vertical glow");
         compositeSettings = RenderSystem.getDevice().createBuffer(() -> "Coliseum Chams outline composite", 136, 80L);
      }
   }

   private static RenderPipeline floatPipeline(String name, String fragment, String uniform, String... samplers) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(ChamsGlowPass.id("pipeline/" + name))
         .withVertexShader(ChamsGlowPass.id("core/chams_blur"))
         .withFragmentShader(ChamsGlowPass.id("core/" + fragment))
         .withBindGroupLayout(BindGroupLayout.builder().withUniform(uniform, UniformType.UNIFORM_BUFFER).build())
         .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, 15))
         .withDepthStencilState(Optional.empty())
         .withCull(false)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
      for (String sampler : samplers) {
         builder.withBindGroupLayout(BindGroupLayout.builder().withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER).build());
      }
      return RenderPipelines.register(builder.build());
   }

   private static void ensureTargets(int w, int h) {
      if (mask != null && width == w && height == h) {
         return;
      }
      releaseTargets();
      mask = new TextureTarget("coliseum_chams_outline_mask", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
      rows = new TextureTarget("coliseum_chams_outline_rows", w, h, GpuFormat.RGBA8_UNORM, null);
      ring = new TextureTarget("coliseum_chams_outline_ring", w, h, GpuFormat.RGBA8_UNORM, null);
      int halfWidth = Math.max(1, (w + 1) / 2);
      int halfHeight = Math.max(1, (h + 1) / 2);
      glowHorizontal = new TextureTarget("coliseum_chams_outline_glow_horizontal", halfWidth, halfHeight, GpuFormat.RGBA16_FLOAT, null);
      glow = new TextureTarget("coliseum_chams_outline_glow", halfWidth, halfHeight, GpuFormat.RGBA16_FLOAT, null);
      width = w;
      height = h;
   }

   public static void cancelFrame() {
      drawn = false;
      active = false;
      releaseTargets();
   }

   public static void releaseTargets() {
      for (TextureTarget target : new TextureTarget[]{mask, rows, ring, glowHorizontal, glow}) {
         if (target != null) {
            target.destroyBuffers();
         }
      }
      mask = null;
      rows = null;
      ring = null;
      glowHorizontal = null;
      glow = null;
      width = -1;
      height = -1;
   }

   public static void close() {
      cancelFrame();
      for (GpuBuffer buffer : new GpuBuffer[]{rowsSettings, ringSettings, horizontalSettings, verticalSettings, compositeSettings}) {
         if (buffer != null) {
            buffer.close();
         }
      }
      rowsSettings = null;
      ringSettings = null;
      horizontalSettings = null;
      verticalSettings = null;
      compositeSettings = null;
   }

   private static void fail(Throwable throwable) {
      failed = true;
      drawn = false;
      active = false;
      throwable.printStackTrace();
      releaseTargets();
   }

   private static float red(int color) {
      return (color >> 16 & 0xFF) / 255.0F;
   }

   private static float green(int color) {
      return (color >> 8 & 0xFF) / 255.0F;
   }

   private static float blue(int color) {
      return (color & 0xFF) / 255.0F;
   }

   private static float alpha(int color) {
      return (color >>> 24) / 255.0F;
   }
}
