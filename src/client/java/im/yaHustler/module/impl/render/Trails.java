package im.yaHustler.module.impl.render;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import im.yaHustler.module.Category;
import im.yaHustler.render.coliseimvisual.PortedModule;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.render.coliseimvisual.GlowQuadRenderer;
import im.yaHustler.render.coliseimvisual.VisualColors;
import im.yaHustler.render.coliseimvisual.VisualResources;
import im.yaHustler.render.coliseimvisual.TrailHistory;
import im.yaHustler.render.coliseimvisual.VisualTheme;
import im.yaHustler.render.coliseimvisual.SharedVertexBuffer;
import im.yaHustler.render.coliseimvisual.WorldRenderFrame;
import im.yaHustler.render.coliseimvisual.VisualThemeColor;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@ModuleInfo(id="trails", name="Trails", category=Category.RENDER, description="Следы движения: Призраки")
public class Trails extends PortedModule {
   private static final Identifier ID38 = VisualResources.image(Trails.TARGETESP_GLOW);
   private final NumberSetting sliderSetting438;
   private final NumberSetting sliderSetting439;
   private final NumberSetting sliderSetting440;
   private final TrailHistory systemClass175;
   private ClientLevel clientLevel22;
   private final RenderPipeline renderPipeline74;
   private static final String TRAILS = "Trails";
   private static final String ПРИЗРАКИ = "Призраки";
   private static final String ДЛИНА_ШЛЕЙФА = "Длина шлейфа";
   private static final float VALUE6316 = 25.0F;
   private static final float VALUE6317 = 10.0F;
   private static final float VALUE6318 = 50.0F;
   private static final String РАЗМЕР_ЧАСТИЦ = "Размер частиц";
   private static final float VALUE6319 = 0.6F;
   private static final float VALUE6320 = 0.3F;
   private static final float VALUE6321 = 1.2F;
   private static final float VALUE6322 = 0.05F;
   private static final String СКОРОСТЬ_ПРИЗРАКОВ = "Скорость призраков";
   private static final float VALUE6323 = 3.0F;
   private static final float VALUE6324 = 10.0F;
   private static final float VALUE6325 = 0.5F;
   private static final String HUSTLE_TRAILS_GHOSTS = "hustle263/coliseimvisual/trails_ghosts";
   private static final String TARGETESP_GLOW = "targetesp/glow";
   private static final String TRAILS1 = "trails";
   private static final String TRAILS_VERTICES = "trailsVertices";
   private static final String SAMPLER0_36 = "Sampler0";
   private static final double VALUE6341 = 20.0;
   private static final double VALUE6342 = 2.0;
   private static final float VALUE6343 = 0.65F;
   private static final double VALUE6344 = 40.0;
   private static final float VALUE6345 = 0.6F;
   private static final float VALUE6346 = 1.25F;
   private static final double VALUE6347 = 0.8;
   private static final float VALUE6348 = 220.0F;
   private static final double VALUE6349 = 0.015;
   private static final double VALUE6350 = 0.5;
   private static final double VALUE6351 = 50.0;
   private static final double VALUE6352 = 360.0;
   private static final double VALUE6353 = 0.1;
   private static final double VALUE6354 = 1.7;
   private static final double VALUE6355 = 1.8;
   private static final double VALUE6356 = 1.7;
   private static final float VALUE6357 = 0.08F;
   private static final float VALUE6358 = 0.16F;
   private static final float VALUE6359 = 0.3F;
   private static final float VALUE6360 = 0.56F;
   private static final float VALUE6361 = 12.0F;
   private static final float VALUE6362 = 12.0F;
   private static final float VALUE6363 = 12.0F;
   private static final float VALUE6364 = 0.05F;
   private static final double VALUE6365 = 100.0;
   private static final double VALUE6366 = 360.0;
   private static final double VALUE6367 = 180.0;
   private static final double VALUE6368 = 360.0;
   private static final double VALUE6369 = 180.0;
   private static final float VALUE6370 = 0.25F;
   private static final double VALUE6516 = 0.5;
   private static final double VALUE6517 = 0.5;

   private void update469() {
      this.systemClass175.clearHistory();
      this.clientLevel22 = null;
   }

   @Override
   public void onVisualEnable() {
      this.update469();
   }

   private int compute319(
      BufferBuilder var1,
      Matrix4f var2,
      Vec3 var3,
      Vec3 var4,
      Vec3 var5,
      Vec3 var6,
      double var7,
      double var9,
      double var11,
      int var13,
      int var14,
      int var15,
      int var16
   ) {
      double var17 = var9 * VALUE6516;
      double var19 = var11 * VALUE6517;
      Vec3 var21 = var3.add(var6.scale(var7));
      Vec3 var22 = var21.add(var4.scale(-var17)).add(var5.scale(var19));
      Vec3 var23 = var21.add(var4.scale(var17)).add(var5.scale(var19));
      Vec3 var24 = var21.add(var4.scale(var17)).add(var5.scale(-var19));
      Vec3 var25 = var21.add(var4.scale(-var17)).add(var5.scale(-var19));
      this.apply887(var1, var2, var22, 0.0F, 0.0F, var13);
      this.apply887(var1, var2, var23, 1.0F, 0.0F, var14);
      this.apply887(var1, var2, var24, 1.0F, 1.0F, var15);
      this.apply887(var1, var2, var25, 0.0F, 1.0F, var16);
      return 4;
   }

   private void apply886(WorldRenderFrame var1) {
      if (mc.options.getCameraType().isFirstPerson()) {
         this.update469();
      } else {
         if (this.clientLevel22 != mc.level) {
            this.update469();
         }

         this.clientLevel22 = mc.level;
         int var2 = this.sliderSetting438.intValue() * 30;
         float var3 = var1.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         double var4 = ((double)mc.level.getGameTime() + var3) / VALUE6341;
         Vec3 var6 = new Vec3(
            Mth.lerp(var3, mc.player.xOld, mc.player.getX()),
            Mth.lerp(var3, mc.player.yOld, mc.player.getY()),
            Mth.lerp(var3, mc.player.zOld, mc.player.getZ())
         );
         this.systemClass175.addSample(var6, var4, var2);
         TrailHistory.TrailSnapshot var7 = this.systemClass175.sampleTrail(var2);
         Camera var8 = mc.gameRenderer.mainCamera();
         Vec3 var9 = createVec3_25(new Vector3f(1.0F, 0.0F, 0.0F).rotate(var8.rotation()));
         Vec3 var10 = createVec3_25(new Vector3f(0.0F, 1.0F, 0.0F).rotate(var8.rotation()));
         Matrix4f var11 = var1.getPoseStack().last().pose();
         BufferBuilder var12 = SharedVertexBuffer.shared().beginVertices(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         int var13 = 0;
         double var14 = var4 * VALUE6342 * this.sliderSetting440.floatValue();
         float var16 = VALUE6343;
         int var17 = var7.points().size();
         float var18 = (float)Math.min(1.0, VALUE6344 / var7.length());
         float var19 = this.sliderSetting439.floatValue() / VALUE6345 * VALUE6346;

         for (int var20 = 0; var20 < 3; var16 = -var16) {
            for (int var21 = 0; var21 < var17; var21++) {
               TrailHistory.TrailPoint var22 = var7.points().get(var21);
               float var23 = (float)Mth.clamp((var4 - var22.time()) / VALUE6347, 0.0, 1.0);
               float var24 = 1.0F - var21 / (float)var7.length();
               float var25 = 1.0F - var23 * var23;
               int var26 = (int)(VALUE6348 * var24 * var25 * var18);
               if (var26 > 5) {
                  double var27 = var14 - var21 * VALUE6349;
                  double var29 = (Math.sin(var27) + 1.0) * VALUE6350;
                  double var31 = Math.toRadians(var20 * 90 + var27 * VALUE6351 % VALUE6352);
                  Vec3 var33 = var22.position()
                     .subtract(var8.position())
                     .add(Math.cos(var31) * var16, var20 % 2 == 0 ? VALUE6353 + VALUE6354 * var29 : VALUE6355 - VALUE6356 * var29, Math.sin(var31) * var16);
                  float var34 = (VALUE6357 + VALUE6358 * var24) * var25 * var19;
                  float var35 = (VALUE6359 + VALUE6360 * var24) * var25 * var19;
                  var33 = var33.add(-var34 / VALUE6361, -var34 / VALUE6362, 0.0).add(var9.scale(var34 / VALUE6363)).add(var10.scale(var34 / 2.0F));
                  int var36 = Math.round(var26 * VALUE6364);
                  var13 += this.compute319(
                     var12,
                     var11,
                     var33,
                     var9,
                     var10,
                     Vec3.ZERO,
                     0.0,
                     var35,
                     var35,
                     this.compute327(var21 * 2, var36, var4),
                     this.compute327(var21 * 4, var36, var4),
                     this.compute327(var21 * 8, var36, var4),
                     this.compute327(var21 * 16, var36, var4)
                  );
                  var13 += this.compute319(
                     var12,
                     var11,
                     var33,
                     var9,
                     var10,
                     Vec3.ZERO,
                     0.0,
                     var34,
                     var34,
                     this.compute327(var21 * 2, var26, var4),
                     this.compute327(var21 * 4, var26, var4),
                     this.compute327(var21 * 8, var26, var4),
                     this.compute327(var21 * 16, var26, var4)
                  );
               }
            }

            var20++;
         }

         this.apply893(var12, ID38, var13, this.renderPipeline74);
      }
   }

   private void apply887(BufferBuilder var1, Matrix4f var2, Vec3 var3, float var4, float var5, int var6) {
      Vector4f var7 = VisualColors.toRgbaVector(var6);
      var1.addVertex(var2, (float)var3.x, (float)var3.y, (float)var3.z).setUv(var4, var5).setColor(var7.x, var7.y, var7.z, var7.w);
   }

   private static Vec3 createVec3_25(Vector3f var0) {
      return new Vec3(var0.x(), var0.y(), var0.z());
   }

   private int compute323(int var1, float var2) {
      var2 = Mth.clamp(var2, 0.0F, 1.0F);
      return VisualColors.argb(
         (int)(VisualColors.red(var1) * var2),
         (int)(VisualColors.green(var1) * var2),
         (int)(VisualColors.blue(var1) * var2),
         VisualColors.alpha(var1)
      );
   }

   public Trails() {
      super(TRAILS, ПРИЗРАКИ, Category.RENDER);
      this.sliderSetting438 = new NumberSetting(ДЛИНА_ШЛЕЙФА, VALUE6316, VALUE6317, VALUE6318, 1.0F);
      this.sliderSetting439 = new NumberSetting(РАЗМЕР_ЧАСТИЦ, VALUE6319, VALUE6320, VALUE6321, VALUE6322);
      this.sliderSetting440 = new NumberSetting(СКОРОСТЬ_ПРИЗРАКОВ, VALUE6323, 1.0F, VALUE6324, VALUE6325);
      this.systemClass175 = new TrailHistory();
      this.renderPipeline74 = GlowQuadRenderer.createGlowPipeline(HUSTLE_TRAILS_GHOSTS, CompareOp.ALWAYS_PASS);
      this.addSettings(this.sliderSetting438, this.sliderSetting439, this.sliderSetting440);
   }

   private void apply893(BufferBuilder var1, Identifier var2, int var3, RenderPipeline var4) {
      MeshData var5 = var1.build();
      if (var5 != null) {
         if (var3 == 0) {
            var5.close();
         } else {
            GpuBuffer var6 = null;

            try {
               DrawState var7 = var5.drawState();
               int var8 = var7.indexCount();
               AutoStorageIndexBuffer var9 = RenderSystem.getSequentialBuffer(var7.primitiveTopology());
               GpuBuffer var10 = var9.getBuffer(var8);
               IndexType var11 = var9.type();
               var6 = RenderSystem.getDevice().createBuffer(() -> TRAILS_VERTICES, 32, var5.vertexBuffer());
               RenderTarget var12 = mc.gameRenderer.mainRenderTarget();
               CommandEncoder var13 = RenderSystem.getDevice().createCommandEncoder();
               AbstractTexture var14 = mc.getTextureManager().getTexture(var2);
               RenderPass var15 = var13.createRenderPass(
                  () -> TRAILS1, var12.getColorTextureView(), Optional.empty(), var12.getDepthTextureView(), OptionalDouble.empty()
               );

               try {
                  RenderSystem.bindDefaultUniforms(var15);
                  var15.setPipeline(RenderSystem.getCompiledPipeline(var4));
                  var15.setUniform(SAMPLER0_36, var14.getTextureView(), var14.getSampler());
                  var15.setVertexBuffer(0, var6.slice());
                  var15.setIndexBuffer(var10, var11);
                  var15.drawIndexed(var8, 1, 0, 0, 0);
               } catch (Throwable var23) {
                  if (var15 != null) {
                     try {
                        var15.close();
                     } catch (Throwable var22) {
                        var23.addSuppressed(var22);
                     }
                  }

                  throw var23;
               }

               if (var15 != null) {
                  var15.close();
               }
            } finally {
               if (var6 != null) {
                  var6.close();
               }

               var5.close();
            }
         }
      }
   }

   private int compute327(int var1, int var2, double var3) {
      double var5 = (var3 * VALUE6365 + var1) % VALUE6366;
      float var7 = (float)((var5 >= VALUE6367 ? VALUE6368 - var5 : var5) / VALUE6369);
      int var8 = VisualTheme.themeColor(VisualThemeColor.MAIN);
      int var9 = VisualColors.lerpVectorColor(var8, this.compute323(var8, VALUE6370), var7);
      return VisualColors.argb(VisualColors.red(var9), VisualColors.green(var9), VisualColors.blue(var9), var2);
   }

   private void apply897(WorldRenderFrame var1) {
      if (mc.player != null && mc.level != null) {
         this.apply886(var1);
      } else {
         this.update469();
      }
   }

   @Override
   public void onVisualDisable() {
      this.update469();
   }

   public void render(WorldRenderFrame event) { if(enabled()) apply897(event); }

}
