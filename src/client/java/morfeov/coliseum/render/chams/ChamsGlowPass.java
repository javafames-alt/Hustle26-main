package morfeov.coliseum.render.chams;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;



public final class ChamsGlowPass {
   public static final Map<Identifier, RenderType> SURFACE_TYPES = new HashMap<>();
   public static TextureTarget body;
   public static TextureTarget armor;
   public static TextureTarget visible;
   public static TextureTarget horizontal;
   public static TextureTarget blurred;
   public static TextureTarget wideHorizontal;
   public static TextureTarget wideBlurred;
   public static GpuTexture terrainDepth;
   public static GpuTextureView terrainDepthView;
   public static RenderPipeline maskPipeline;
   public static RenderPipeline surfacePipeline;
   public static RenderPipeline visibilityPipeline;
   public static RenderPipeline blurPipeline;
   public static RenderPipeline compositePipeline;
   public static GpuBuffer visibilitySettings;
   public static GpuBuffer horizontalSettings;
   public static GpuBuffer verticalSettings;
   public static GpuBuffer wideHorizontalSettings;
   public static GpuBuffer wideVerticalSettings;
   public static GpuBuffer compositeSettings;
   public static GpuBuffer vertex;
   public static int width = -1;
   public static int height = -1;
   public static boolean active;
   public static boolean drawn;
   public static boolean failed;

   public static boolean ready() {
      return active && !failed && body != null;
   }

   public static void beginFrame(boolean enabled) {
      active = enabled;
      drawn = false;
      if (!enabled) {
         releaseTargets();
      } else if (!failed) {
         try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (main == null || main.getDepthTexture() == null || main.width <= 0 || main.height <= 0) {
               active = false;
               return;
            }

            ensureTargets(main.width, main.height);
            initPipelines();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(body.getColorTexture(), new Vector4f(0.0F), body.getDepthTexture(), 0.0);
            encoder.clearColorAndDepthTextures(armor.getColorTexture(), new Vector4f(0.0F), armor.getDepthTexture(), 0.0);
            encoder.copyTextureToTexture(main.getDepthTexture(), terrainDepth, 0, 0, 0, 0, 0, width, height);
         } catch (Throwable throwable) {
            fail(throwable);
         }
      }
   }

   public static RenderType surfaceType(Identifier texture) {
      return !ready()
         ? RenderTypes.entityTranslucentEmissive(texture)
         : SURFACE_TYPES.computeIfAbsent(
            texture,
            id -> RenderType.create(
               "coliseum_chams_fill",
               RenderSetup.builder(surfacePipeline).withTexture("Sampler0", id).setOutline(OutlineProperty.AFFECTS_OUTLINE).sortOnUpload().createRenderSetup()
            )
         );
   }

   public static void render(float opacity, float radius, float strength, boolean throughWalls, boolean throughArmor) {
      boolean pending = active && drawn;
      drawn = false;
      active = false;
      if (pending && !failed && visible != null) {
         try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (main == null || main.width != width || main.height != height) {
               return;
            }

            GpuTextureView sceneDepth = ChamsDepth.view();
            if (sceneDepth == null) {
               return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            write(encoder, visibilitySettings, throughWalls ? 1.0F : 0.0F, throughArmor ? 1.0F : 0.0F, 0.0F, 0.0F);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_visible_mask", visible.getColorTextureView(), Optional.of(new Vector4f(0.0F)));

            try {
               pass.setPipeline(RenderSystem.getCompiledPipeline(visibilityPipeline));
               pass.setUniform("ChamsVisibility", visibilitySettings);
               pass.setUniform("BodySampler", body.getColorTextureView(), nearest);
               pass.setUniform("BodyDepthSampler", body.getDepthTextureView(), nearest);
               pass.setUniform("ArmorDepthSampler", armor.getDepthTextureView(), nearest);
               pass.setUniform("TerrainDepthSampler", terrainDepthView, nearest);
               pass.setUniform("SceneDepthSampler", sceneDepth, nearest);
               draw(pass);
            } catch (Throwable var17) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var15) {
                     var17.addSuppressed(var15);
                  }
               }

               throw var17;
            }

            if (pass != null) {
               pass.close();
            }

            if (strength > 0.0F) {
               float safeRadius = Math.clamp(radius, 2.0F, 64.0F);
               float closeRadius = Math.max(2.0F, safeRadius * 0.35F);
               write(encoder, horizontalSettings, 1.0F / width, 0.0F, closeRadius, 0.0F);
               write(encoder, verticalSettings, 0.0F, 1.0F / height, closeRadius, 0.0F);
               blur(encoder, visible.getColorTextureView(), horizontal, horizontalSettings);
               blur(encoder, horizontal.getColorTextureView(), blurred, verticalSettings);
               write(encoder, wideHorizontalSettings, 1.0F / width, 0.0F, safeRadius, 0.0F);
               write(encoder, wideVerticalSettings, 0.0F, 1.0F / height, safeRadius, 0.0F);
               blur(encoder, visible.getColorTextureView(), wideHorizontal, wideHorizontalSettings);
               blur(encoder, wideHorizontal.getColorTextureView(), wideBlurred, wideVerticalSettings);
            }

            write(
               encoder,
               compositeSettings,
               Math.clamp(opacity, 0.0F, 1.0F),
               Math.clamp(strength, 0.0F, 3.0F),
               !throughWalls && !throughArmor ? 0.0F : 1.0F,
               0.0F
            );
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            RenderPass passx = encoder.createRenderPass(() -> "coliseum/chams_glow", main.getColorTextureView(), Optional.empty());

            try {
               passx.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
               passx.setUniform("ChamsComposite", compositeSettings);
               passx.setUniform("VisibleSampler", visible.getColorTextureView(), nearest);
               passx.setUniform("GlowSampler", strength > 0.0F ? blurred.getColorTextureView() : visible.getColorTextureView(), linear);
               passx.setUniform("WideGlowSampler", strength > 0.0F ? wideBlurred.getColorTextureView() : visible.getColorTextureView(), linear);
               passx.setUniform("BodyDepthSampler", body.getDepthTextureView(), nearest);
               passx.setUniform("SceneDepthSampler", sceneDepth, nearest);
               draw(passx);
            } catch (Throwable var16) {
               if (passx != null) {
                  try {
                     passx.close();
                  } catch (Throwable var14) {
                     var16.addSuppressed(var14);
                  }
               }

               throw var16;
            }

            if (passx != null) {
               passx.close();
            }
         } catch (Throwable throwable) {
            fail(throwable);
         }
      }
   }

   public static void blur(CommandEncoder encoder, GpuTextureView source, TextureTarget destination, GpuBuffer settings) {
      RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_gaussian_blur", destination.getColorTextureView(), Optional.empty());

      try {
         pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
         pass.setUniform("ChamsBlurDirection", settings);
         pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   public static void draw(RenderPass pass) {
      pass.draw(6, 1, 0, 0);
   }

   public static void write(CommandEncoder encoder, GpuBuffer buffer, float x, float y, float z, float w) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         encoder.writeToBuffer(buffer.slice(), Std140Builder.onStack(stack, 16).putVec4(x, y, z, w).get());
      } catch (Throwable var10) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void initShaderPipelines() {
      if (maskPipeline == null) {
         maskPipeline = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
               .withLocation(id("pipeline/chams_glow_mask"))
               .withVertexShader(id("core/chams_fill"))
               .withFragmentShader(id("core/chams_glow_mask"))
               .withShaderDefine("NO_CARDINAL_LIGHTING")
               .withShaderDefine("NO_OVERLAY")
               .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15))
               .withDepthStencilState(DepthStencilState.DEFAULT)
               .withCull(false)
               .build()
         );
         surfacePipeline = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
               .withLocation(id("pipeline/chams_fill"))
               .withVertexShader(id("core/chams_fill"))
               .withFragmentShader(id("core/chams_fill"))
               .withShaderDefine("NO_CARDINAL_LIGHTING")
               .withShaderDefine("NO_OVERLAY")
               .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
               .withDepthStencilState(DepthStencilState.DEFAULT)
               .withCull(false)
               .build()
         );
         visibilityPipeline = screenPipeline(
            "chams_visible", "ChamsVisibility", null, "BodySampler", "BodyDepthSampler", "ArmorDepthSampler", "TerrainDepthSampler", "SceneDepthSampler"
         );
         blurPipeline = screenPipeline("chams_glow_blur", "ChamsBlurDirection", null, "InSampler");
         compositePipeline = screenPipeline(
            "chams_glow_composite",
            "ChamsComposite",
            new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA),
            "VisibleSampler",
            "GlowSampler",
            "WideGlowSampler",
            "BodyDepthSampler",
            "SceneDepthSampler"
         );
      }
   }
   public static void initPipelines() {
      initShaderPipelines();
      if (visibilitySettings == null) {
         visibilitySettings = settings("visibility");
         horizontalSettings = settings("horizontal blur");
         verticalSettings = settings("vertical blur");
         wideHorizontalSettings = settings("wide horizontal blur");
         wideVerticalSettings = settings("wide vertical blur");
         compositeSettings = settings("composite");
         ByteBuffer bytes = MemoryUtil.memAlloc(4);

         try {
            bytes.putInt(0).flip();
            vertex = RenderSystem.getDevice().createBuffer(() -> "Coliseum Chams glow vertex", 32, bytes);
         } finally {
            MemoryUtil.memFree(bytes);
         }
      }
   }

   public static RenderPipeline screenPipeline(String name, String uniform, BlendFunction blend, String... samplers) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(id("pipeline/" + name))
         .withVertexShader(id("core/chams_blur"))
         .withFragmentShader(id("core/" + name))
         .withBindGroupLayout(BindGroupLayout.builder().withUniform(uniform, UniformType.UNIFORM_BUFFER).build())
         .withColorTargetState(blend == null ? new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15) : new ColorTargetState(blend))
         .withDepthStencilState(Optional.empty())
         .withCull(false)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);

      for (String sampler : samplers) {
         builder.withBindGroupLayout(BindGroupLayout.builder().withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER).build());
      }

      return RenderPipelines.register(builder.build());
   }

   public static GpuBuffer settings(String name) {
      return RenderSystem.getDevice().createBuffer(() -> "Coliseum Chams " + name, 136, 16L);
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("coliseum", path);
   }

   public static void ensureTargets(int w, int h) {
      if (body == null || width != w || height != h) {
         releaseTargets();
         body = new TextureTarget("coliseum_chams_body", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
         armor = new TextureTarget("coliseum_chams_armor", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
         visible = new TextureTarget("coliseum_chams_visible", w, h, GpuFormat.RGBA8_UNORM, null);
         horizontal = new TextureTarget("coliseum_chams_horizontal", w, h, GpuFormat.RGBA8_UNORM, null);
         blurred = new TextureTarget("coliseum_chams_blurred", horizontal.width, horizontal.height, GpuFormat.RGBA8_UNORM, null);
         wideHorizontal = new TextureTarget("coliseum_chams_wide_horizontal", Math.max(1, (w + 1) / 2), Math.max(1, (h + 1) / 2), GpuFormat.RGBA8_UNORM, null);
         wideBlurred = new TextureTarget("coliseum_chams_wide_blurred", wideHorizontal.width, wideHorizontal.height, GpuFormat.RGBA8_UNORM, null);
         terrainDepth = RenderSystem.getDevice().createTexture(() -> "Coliseum Chams terrain depth", 5, GpuFormat.D32_FLOAT, w, h, 1, 1);
         terrainDepthView = RenderSystem.getDevice().createTextureView(terrainDepth);
         width = w;
         height = h;
      }
   }

   public static void cancelFrame() {
      drawn = false;
      active = false;
      releaseTargets();
   }

   public static void releaseTargets() {
      for (TextureTarget target : new TextureTarget[]{body, armor, visible, horizontal, blurred, wideHorizontal, wideBlurred}) {
         if (target != null) {
            target.destroyBuffers();
         }
      }

      wideBlurred = null;
      wideHorizontal = null;
      blurred = null;
      horizontal = null;
      visible = null;
      armor = null;
      body = null;
      if (terrainDepthView != null) {
         terrainDepthView.close();
      }

      if (terrainDepth != null) {
         terrainDepth.close();
      }

      terrainDepthView = null;
      terrainDepth = null;
      height = -1;
      width = -1;
   }

   public static void fail(Throwable throwable) {
      failed = true;
      drawn = false;
      active = false;
      throwable.printStackTrace();
      releaseTargets();
   }
}
