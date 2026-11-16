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
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
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
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;



public final class ChamsBlurPass {
   public static TextureTarget mask;
   public static GpuTexture scene;
   public static GpuTextureView sceneView;
   public static RenderPipeline maskPipeline;
   public static RenderPipeline blurPipeline;
   public static final Map<Identifier, RenderType> TYPES = new HashMap<>();
   public static GpuBuffer settings;
   public static GpuBuffer vertex;
   public static int width = -1;
   public static int height = -1;
   public static boolean active;
   public static boolean drawn;
   public static boolean failed;

   public static boolean ready() {
      return active && !failed && mask != null && scene != null;
   }

   public static void beginFrame(boolean enabled) {
      active = enabled;
      drawn = false;
      if (!enabled) {
         clear();
      } else if (!failed) {
         try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (main == null || main.getDepthTexture() == null || main.width <= 0 || main.height <= 0) {
               active = false;
               return;
            }

            ensure(main.width, main.height);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(mask.getColorTexture(), new Vector4f(0.0F), mask.getDepthTexture(), 0.0);
         } catch (Throwable throwable) {
            fail(throwable);
         }
      }
   }

   public static void mark() {
      if (ready()) {
         drawn = true;
      }
   }

   public static RenderType maskType(Identifier texture) {
      if (!ready()) {
         return RenderTypes.entityTranslucentEmissive(texture);
      }

      try {
         initMaskPipeline();

         return TYPES.computeIfAbsent(
            texture,
            id -> RenderType.create(
               "coliseum_chams_blur_mask", RenderSetup.builder(maskPipeline).withTexture("Sampler0", id).createRenderSetup()
            )
         );
      } catch (Throwable throwable) {
         fail(throwable);
         return RenderTypes.entityTranslucentEmissive(texture);
      }
   }

   public static void initMaskPipeline() {
if (maskPipeline == null) {
            maskPipeline = RenderPipeline.builder(new Snippet[]{RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
               .withLocation(Identifier.fromNamespaceAndPath("coliseum", "pipeline/chams_blur_mask"))
               .withVertexShader(Identifier.fromNamespaceAndPath("coliseum", "core/chams_fill"))
               .withFragmentShader(Identifier.fromNamespaceAndPath("coliseum", "core/chams_fill"))
               .withShaderDefine("NO_CARDINAL_LIGHTING")
               .withShaderDefine("NO_OVERLAY")
               .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
               .withCull(false)
               .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
               .build();
            RenderPipelines.register(maskPipeline);
         }

   }

   public static void render(int color, float radius, float tintAlpha, boolean mirror, boolean throughWalls) {
      boolean pending = active && drawn;
      drawn = false;
      active = false;
      if (pending && !failed && mask != null && scene != null) {
         try {
            initPipeline();
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (main == null || main.width != width || main.height != height) {
               return;
            }

            GpuTextureView sceneDepth = ChamsDepth.view();
            if (sceneDepth == null) {
               return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getColorTexture(), scene, 0, 0, 0, 0, 0, width, height);
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            MemoryStack stack = MemoryStack.stackPush();

            try {
               encoder.writeToBuffer(
                  settings.slice(),
                  Std140Builder.onStack(stack, 32).putVec4(r, g, b, tintAlpha).putVec4(radius, mirror ? 1.0F : 0.0F, throughWalls ? 1.0F : 0.0F, 0.0F).get()
               );
            } catch (Throwable var20) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var18) {
                     var20.addSuppressed(var18);
                  }
               }

               throw var20;
            }

            if (stack != null) {
               stack.close();
            }

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            RenderPass pass = encoder.createRenderPass(() -> "coliseum/chams_blur", main.getColorTextureView(), Optional.empty());

            try {
               pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
               pass.setUniform("ChamsBlur", settings);
               pass.setUniform("SceneSampler", sceneView, sampler);
               pass.setUniform("MaskSampler", mask.getColorTextureView(), sampler);
               pass.setUniform("SceneDepthSampler", sceneDepth, depthSampler);
               pass.setUniform("MaskDepthSampler", mask.getDepthTextureView(), depthSampler);
               pass.draw(6, 1, 0, 0);
            } catch (Throwable var19) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var17) {
                     var19.addSuppressed(var17);
                  }
               }

               throw var19;
            }

            if (pass != null) {
               pass.close();
            }
         } catch (Throwable throwable) {
            fail(throwable);
         }
      }
   }

   public static void cancelFrame() {
      drawn = false;
      active = false;
   }

   public static void clear() {
      failed = false;
      drawn = false;
      active = false;
      if (mask != null) {
         mask.destroyBuffers();
         mask = null;
      }

      if (sceneView != null) {
         sceneView.close();
         sceneView = null;
      }

      if (scene != null) {
         scene.close();
         scene = null;
      }

      TYPES.clear();
      height = -1;
      width = -1;
   }

   public static void initShaderPipeline() {
      if (blurPipeline == null) {
         Identifier shader = Identifier.fromNamespaceAndPath("coliseum", "core/chams_blur");
         blurPipeline = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[0])
               .withLocation(Identifier.fromNamespaceAndPath("coliseum", "pipeline/chams_blur"))
               .withVertexShader(shader)
               .withFragmentShader(shader)
               .withBindGroupLayout(
                  BindGroupLayout.builder()
                     .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                     .withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                     .withUniform("SceneDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                     .withUniform("MaskDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                     .withUniform("ChamsBlur", UniformType.UNIFORM_BUFFER)
                     .build()
               )
               .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ZERO)))
               .withDepthStencilState(Optional.empty())
               .withCull(false)
               .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
               .build()
         );
      }
   }
   public static void initPipeline() {
      initShaderPipeline();
      if (settings == null) {
         settings = RenderSystem.getDevice().createBuffer(() -> "Coliseum Chams blur settings", 136, 32L);
      }
   }

   public static void ensure(int w, int h) {
      if (mask == null || width != w || height != h || scene == null || scene.isClosed()) {
         if (mask != null) {
            mask.destroyBuffers();
         }

         if (sceneView != null) {
            sceneView.close();
         }

         if (scene != null) {
            scene.close();
         }

         mask = new TextureTarget("coliseum_chams_blur_mask", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
         scene = RenderSystem.getDevice().createTexture(() -> "Coliseum Chams scene", 5, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
         sceneView = RenderSystem.getDevice().createTextureView(scene);
         width = w;
         height = h;
      }
   }

   public static GpuBuffer vertex() {
      if (vertex != null && !vertex.isClosed()) {
         return vertex;
      }

      ByteBuffer bytes = MemoryUtil.memAlloc(4);
      bytes.putInt(0).flip();

      try {
         vertex = RenderSystem.getDevice().createBuffer(() -> "Coliseum Chams vertex", 32, bytes);
      } finally {
         MemoryUtil.memFree(bytes);
      }

      return vertex;
   }

   public static void fail(Throwable throwable) {
      failed = true;
      drawn = false;
      active = false;
      throwable.printStackTrace();
   }
}
