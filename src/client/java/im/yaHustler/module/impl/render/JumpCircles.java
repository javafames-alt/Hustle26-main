package im.yaHustler.module.impl.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
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
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.*;
import im.yaHustler.render.circles.CircleColors;
import im.yaHustler.render.circles.CirclePipelineBuilder;
import im.yaHustler.render.circles.CircleGeometry;
import im.yaHustler.render.circles.CircleFrame;
import net.minecraft.client.Minecraft;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** the original implementation's six jump effects, remapped to 26.3 with explicit settings, geometry and GPU pass names.
 * TerrainCircle samples collision surfaces once at spawn; all animations age in milliseconds.
 */
@ModuleInfo(id="jumpcircles",name="JumpCircles",category=Category.RENDER,description="Круги при прыжке")
public final class JumpCircles extends Module implements AutoCloseable {
   private static final Minecraft mc = Minecraft.getInstance();
   private static Identifier shader(String name) {
      return Identifier.fromNamespaceAndPath("hustle263", "core/coliseimvisual/" + name);
   }
   private static final class LightningBranch {
      final Vec3[] points;
      final float delay;
      LightningBranch(Vec3[] points, float delay) { this.points=points; this.delay=delay; }
   }
   private static final Identifier CIRCLE_TEXTURE = Identifier.fromNamespaceAndPath("hustle263", "textures/jumpcircles/circle.png");
   private static final Identifier TERRAIN_TEXTURE = Identifier.fromNamespaceAndPath("hustle263", "textures/jumpcircles/hustle263_circle.png");

   private static final Identifier[] LEECH_FRAMES = leechFrameTextures();

   private final ModeSetting shape;
   private final NumberSetting radius;
   private final BooleanSetting themeColor;
   private final ColorSetting glowColor;
   private final BooleanSetting distortion;
   private final NumberSetting distortionStrength;
   private final NumberSetting terrainLifetime;
   private final NumberSetting terrainRadius;
   private final BooleanSetting glass;
   private final ModeSetting leechAnimation;
   private final NumberSetting leechLifetime;
   private final NumberSetting leechRange;
   private final ModeSetting leechColors;
   private final ColorSetting leechColorStart;
   private final ColorSetting leechColorEnd;
   private final BooleanSetting deepLight;
   private final NumberSetting waveLifetime;
   private final NumberSetting waveSize;
   private final ModeSetting waveAnimation;
   private final NumberSetting waveStrength;
   private final NumberSetting waveThickness;
   private final NumberSetting saturation;
   private final BooleanSetting turbulence;
   private final NumberSetting turbulenceStrength;
   private final BooleanSetting waveGlow;
   private final NumberSetting glowHeight;
   private final NumberSetting glowWidth;
   private final NumberSetting glowIntensity;
   private final NumberSetting glowOpacity;
   private final NumberSetting colorIntensity;
   private final ModeSetting waveColors;
   private final BooleanSetting secondWaveColor;
   private final ColorSetting waveColorStart;
   private final ColorSetting waveColorEnd;
   private final List<Circle> circles;
   private final List<LightningBurst> lightningBursts;
   private final List<Leech> leeches;
   private final List<TerrainCircle> terrainCircles;
   private final List<Wave> waves;
   private final RenderPipeline circlePipeline;
   private final RenderPipeline terrainPipeline;
   private final RenderPipeline glassPipeline;
   private final RenderPipeline distortionPipeline;
   private final RenderPipeline distortionGlowPipeline;
   private final RenderPipeline diamondPipeline;
   private final RenderPipeline lightningPipeline;
   private final RenderPipeline wavePipeline;
   private boolean wasOnGround;
   private boolean wasJumpPressed;
   private boolean jumpEmitted;
   private long lastSpawnAt;
   private AbstractTexture circleTexture;
   private AbstractTexture terrainTexture;
   private TextureTarget distortionScene;
   private TextureTarget waveScene;
   private final Matrix4f inverseViewProjection;
   private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private final int[] terrainPalette;

   private void resizeWaveScene(int width, int height) {
      if (this.waveScene == null) {
         this.waveScene = new TextureTarget("jump-flex-scene", width, height, GpuFormat.RGBA8_UNORM, null);
      } else if (this.waveScene.width != width || this.waveScene.height != height) {
         this.waveScene.resize(width, height);
      }
   }

   public JumpCircles() {
      this.shape = new ModeSetting("Форма", "Круг", "Круг");
      this.radius = new NumberSetting("Радиус", 1.5F, 1.0F, 2.0F, 0.1F)
         .visibleWhen(() -> !this.shape.is("Пиявки") && !this.shape.is("Круг 2") && !this.shape.is("Гибкий"));
      this.themeColor = new BooleanSetting("Авто цвет под тему", true).visibleWhen(() -> !this.shape.is("Пиявки") && !this.shape.is("Гибкий"));
      this.glowColor = new ColorSetting("Цвет свечения", -1661312)
         .visibleWhen(() -> !this.shape.is("Пиявки") && !this.shape.is("Гибкий") && !this.themeColor.enabled());
      this.distortion = new BooleanSetting("Искажение", false).visibleWhen(() -> this.shape.is("Круг"));
      this.distortionStrength = new NumberSetting("Сила искажения", 18.0F, 3.0F, 40.0F, 1.0F).visibleWhen(() -> this.shape.is("Круг") && this.distortion.enabled());
      this.terrainLifetime = new NumberSetting("Время жизни Круг 2", 15.0F, 15.0F, 50.0F, 1.0F).visibleWhen(() -> this.shape.is("Круг 2"));
      this.terrainRadius = new NumberSetting("Радиус Круг 2", 1.25F, 0.5F, 2.5F, 0.1F).visibleWhen(() -> this.shape.is("Круг 2"));
      this.glass = new BooleanSetting("Стекло", false).visibleWhen(() -> this.shape.is("Круг 2"));
      this.leechAnimation = new ModeSetting("Анимация", "FadeOutRotate", "FadeOutRotate", "FadeInOutRotate", "FadeInExponent")
         .visibleWhen(() -> this.shape.is("Пиявки"));
      this.leechLifetime = new NumberSetting("Время жизни Пиявки", 3500.0F, 500.0F, 5000.0F, 100.0F).visibleWhen(() -> this.shape.is("Пиявки"));
      this.leechRange = new NumberSetting("Дальность", 2.0F, 1.0F, 3.0F, 0.1F).visibleWhen(() -> this.shape.is("Пиявки"));
      this.leechColors = new ModeSetting("Режим цвета", "Радуга", "Радуга", "Клиент", "Свой", "Свой+Fade")
         .visibleWhen(() -> this.shape.is("Пиявки"));
      this.leechColorStart = new ColorSetting("Цвет 1", -45056)
         .visibleWhen(() -> this.shape.is("Пиявки") && (this.leechColors.is("Свой") || this.leechColors.is("Свой+Fade")));
      this.leechColorEnd = new ColorSetting("Цвет 2", -29184).visibleWhen(() -> this.shape.is("Пиявки") && this.leechColors.is("Свой+Fade"));
      this.deepLight = new BooleanSetting("Глубокий свет", true).visibleWhen(() -> this.shape.is("Пиявки"));
      this.waveLifetime = new NumberSetting("Время", 1500.0F, 500.0F, 5000.0F, 50.0F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.waveSize = new NumberSetting("Размер", 3.0F, 0.5F, 3.0F, 0.1F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.waveAnimation = new ModeSetting("Тип анимации", "Эластичная", "Эластичная", "Обычная", "Назад")
         .visibleWhen(() -> this.shape.is("Гибкий"));
      this.waveStrength = new NumberSetting("Сила волны", 1.0F, 0.2F, 3.0F, 0.1F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.waveThickness = new NumberSetting("Толщина волны", 0.5F, 0.3F, 0.8F, 0.05F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.saturation = new NumberSetting("Насыщенность", 0.0F, -1.0F, 1.0F, 0.05F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.turbulence = new BooleanSetting("Турбулентность", true).visibleWhen(() -> this.shape.is("Гибкий"));
      this.turbulenceStrength = new NumberSetting("Сила турбулентности", 0.5F, 0.1F, 2.0F, 0.1F).visibleWhen(() -> this.shape.is("Гибкий") && this.turbulence.enabled());
      this.waveGlow = new BooleanSetting("Свечение волны", true).visibleWhen(() -> this.shape.is("Гибкий"));
      this.glowHeight = new NumberSetting("Высота свечения", 22.0F, 15.0F, 45.0F, 1.0F).visibleWhen(() -> this.shape.is("Гибкий") && this.waveGlow.enabled());
      this.glowWidth = new NumberSetting("Ширина свечения", 18.0F, 10.0F, 30.0F, 1.0F).visibleWhen(() -> this.shape.is("Гибкий") && this.waveGlow.enabled());
      this.glowIntensity = new NumberSetting("Сила цвета свечения", 70.0F, 30.0F, 100.0F, 1.0F).visibleWhen(() -> this.shape.is("Гибкий") && this.waveGlow.enabled());
      this.glowOpacity = new NumberSetting("Прозрачность свечения", 60.0F, 20.0F, 80.0F, 1.0F).visibleWhen(() -> this.shape.is("Гибкий") && this.waveGlow.enabled());
      this.colorIntensity = new NumberSetting("Сила цвета", 55.0F, 0.0F, 100.0F, 1.0F).visibleWhen(() -> this.shape.is("Гибкий"));
      this.waveColors = new ModeSetting("Режим цвета волны", "Радуга", "Радуга", "Клиент", "Свой")
         .visibleWhen(() -> this.shape.is("Гибкий"));
      this.secondWaveColor = new BooleanSetting("Второй цвет", false).visibleWhen(() -> this.shape.is("Гибкий") && this.waveColors.is("Свой"));
      this.waveColorStart = new ColorSetting("Цвет волны", -1).visibleWhen(() -> this.shape.is("Гибкий") && this.waveColors.is("Свой"));
      this.waveColorEnd = new ColorSetting("Цвет волны 2", -1661312)
         .visibleWhen(() -> this.shape.is("Гибкий") && this.waveColors.is("Свой") && this.secondWaveColor.enabled());
      this.circles = new ArrayList<>();
      this.lightningBursts = new ArrayList<>();
      this.leeches = new ArrayList<>();
      this.terrainCircles = new ArrayList<>();
      this.waves = new ArrayList<>();
      this.circlePipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_circle")
         .vertexShader(shader("dead_chams"))
         .fragmentShader(shader("dead_chams"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .sampler("Sampler0")
         .blend(BlendFunction.ADDITIVE)
         .build();
      this.terrainPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_circle_hustle263")
         .vertexShader(shader("dead_chams"))
         .fragmentShader(shader("dead_chams"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .sampler("Sampler0")
         .blend(BlendFunction.OVERLAY)
         .build();
      this.glassPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_circle_hustle263_glass")
         .uniform("Globals", UniformType.UNIFORM_BUFFER)
         .vertexShader(shader("jump_frost"))
         .fragmentShader(shader("jump_frost"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .sampler("Sampler0")
         .blend(BlendFunction.TRANSLUCENT)
         .build();
      this.distortionPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_circle_distortion")
         .vertexShader(shader("jump_circle"))
         .fragmentShader(shader("jump_circle_distortion"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .uniform("JumpDistortion", UniformType.UNIFORM_BUFFER)
         .sampler("SceneSampler")
         .blend(BlendFunction.TRANSLUCENT)
         .build();
      this.distortionGlowPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_circle_distortion_glow")
         .vertexShader(shader("jump_circle"))
         .fragmentShader(shader("jump_circle_glow"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .blend(BlendFunction.ADDITIVE)
         .build();
      this.diamondPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_diamond")
         .vertexShader(shader("jump_circle"))
         .fragmentShader(shader("jump_diamond"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .blend(BlendFunction.ADDITIVE)
         .build();
      this.lightningPipeline = CirclePipelineBuilder.builder(CirclePipelineBuilder.WORLD_PROJECTION)
         .location("hustle263/coliseimvisual/jump_lightning")
         .vertexShader(shader("jump_circle"))
         .fragmentShader(shader("jump_lightning"))
         .depthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .writeDepth(false)
         .cull(false)
         .vertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
         .blend(BlendFunction.ADDITIVE)
         .build();
      this.wavePipeline = CirclePipelineBuilder.builder()
         .location("hustle263/coliseimvisual/jump_flex")
         .vertexShader(shader("hands_fullscreen"))
         .fragmentShader(shader("jump_flex"))
         .depthTest(CompareOp.ALWAYS_PASS)
         .writeDepth(false)
         .cull(false)
         .noBlend()
         .vertexFormat(CirclePipelineBuilder.NO_VERTICES, PrimitiveTopology.TRIANGLES)
         .uniform("JumpFlex", UniformType.UNIFORM_BUFFER)
         .sampler("SceneSampler")
         .sampler("DepthSampler")
         .build();
      this.wasOnGround = true;
      this.inverseViewProjection = new Matrix4f();
      this.terrainPalette = new int[49];
      for (Setting<?> setting : new Setting<?>[]{this.radius, this.themeColor, this.glowColor, this.distortion, this.distortionStrength, this.terrainLifetime, this.terrainRadius, this.glass, this.leechAnimation, this.leechLifetime, this.leechRange, this.leechColors, this.leechColorStart, this.leechColorEnd, this.deepLight, this.waveLifetime, this.waveSize, this.waveAnimation, this.waveStrength, this.waveThickness, this.saturation, this.turbulence, this.turbulenceStrength, this.waveGlow, this.glowHeight, this.glowWidth, this.glowIntensity, this.glowOpacity, this.colorIntensity, this.waveColors, this.secondWaveColor, this.waveColorStart, this.waveColorEnd}) add(setting);
   }

   private TerrainCircle sampleTerrainCircle(Entity entity, double x, double y, double z, float radius) {
      float sampleRadius = radius + 0.5F;
      int gridSize = terrainGridSize(radius);
      double originX = x - sampleRadius;
      double originZ = z - sampleRadius;
      double gridStep = 2.0 * sampleRadius / (gridSize - 1);
      float[] heights = new float[gridSize * gridSize];
      float[] visibility = new float[gridSize * gridSize];
      boolean[] blocked = new boolean[gridSize * gridSize];

      for (int row = 0; row < gridSize; row++) {
         for (int column = 0; column < gridSize; column++) {
            double sampleX = originX + row * gridStep;
            double sampleZ = originZ + column * gridStep;
            float surfaceY = this.sampleGroundHeight(entity, sampleX, sampleZ, y);
            float heightDelta = surfaceY - (float)y;
            int cell = row * gridSize + column;
            visibility[cell] = heightDelta >= -2.55F ? 1.0F : 0.0F;
            blocked[cell] = heightDelta > 5.8F;
            heights[cell] = Mth.clamp(surfaceY, (float)(y - 2.55F), (float)(y + 2.8F));
         }
      }

      smoothTerrainGrid(heights, visibility, blocked, gridSize);
      return new TerrainCircle(
         new Vec3(x, y, z), radius, originX, originZ, gridStep, gridSize, heights, visibility, blocked, this.collectVisibleWalls(entity, x, y, z, radius)
      );
   }

   private void drawTexturedQuads(BufferBuilder vertices, Identifier textureId, int vertexCount) {
      MeshData mesh = vertices.build();
      if (mesh != null) {
         if (vertexCount == 0) {
            mesh.close();
         } else {
            GpuBuffer vertexBuffer = null;

            try {
               DrawState drawState = mesh.drawState();
               int indexCount = drawState.indexCount();
               AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
               GpuBuffer indexBuffer = indices.getBuffer(indexCount);
               IndexType indexType = indices.type();
               vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "jumpLeeches", 32, mesh.vertexBuffer());
               RenderTarget target = mc.gameRenderer.mainRenderTarget();
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
               RenderPass pass = encoder.createRenderPass(
                  () -> "jumpLeeches", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
               );

               try {
                  RenderSystem.bindDefaultUniforms(pass);
                  pass.setUniform("Sampler0", texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                  pass.setPipeline(RenderSystem.getCompiledPipeline(this.circlePipeline));
                  pass.setVertexBuffer(0, vertexBuffer.slice());
                  pass.setIndexBuffer(indexBuffer, indexType);
                  pass.drawIndexed(indexCount, 1, 0, 0, 0);
               } catch (Throwable failure) {
                  if (pass != null) {
                     try {
                        pass.close();
                     } catch (Throwable failure2) {
                        failure.addSuppressed(failure2);
                     }
                  }

                  throw failure;
               }

               if (pass != null) {
                  pass.close();
               }
            } finally {
               if (vertexBuffer != null) {
                  vertexBuffer.close();
               }

               mesh.close();
            }
         }
      }
   }

   private static float easeInOutElastic(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      if (progress != 0.0F && progress != 1.0F) {
         float progress2 = ((float) (Math.PI * 4.0 / 9.0));
         return progress < 0.5F
            ? (float)(-(Math.pow(2.0, 20.0F * progress - 10.0F) * Math.sin((20.0F * progress - 11.125) * progress2)) / 2.0)
            : (float)(Math.pow(2.0, -20.0F * progress + 10.0F) * Math.sin((20.0F * progress - 11.125) * progress2) / 2.0 + 1.0);
      } else {
         return progress;
      }
   }

   private BlockHitResult traceGround(Entity entity, double x, double z, double startY, double endY) {
      Vec3 point = new Vec3(x, startY, z);
      Vec3 point2 = new Vec3(x, endY, z);
      return mc.level.clip(new ClipContext(point, point2, Block.COLLIDER, Fluid.NONE, entity));
   }

   private void appendWallVertex(
      BufferBuilder vertices, Matrix4f matrix, TerrainCircle circle, Vec3 camera, float radius, float opacity, float time, float[] wall, float value, float value2
   ) {
      double coordinate = Mth.lerp(value, wall[0], wall[2]);
      double coordinate2 = Mth.lerp(value, wall[1], wall[3]);
      double coordinate3 = wall[4] + value2;
      double coordinate4 = coordinate - circle.position.x;
      double coordinate5 = coordinate2 - circle.position.z;
      float value3 = (float)Math.sqrt(coordinate4 * coordinate4 + coordinate5 * coordinate5);
      float value4 = value3 / radius;
      float value5 = (float)(coordinate3 - circle.position.y);
      float value6 = surfaceNoise(coordinate, coordinate3, coordinate2, time);
      float value7 = value5 + 0.45F * value6;
      float value8 = value4 + 0.12F * value6;
      float value9 = Math.min(2.6F, Math.max(0.0F, radius - value3) * 1.3F);
      float value10 = value9 > 0.05F ? 1.0F - smoothstep(value9 * 0.55F, value9, value7) : 0.0F;
      float value11 = 0.8F * smoothstep(0.0F, 0.3F, value5) * value10 * (1.0F - smoothstep(0.85F, 1.05F, value8));
      float value12 = 0.82F + 0.18F * surfaceNoise(coordinate * 1.9 + 7.3, coordinate3 * 1.6, coordinate2 * 2.1 - 3.1, time * 1.4F);
      float value13 = opacity * value11 * value12;
      float value14 = Math.min(value4, 1.0F);
      float value15 = value3 > 1.0E-4F ? (float)(coordinate4 / value3) : 0.0F;
      float value16 = value3 > 1.0E-4F ? (float)(coordinate5 / value3) : 0.0F;
      float value17 = 0.5F + value15 * value14 * 0.5F;
      float value18 = 0.5F + value16 * value14 * 0.5F;
      float value19 = (float)Math.atan2(coordinate5, coordinate4);
      int index = Math.floorMod(Math.round(value19 / ((float) (Math.PI * 2)) * 48.0F), 48);
      int index2 = this.themeColor.enabled() ? this.terrainPalette[index] : this.effectColor();
      this.appendVertex(
         vertices,
         matrix,
         (float)(coordinate - camera.x),
         (float)(coordinate3 - camera.y),
         (float)(coordinate2 - camera.z),
         value17,
         value18,
         CircleColors.rgba(multiplyOpacity(index2, value13))
      );
   }

   private void renderLightning(CircleFrame frame) {
      if (!this.lightningBursts.isEmpty()) {
         Vec3 partialTick = mc.gameRenderer.mainCamera().position();
         Matrix4f rotation = frame.pose().last().pose();
         Vector4f camera = CircleColors.rgba(this.effectColor());
         BufferBuilder matrix = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         int vertices = 0;
         long timestamp = System.currentTimeMillis();
         float value = (float)(timestamp % 100000L) / 1000.0F;
         Iterator iterator = this.lightningBursts.iterator();

         while (iterator.hasNext()) {
            LightningBurst burst = (LightningBurst)iterator.next();
            float value2 = (float)(timestamp - burst.bornAt) / 1550.0F;
            if (value2 >= 1.0F) {
               iterator.remove();
            } else {
               float value3 = this.smootherstep(Mth.clamp(value2 / 0.4F, 0.0F, 1.0F));
               float value4 = 1.0F - this.smootherstep(Mth.clamp(value2 / 0.05F, 0.0F, 1.0F));
               float value5 = this.smootherstep(Mth.clamp((value2 - 0.58F) / 0.42000002F, 0.0F, 1.0F));
               float value6 = value3 >= 1.0F ? 0.85F + 0.15F * Mth.sin(value * 42.0F + burst.seed) : 1.0F;
               float value7 = (1.0F - value5) * value6;
               if (!(value7 <= 0.01F) && !(value3 <= 0.001F)) {
                  for (LightningBranch branch : burst.branches) {
                     Vec3[] points = branch.points;
                     int index = points.length - 1;
                     float value8 = Mth.clamp((value3 - branch.delay) / (1.0F - branch.delay), 0.0F, 1.0F);
                     if (!(value8 <= 0.001F)) {
                        float value9 = value8 * index;

                        for (int index2 = 0; index2 < index && !(value9 <= index2); index2++) {
                           float value10 = Mth.clamp(value9 - index2, 0.0F, 1.0F);
                           boolean visible = value10 < 1.0F;
                           Vec3 point = visible ? points[index2].lerp(points[index2 + 1], value10) : points[index2 + 1];
                           float value11 = (float)index2 / index;
                           float value12 = value11 + value10 / index;
                           float value13 = Mth.lerp(value11, 0.26F, 0.03F) * this.radius.floatValue();
                           float value14 = Mth.lerp(value12, 0.26F, 0.03F) * this.radius.floatValue();
                           float value15 = Mth.lerp((value11 + value12) * 0.5F, 1.0F, 0.45F);
                           float value16 = visible ? 1.9F : 1.0F;
                           float value17 = value7 * value15 * camera.w * (1.0F + value4 * 1.6F) * value16;
                           float value18 = visible ? 1.0F : Math.min(value11 * 1.6F, 1.0F);
                           Vector4f color = new Vector4f(
                              Mth.lerp(value18, 1.0F, camera.x), Mth.lerp(value18, 1.0F, camera.y), Mth.lerp(value18, 1.0F, camera.z), Mth.clamp(value17, 0.0F, 1.0F)
                           );
                           if (value13 > 0.001F && value14 > 0.001F) {
                              vertices += this.appendLightningSegment(matrix, rotation, burst.position, partialTick, points[index2], point, value13, value14, color);
                           }
                        }
                     }
                  }
               }
            }
         }

         this.drawColoredQuads(matrix, vertices, this.lightningPipeline);
      }
   }

   private int appendTerrainCellSmooth(BufferBuilder vertices, Matrix4f matrix, TerrainCircle circle, Vec3 camera, float radius, float opacity, int row, int column) {
      int index = circle.gridSize;
      int index2 = row * index + column;
      int index3 = row * index + column + 1;
      int index4 = (row + 1) * index + column + 1;
      int index5 = (row + 1) * index + column;
      boolean visible = circle.blocked[index2];
      boolean visible2 = circle.blocked[index3];
      boolean visible3 = circle.blocked[index4];
      boolean visible4 = circle.blocked[index5];
      if (visible && visible2 && visible3 && visible4) {
         return 0;
      }

      float value = circle.heights[index2];
      float value2 = circle.heights[index3];
      float value3 = circle.heights[index4];
      float value4 = circle.heights[index5];
      float value5 = circle.visibility[index2];
      float value6 = circle.visibility[index3];
      float value7 = circle.visibility[index4];
      float value8 = circle.visibility[index5];
      if (visible || visible2 || visible3 || visible4) {
         float value9 = -Float.MAX_VALUE;
         if (!visible) {
            value9 = Math.max(value9, value);
         }

         if (!visible2) {
            value9 = Math.max(value9, value2);
         }

         if (!visible3) {
            value9 = Math.max(value9, value3);
         }

         if (!visible4) {
            value9 = Math.max(value9, value4);
         }

         if (visible) {
            value = value9;
         }

         if (visible2) {
            value2 = value9;
         }

         if (visible3) {
            value3 = value9;
         }

         if (visible4) {
            value4 = value9;
         }
      }

      float value10 = Math.min(Math.min(value, value2), Math.min(value3, value4));
      float value11 = Math.max(Math.max(value, value2), Math.max(value3, value4));
      int index6 = Mth.clamp((int)Math.ceil((value11 - value10) / 0.35F), 1, 8);
      boolean visible5 = Math.abs(value - value4) + Math.abs(value2 - value3) >= Math.abs(value - value2) + Math.abs(value4 - value3);
      int index7 = 0;

      for (int index8 = 0; index8 < index6; index8++) {
         float value12 = (float)index8 / index6;
         float value13 = (float)(index8 + 1) / index6;
         if (visible5) {
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value12, 0.0F, value, value5);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value12, 1.0F, value4, value8);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value13, 1.0F, value3, value7);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value13, 0.0F, value2, value6);
         } else {
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 0.0F, value12, value, value5);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 0.0F, value13, value4, value8);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 1.0F, value13, value3, value7);
            this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 1.0F, value12, value2, value6);
         }

         index7 += 4;
      }

      return index7;
   }

   private float waveExpansion(float progress) {
      float clamped = Mth.clamp(progress, 0.0F, 1.0F);
      if (this.waveAnimation.is("Назад")) {
         return easeOutBack(clamped);
      } else {
         return this.waveAnimation.is("Обычная") ? clamped : easeOutElastic(clamped);
      }
   }

   private static void writeColor(float[] target, int offset, int color) {
      target[offset] = (color >> 16 & 0xFF) / 255.0F;
      target[offset + 1] = (color >> 8 & 0xFF) / 255.0F;
      target[offset + 2] = (color & 0xFF) / 255.0F;
      target[offset + 3] = 1.0F;
   }

   private List<float[]> collectVisibleWalls(Entity entity, double coordinate, double coordinate2, double coordinate3, float value) {
      ArrayList items = new ArrayList<>();
      double coordinate4 = value + 0.2;
      int index = Mth.floor(coordinate - coordinate4);
      int index2 = Mth.floor(coordinate + coordinate4);
      int index3 = Mth.floor(coordinate3 - coordinate4);
      int index4 = Mth.floor(coordinate3 + coordinate4);
      int index5 = Mth.floor(coordinate2) - 1;
      int index6 = Mth.floor(coordinate2) + 2;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int index7 = index; index7 <= index2; index7++) {
         for (int index8 = index3; index8 <= index4; index8++) {
            float value2 = this.sampleGroundHeight(entity, index7 + 0.5, index8 + 0.5, coordinate2);
            if (value2 - coordinate2 <= 5.8F) {
               for (int index9 = index5; index9 <= index6; index9++) {
                  cursor.set(index7, index9, index8);
                  VoxelShape collision = mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor);
                  if (isSolidCube(collision)) {
                     for (Direction direction : HORIZONTAL_DIRECTIONS) {
                        cursor.set(index7 + direction.getStepX(), index9, index8 + direction.getStepZ());
                        VoxelShape collision2 = mc.level.getBlockState(cursor).getCollisionShape(mc.level, cursor);
                        boolean visible = collision2.isEmpty() || !isSolidCube(collision2);
                        cursor.set(index7, index9, index8);
                        if (visible) {
                           double coordinate5 = index7 + 0.5 + direction.getStepX() * 0.5;
                           double coordinate6 = index8 + 0.5 + direction.getStepZ() * 0.5;
                           if (!((coordinate - coordinate5) * direction.getStepX() + (coordinate3 - coordinate6) * direction.getStepZ() <= 0.0)) {
                              float value3;
                              float value4;
                              float value5;
                              float value6;
                              if (direction.getStepZ() != 0) {
                                 value3 = index7;
                                 value5 = index7 + 1;
                                 value4 = value6 = (float)(index8 + (direction == Direction.SOUTH ? 1.0 : 0.0) + direction.getStepZ() * 0.02);
                              } else {
                                 value4 = index8;
                                 value6 = index8 + 1;
                                 value3 = value5 = (float)(index7 + (direction == Direction.EAST ? 1.0 : 0.0) + direction.getStepX() * 0.02);
                              }

                              double coordinate7 = Mth.clamp(coordinate, Math.min(value3, value5), Math.max(value3, value5)) - coordinate;
                              double coordinate8 = Mth.clamp(coordinate3, Math.min(value4, value6), Math.max(value4, value6)) - coordinate3;
                              if (!(coordinate7 * coordinate7 + coordinate8 * coordinate8 > coordinate4 * coordinate4)) {
                                 items.add(new float[]{value3, value4, value5, value6, index9});
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return items;
   }

   private static float smoothstep(float start, float end, float value) {
      float progress = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
      return progress * progress * (3.0F - 2.0F * progress);
   }

   private static float easeOutBack(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      float progress2 = 1.70158F;
      float value = progress2 + 1.0F;
      return 1.0F + value * (float)Math.pow(progress - 1.0F, 3.0) + progress2 * (float)Math.pow(progress - 1.0F, 2.0);
   }

   private static float easeOutCircular(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      return (float)Math.sqrt(1.0 - Math.pow(progress - 1.0F, 2.0));
   }

   private int effectColor() {
      return this.themeColor.enabled() ? CircleColors.primary() : this.glowColor.color();
   }

   private void renderCircles(CircleFrame frame) {
      if (!this.circles.isEmpty()) {
         if (this.circleTexture == null) {
            this.circleTexture = mc.getTextureManager().getTexture(CIRCLE_TEXTURE);
         }

         if (this.circleTexture != null) {
            float partialTick = frame.deltaTracker().getGameTimeDeltaPartialTick(true);
            float rotationDegrees = ((float)mc.level.getGameTime() + partialTick) * 2.5F;
            Vec3 camera = mc.gameRenderer.mainCamera().position();
            Matrix4f matrix = frame.pose().last().pose();
            boolean distort = this.distortion.enabled();
            BufferBuilder vertices = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int vertexCount = 0;
            Iterator<Circle> iterator = this.circles.iterator();

            while (iterator.hasNext()) {
               Circle circle = (Circle)iterator.next();
               long ageMs = System.currentTimeMillis() - circle.bornAt - circle.delayMs;
               if (ageMs >= 0L) {
                  float progress = (float)ageMs / 1250.0F;
                  if (progress >= 1.0F) {
                     iterator.remove();
                  } else {
                     float growProgress = Mth.clamp(progress / 0.34F, 0.0F, 1.0F);
                     float shrinkProgress = Mth.clamp((progress - 0.66F) / 0.34F, 0.0F, 1.0F);
                     float growEase = this.smootherstep(growProgress);
                     float shrinkEase = this.smootherstep(shrinkProgress);
                     float expansion = this.easeOutExponential(Mth.clamp(progress / 0.4F, 0.0F, 1.0F));
                     float bounce = Mth.sin(growProgress * ((float) Math.PI)) * 0.035F;
                     float ripple = Mth.sin(Mth.clamp((progress - 0.28F) / 0.38F, 0.0F, 1.0F) * ((float) Math.PI)) * 0.012F;
                     float alpha = growEase * (1.0F - shrinkEase) * circle.opacity;
                     float sizeScale = 0.14F + expansion * 0.86F + bounce + ripple + shrinkEase * 0.15F + circle.sizeOffset;
                     float baseRadius = this.radius.floatValue() * 0.55F;
                     float radius = baseRadius * sizeScale;
                     float quadRadius = distort ? radius * 1.4F : radius;
                     Vector4f color = CircleColors.rgba(this.effectColor());
                     Vector4f fadedColor = new Vector4f(color.x, color.y, color.z, color.w * alpha);
                     float rotation = distort ? 0.0F : (float)Math.toRadians(rotationDegrees + progress * 72.0F + (float)circle.delayMs * 0.06F);
                     this.appendQuad(vertices, matrix, circle.position, camera, quadRadius, quadRadius, rotation, fadedColor);
                     vertexCount += 4;
                  }
               }
            }

            MeshData mesh = vertices.build();
            if (mesh != null) {
               if (vertexCount == 0) {
                  mesh.close();
               } else {
                  RenderPass pass = null;
                  GpuBuffer vertexBuffer = null;
                  GpuBuffer distortionUniforms = null;

                  try {
                     RenderTarget target = mc.gameRenderer.mainRenderTarget();
                     CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                     float timeSeconds = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;
                     float screenHeight = Math.max(1.0F, mc.getWindow().getScreenHeight());
                     float mouseX = 1.5F * (2.0F * (float)mc.mouseHandler.xpos() - mc.getWindow().getScreenWidth()) / screenHeight;
                     float mouseY = 1.5F
                        * (2.0F * (mc.getWindow().getScreenHeight() - (float)mc.mouseHandler.ypos()) - mc.getWindow().getScreenHeight())
                        / screenHeight;
                     DrawState drawState = mesh.drawState();
                     int indexCount = drawState.indexCount();
                     AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
                     GpuBuffer indexBuffer = indices.getBuffer(indexCount);
                     IndexType indexType = indices.type();
                     vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "jumpCircleVertices", 32, mesh.vertexBuffer());
                     if (distort) {
                        TextureTarget scene = this.resizeDistortionScene(target);
                        encoder.copyTextureToTexture(target.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);
                        MemoryStack stack = MemoryStack.stackPush();

                        try {
                           ByteBuffer uniformBytes = Std140Builder.onStack(stack, 32)
                              .putVec2(target.width, target.height)
                              .putFloat(this.distortionStrength.floatValue())
                              .putFloat(timeSeconds)
                              .putVec2(mouseX, mouseY)
                              .align(16)
                              .get();
                           distortionUniforms = RenderSystem.getDevice().createBuffer(() -> "jumpCircleDistortionUniforms", 128, uniformBytes);
                        } catch (Throwable failure) {
                           if (stack != null) {
                              try {
                                 stack.close();
                              } catch (Throwable failure2) {
                                 failure.addSuppressed(failure2);
                              }
                           }

                           throw failure;
                        }

                        if (stack != null) {
                           stack.close();
                        }

                        RenderPass distortionPass = encoder.createRenderPass(
                           () -> "jumpCircleDistortion", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                        );

                        try {
                           RenderSystem.bindDefaultUniforms(distortionPass);
                           distortionPass.setUniform("SceneSampler", scene.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                           distortionPass.setUniform("JumpDistortion", distortionUniforms);
                           distortionPass.setPipeline(RenderSystem.getCompiledPipeline(this.distortionPipeline));
                           distortionPass.setVertexBuffer(0, vertexBuffer.slice());
                           distortionPass.setIndexBuffer(indexBuffer, indexType);
                           distortionPass.drawIndexed(indexCount, 1, 0, 0, 0);
                        } catch (Throwable failure3) {
                           if (distortionPass != null) {
                              try {
                                 distortionPass.close();
                              } catch (Throwable failure4) {
                                 failure3.addSuppressed(failure4);
                              }
                           }

                           throw failure3;
                        }

                        if (distortionPass != null) {
                           distortionPass.close();
                        }

                        RenderPass glowPass = encoder.createRenderPass(
                           () -> "jumpCircleDistortionGlow", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                        );

                        try {
                           RenderSystem.bindDefaultUniforms(glowPass);
                           glowPass.setPipeline(RenderSystem.getCompiledPipeline(this.distortionGlowPipeline));
                           glowPass.setVertexBuffer(0, vertexBuffer.slice());
                           glowPass.setIndexBuffer(indexBuffer, indexType);
                           glowPass.drawIndexed(indexCount, 1, 0, 0, 0);
                        } catch (Throwable failure5) {
                           if (glowPass != null) {
                              try {
                                 glowPass.close();
                              } catch (Throwable failure6) {
                                 failure5.addSuppressed(failure6);
                              }
                           }

                           throw failure5;
                        }

                        if (glowPass != null) {
                           glowPass.close();
                        }
                     }

                     if (!distort) {
                        pass = encoder.createRenderPass(
                           () -> "jumpCircle", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                        );
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("Sampler0", this.circleTexture.getTextureView(), this.circleTexture.getSampler());
                        pass.setPipeline(RenderSystem.getCompiledPipeline(this.circlePipeline));
                        pass.setVertexBuffer(0, vertexBuffer.slice());
                        pass.setIndexBuffer(indexBuffer, indexType);
                        pass.drawIndexed(indexCount, 1, 0, 0, 0);
                     }
                  } finally {
                     if (pass != null) {
                        pass.close();
                     }

                     if (vertexBuffer != null) {
                        vertexBuffer.close();
                     }

                     if (distortionUniforms != null) {
                        distortionUniforms.close();
                     }

                     mesh.close();
                  }
               }
            }
         }
      }
   }

   private int appendLeechQuad(BufferBuilder vertices, Matrix4f matrix, Vec3 point, LeechQuad quad) {
      float value = quad.size * 0.5F;
      float value2 = (float)Math.toRadians(quad.rotationDegrees);
      int index = quad.colorPhase;
      float value3 = quad.opacity;
      Vector4f color = CircleColors.rgba(this.leechColor(index, value3));
      Vector4f color2 = CircleColors.rgba(this.leechColor(index + 324, value3));
      Vector4f color3 = CircleColors.rgba(this.leechColor(index + 648, value3));
      Vector4f color4 = CircleColors.rgba(this.leechColor(index + 972, value3));
      float value4 = Mth.cos(value2);
      float value5 = Mth.sin(value2);
      float value6 = (float)(quad.position.y - point.y) + 0.001F;
      this.appendRotatedVertex(vertices, matrix, quad.position, point, -value, -value, value6, value4, value5, 0.0F, 0.0F, color);
      this.appendRotatedVertex(vertices, matrix, quad.position, point, -value, value, value6, value4, value5, 0.0F, 1.0F, color2);
      this.appendRotatedVertex(vertices, matrix, quad.position, point, value, value, value6, value4, value5, 1.0F, 1.0F, color3);
      this.appendRotatedVertex(vertices, matrix, quad.position, point, value, -value, value6, value4, value5, 1.0F, 0.0F, color4);
      return 4;
   }

   private int appendTerrainCell(BufferBuilder vertices, Matrix4f matrix, TerrainCircle circle, Vec3 camera, float radius, float opacity, int row, int column) {
      int index = circle.gridSize;
      int index2 = row * index + column;
      int index3 = row * index + column + 1;
      int index4 = (row + 1) * index + column + 1;
      int index5 = (row + 1) * index + column;
      boolean visible = circle.blocked[index2];
      boolean visible2 = circle.blocked[index3];
      boolean visible3 = circle.blocked[index4];
      boolean visible4 = circle.blocked[index5];
      if (visible && visible2 && visible3 && visible4) {
         return 0;
      }

      float value = circle.heights[index2];
      float value2 = circle.heights[index3];
      float value3 = circle.heights[index4];
      float value4 = circle.heights[index5];
      float value5 = circle.visibility[index2];
      float value6 = circle.visibility[index3];
      float value7 = circle.visibility[index4];
      float value8 = circle.visibility[index5];
      if (visible || visible2 || visible3 || visible4) {
         float value9 = -Float.MAX_VALUE;
         if (!visible) {
            value9 = Math.max(value9, value);
         }

         if (!visible2) {
            value9 = Math.max(value9, value2);
         }

         if (!visible3) {
            value9 = Math.max(value9, value3);
         }

         if (!visible4) {
            value9 = Math.max(value9, value4);
         }

         if (visible) {
            value = value9;
         }

         if (visible2) {
            value2 = value9;
         }

         if (visible3) {
            value3 = value9;
         }

         if (visible4) {
            value4 = value9;
         }
      }

      float value10 = Math.min(Math.min(value, value2), Math.min(value3, value4));
      float value11 = Math.max(Math.max(value, value2), Math.max(value3, value4));
      int index6 = Mth.clamp((int)Math.ceil((value11 - value10) / 0.35F), 1, 8);
      boolean visible5 = Math.abs(value - value4) + Math.abs(value2 - value3) >= Math.abs(value - value2) + Math.abs(value4 - value3);
      int index7 = 0;

      for (int index8 = 0; index8 < index6; index8++) {
         float value12 = (float)index8 / index6;
         float value13 = (float)(index8 + 1) / index6;
         if (visible5) {
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value12, 0.0F, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value12, 1.0F, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value13, 1.0F, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value13, 0.0F, value, value2, value3, value4, value5, value6, value7, value8);
         } else {
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 0.0F, value12, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 0.0F, value13, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 1.0F, value13, value, value2, value3, value4, value5, value6, value7, value8);
            this.appendBilinearTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, 1.0F, value12, value, value2, value3, value4, value5, value6, value7, value8);
         }

         index7 += 4;
      }

      return index7;
   }

   private static boolean isFinite(double value) {
      return Double.isFinite(value) && Math.abs(value) <= 3.0E7;
   }

   private void renderTerrainCircles(CircleFrame frame) {
      if (!this.terrainCircles.isEmpty()) {
         long now = System.currentTimeMillis();
         long lifetime = this.terrainLifetimeMs();
         this.terrainCircles.removeIf(element -> now - element.bornAt >= lifetime || element.hasFaded(now));
         if (!this.terrainCircles.isEmpty()) {
            if (this.terrainTexture == null) {
               this.terrainTexture = mc.getTextureManager().getTexture(TERRAIN_TEXTURE);
            }

            for (int paletteIndex = 0; paletteIndex <= 48; paletteIndex++) {
               this.terrainPalette[paletteIndex] = CircleColors.themeGradient((int)(paletteIndex * 360.0F / 48.0F));
            }

            Vec3 camera = mc.gameRenderer.mainCamera().position();
            Matrix4f matrix = frame.pose().last().pose();
            BufferBuilder vertices = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int vertexCount = 0;

            for (TerrainCircle circle : this.terrainCircles) {
               float progress = Mth.clamp((float)(now - circle.bornAt) / (float)lifetime, 0.0F, 1.0F);
               float radius = easeOutCubic(progress) * circle.radius;
               float opacity = (float)Math.sin(progress * (Math.PI)) * circle.fadeOpacity(now);
               if (!(radius <= 0.001F) && !(opacity <= 0.001F)) {
                  double centerX = circle.position.x;
                  double centerZ = circle.position.z;
                  double radiusSquared = radius * radius;
                  int minRow = Math.max(0, (int)Math.floor((centerX - radius - circle.originX) / circle.gridStep));
                  int minColumn = Math.max(0, (int)Math.floor((centerZ - radius - circle.originZ) / circle.gridStep));
                  int maxRow = Math.min(circle.gridSize - 2, (int)Math.ceil((centerX + radius - circle.originX) / circle.gridStep));
                  int maxColumn = Math.min(circle.gridSize - 2, (int)Math.ceil((centerZ + radius - circle.originZ) / circle.gridStep));

                  for (int row = minRow; row <= maxRow; row++) {
                     double x = circle.originX + row * circle.gridStep;

                     for (int column = minColumn; column <= maxColumn; column++) {
                        double z = circle.originZ + column * circle.gridStep;
                        double deltaX = Mth.clamp(centerX, x, x + circle.gridStep) - centerX;
                        double deltaZ = Mth.clamp(centerZ, z, z + circle.gridStep) - centerZ;
                        if (!(deltaX * deltaX + deltaZ * deltaZ >= radiusSquared)) {
                           vertexCount += this.appendTerrainCell(vertices, matrix, circle, camera, radius, opacity, row, column);
                        }
                     }
                  }

                  float seconds = (float)(now - circle.bornAt) / 1000.0F;

                  for (float[] wall : circle.walls) {
                     vertexCount += this.appendWall(vertices, matrix, circle, camera, radius, opacity, seconds, wall);
                  }
               }
            }

            this.drawTerrainQuads(vertices, vertexCount);
         }
      }
   }

   private static int scaleBrightness(int color, float brightness) {
      brightness = Mth.clamp(brightness, 0.0F, 1.0F);
      return CircleColors.argb(
         (int)(CircleColors.red(color) * brightness),
         (int)(CircleColors.green(color) * brightness),
         (int)(CircleColors.blue(color) * brightness),
         CircleColors.alpha(color)
      );
   }

   private float smootherstep(float progress2) {
      return progress2 * progress2 * progress2 * (progress2 * (progress2 * 6.0F - 15.0F) + 10.0F);
   }

   private void renderWaves(CircleFrame frame) {
      float lifetime = Math.max(1.0F, this.waveLifetime.floatValue());
      long now = System.currentTimeMillis();
      this.waves.removeIf(element -> (float)(now - element.bornAt) / lifetime > 1.0F || !isFinitePosition(element.position));
      if (!this.waves.isEmpty()) {
         this.drawWavePass(frame, lifetime);
      }
   }

   private static boolean isSolidCube(VoxelShape collision) {
      return !collision.isEmpty() && net.minecraft.world.level.block.Block.isShapeFullBlock(collision);
   }

   private void appendGradientQuad(
      BufferBuilder vertices, Matrix4f matrix, Vec3 position, Vec3 camera, float halfSize, float height, float rotation, Vector4f colorA, Vector4f colorB, Vector4f colorC, Vector4f colorD
   ) {
      float cos = Mth.cos(rotation);
      float sin = Mth.sin(rotation);
      float y = (float)(position.y - camera.y) + 0.001F + height;
      this.appendRotatedVertex(vertices, matrix, position, camera, -halfSize, -halfSize, y, cos, sin, 0.0F, 0.0F, colorA);
      this.appendRotatedVertex(vertices, matrix, position, camera, -halfSize, halfSize, y, cos, sin, 0.0F, 1.0F, colorB);
      this.appendRotatedVertex(vertices, matrix, position, camera, halfSize, halfSize, y, cos, sin, 1.0F, 1.0F, colorC);
      this.appendRotatedVertex(vertices, matrix, position, camera, halfSize, -halfSize, y, cos, sin, 1.0F, 0.0F, colorD);
   }

   private static float easeOutCubic(float progress) {
      float progress2 = 1.0F - progress;
      return 1.0F - progress2 * progress2 * progress2;
   }

   private void drawWavePass(CircleFrame frame, float lifetime) {
      RenderTarget target = mc.gameRenderer.mainRenderTarget();
      if (target != null
         && target.getColorTexture() != null
         && target.getDepthTexture() != null
         && !target.getDepthTexture().isClosed()
         && target.width > 0
         && target.height > 0) {
         Vec3 camera = frame.cameraPosition();
         this.inverseViewProjection.set(frame.projection()).mul(frame.view()).invert();
         long now = System.currentTimeMillis();
         float strength = 0.018F * this.waveStrength.floatValue();
         float aspectRatio = (float)target.width / Math.max(1, target.height);
         boolean glow = this.waveGlow.enabled();
         boolean singleColor = this.hasSingleWaveColor();
         int color = singleColor ? this.waveColor(0) : 0;
         this.resizeWaveScene(target.width, target.height);
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         encoder.copyTextureToTexture(target.getColorTexture(), this.waveScene.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);
         MemoryStack stack = MemoryStack.stackPush();

         label152: {
            try {
               Std140Builder uniforms = Std140Builder.onStack(stack, 1648);
               int waveCount = 0;
               float phase = 0.0F;
               float[] waveData = new float[128];
               float[] colors = new float[256];

               for (Wave wave : this.waves) {
                  if (waveCount >= 16) {
                     break;
                  }

                  float progress = (float)(now - wave.bornAt) / lifetime;
                  if (!(progress < 0.0F) && !(progress >= 1.0F) && isFinitePosition(wave.position)) {
                     float expansion = this.waveExpansion(progress);
                     float fadeIn = Mth.clamp(progress / 0.1F, 0.0F, 1.0F);
                     float fadeOut = Mth.clamp((1.0F - progress) / 0.3F, 0.0F, 1.0F);
                     float alpha = fadeIn * fadeOut;
                     float radius = wave.size * 0.75F;
                     float thickness = Math.max(0.15F, radius * this.waveThickness.floatValue() / 2.0F);
                     int dataOffset = waveCount * 8;
                     waveData[dataOffset] = (float)(wave.position.x - camera.x);
                     waveData[dataOffset + 1] = (float)(wave.position.y - camera.y);
                     waveData[dataOffset + 2] = (float)(wave.position.z - camera.z);
                     waveData[dataOffset + 3] = expansion * radius;
                     waveData[dataOffset + 4] = thickness;
                     waveData[dataOffset + 5] = strength * alpha;
                     waveData[dataOffset + 6] = radius;
                     waveData[dataOffset + 7] = alpha;
                     int colorOffset = waveCount * 16;
                     if (singleColor) {
                        writeColor(colors, colorOffset, color);
                        writeColor(colors, colorOffset + 4, color);
                        writeColor(colors, colorOffset + 8, color);
                        writeColor(colors, colorOffset + 12, color);
                     } else {
                        int colorPhase = (int)phase;
                        writeColor(colors, colorOffset, this.waveColor(colorPhase));
                        writeColor(colors, colorOffset + 4, this.waveColor(90 + colorPhase));
                        writeColor(colors, colorOffset + 8, this.waveColor(180 + colorPhase));
                        writeColor(colors, colorOffset + 12, this.waveColor(270 + colorPhase));
                     }

                     phase += 45.0F * (1.0F - progress);
                     waveCount++;
                  }
               }

               if (waveCount == 0) {
                  break label152;
               }

               uniforms.putVec4(waveCount, aspectRatio, (float)(now % 100000L) / 1000.0F, this.turbulence.enabled() ? 0.01F * this.turbulenceStrength.floatValue() : 0.0F);
               uniforms.putVec4(
                  Mth.clamp(this.colorIntensity.floatValue() / 100.0F, 0.0F, 1.0F), Mth.clamp(1.0F + this.saturation.floatValue(), 0.0F, 2.0F), glow ? 1.0F : 0.0F, 0.6F
               );
               uniforms.putVec4(
                  Mth.clamp(this.glowHeight.floatValue() / 100.0F, 0.05F, 5.0F),
                  Mth.clamp(this.glowWidth.floatValue() / 100.0F, 0.05F, 5.0F),
                  Mth.clamp(this.glowIntensity.floatValue() / 100.0F, 0.0F, 1.0F),
                  Mth.clamp(this.glowOpacity.floatValue() / 100.0F, 0.0F, 1.0F)
               );
               uniforms.putMat4f(this.inverseViewProjection);

               for (int dataIndex = 0; dataIndex < 32; dataIndex++) {
                  int dataOffset2 = dataIndex * 4;
                  uniforms.putVec4(waveData[dataOffset2], waveData[dataOffset2 + 1], waveData[dataOffset2 + 2], waveData[dataOffset2 + 3]);
               }

               for (int colorIndex = 0; colorIndex < 64; colorIndex++) {
                  int colorOffset2 = colorIndex * 4;
                  uniforms.putVec4(colors[colorOffset2], colors[colorOffset2 + 1], colors[colorOffset2 + 2], colors[colorOffset2 + 3]);
               }

               ByteBuffer uniformBytes = uniforms.get();
               GpuBuffer uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "jumpFlexWaveUniforms", 128, uniformBytes);

               try {
                  RenderPass pass = encoder.createRenderPass(() -> "jumpFlexWave", target.getColorTextureView(), Optional.empty());

                  try {
                     pass.setPipeline(RenderSystem.getCompiledPipeline(this.wavePipeline));
                     pass.setUniform("SceneSampler", this.waveScene.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                     pass.setUniform("DepthSampler", target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                     pass.setUniform("JumpFlex", uniformBuffer);
                     pass.draw(3, 1, 0, 0);
                  } catch (Throwable failure) {
                     if (pass != null) {
                        try {
                           pass.close();
                        } catch (Throwable failure2) {
                           failure.addSuppressed(failure2);
                        }
                     }

                     throw failure;
                  }

                  if (pass != null) {
                     pass.close();
                  }
               } catch (Throwable failure3) {
                  if (uniformBuffer != null) {
                     try {
                        uniformBuffer.close();
                     } catch (Throwable failure4) {
                        failure3.addSuppressed(failure4);
                     }
                  }

                  throw failure3;
               }

               if (uniformBuffer != null) {
                  uniformBuffer.close();
               }
            } catch (Throwable failure5) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable failure6) {
                     failure5.addSuppressed(failure6);
                  }
               }

               throw failure5;
            }

            if (stack != null) {
               stack.close();
            }

            return;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   private void renderDiamonds(CircleFrame frame) {
      if (!this.circles.isEmpty()) {
         float partialTick = frame.deltaTracker().getGameTimeDeltaPartialTick(true);
         float rotation = ((float)mc.level.getGameTime() + partialTick) * 2.5F;
         Vec3 camera = mc.gameRenderer.mainCamera().position();
         Matrix4f matrix = frame.pose().last().pose();
         BufferBuilder vertices = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         int index = 0;
         Iterator<Circle> iterator = this.circles.iterator();

         while (iterator.hasNext()) {
            Circle circle = (Circle)iterator.next();
            long timestamp = System.currentTimeMillis() - circle.bornAt - circle.delayMs;
            if (timestamp >= 0L) {
               float value = (float)timestamp / 1250.0F;
               if (value >= 1.0F) {
                  iterator.remove();
               } else {
                  float value2 = Mth.clamp(value / 0.34F, 0.0F, 1.0F);
                  float value3 = Mth.clamp((value - 0.66F) / 0.34F, 0.0F, 1.0F);
                  float value4 = this.smootherstep(value2);
                  float value5 = this.smootherstep(value3);
                  float value6 = this.easeOutExponential(Mth.clamp(value / 0.4F, 0.0F, 1.0F));
                  float value7 = Mth.sin(value2 * ((float) Math.PI)) * 0.035F;
                  float value8 = Mth.sin(Mth.clamp((value - 0.28F) / 0.38F, 0.0F, 1.0F) * ((float) Math.PI)) * 0.012F;
                  float value9 = value4 * (1.0F - value5) * circle.opacity;
                  float value10 = 0.14F + value6 * 0.86F + value7 + value8 + value5 * 0.15F + circle.sizeOffset;
                  float value11 = this.radius.floatValue() * 0.55F;
                  float value12 = value11 * value10;
                  Vector4f color = CircleColors.rgba(this.effectColor());
                  Vector4f color2 = new Vector4f(color.x, color.y, color.z, color.w * value9);
                  float value13 = (float)Math.toRadians(rotation + value * 72.0F + (float)circle.delayMs * 0.06F);
                  this.appendQuad(vertices, matrix, circle.position, camera, value12, value12, value13, color2);
                  index += 4;
               }
            }
         }

         this.drawColoredQuads(vertices, index, this.diamondPipeline);
      }
   }

   private int appendWall(BufferBuilder vertices, Matrix4f matrix, TerrainCircle circle, Vec3 camera, float radius, float opacity, float time, float[] wall) {
      double coordinate = circle.position.x;
      double coordinate2 = circle.position.z;
      double coordinate3 = Mth.clamp(coordinate, Math.min(wall[0], wall[2]), Math.max(wall[0], wall[2])) - coordinate;
      double coordinate4 = Mth.clamp(coordinate2, Math.min(wall[1], wall[3]), Math.max(wall[1], wall[3])) - coordinate2;
      if (coordinate3 * coordinate3 + coordinate4 * coordinate4 >= radius * radius * 1.1) {
         return 0;
      }

      int index = 0;

      for (int index2 = 0; index2 < 4; index2++) {
         float value = index2 / 4.0F;
         float value2 = (index2 + 1) / 4.0F;

         for (int index3 = 0; index3 < 6; index3++) {
            float value3 = index3 / 6.0F;
            float value4 = (index3 + 1) / 6.0F;
            this.appendWallVertex(vertices, matrix, circle, camera, radius, opacity, time, wall, value, value3);
            this.appendWallVertex(vertices, matrix, circle, camera, radius, opacity, time, wall, value2, value3);
            this.appendWallVertex(vertices, matrix, circle, camera, radius, opacity, time, wall, value2, value4);
            this.appendWallVertex(vertices, matrix, circle, camera, radius, opacity, time, wall, value, value4);
            index += 4;
         }
      }

      return index;
   }

   private void appendTerrainVertex(
      BufferBuilder vertices,
      Matrix4f matrix,
      TerrainCircle circle,
      Vec3 camera,
      float radius,
      float opacity,
      int row,
      int column,
      float value,
      float value2,
      float value3,
      float value4
   ) {
      double coordinate = circle.originX + (row + value) * circle.gridStep;
      double coordinate2 = circle.originZ + (column + value2) * circle.gridStep;
      double coordinate3 = value3 + 0.04F;
      double coordinate4 = coordinate - circle.position.x;
      double coordinate5 = coordinate2 - circle.position.z;
      float value5 = (float)Math.sqrt(coordinate4 * coordinate4 + coordinate5 * coordinate5);
      float value6 = value5 / Math.max(1.0E-4F, radius);
      float value7 = Math.min(value6, 1.0F);
      float value8 = value5 > 1.0E-4F ? (float)(coordinate4 / value5) : 0.0F;
      float value9 = value5 > 1.0E-4F ? (float)(coordinate5 / value5) : 0.0F;
      float value10 = 0.5F + value8 * value7 * 0.5F;
      float value11 = 0.5F + value9 * value7 * 0.5F;
      float value12 = 1.0F - smoothstep(1.05F, 1.9F, value3 - (float)circle.position.y);
      float value13 = (0.5F + 0.5F * value7) * (1.0F - smoothstep(0.72F, 1.0F, value6));
      float value14 = opacity * value4 * value12 * value13;
      float value15 = (float)Math.atan2(coordinate5, coordinate4);
      int index = Math.floorMod(Math.round(value15 / ((float) (Math.PI * 2)) * 48.0F), 48);
      int index2 = this.themeColor.enabled() ? this.terrainPalette[index] : this.effectColor();
      this.appendVertex(
         vertices,
         matrix,
         (float)(coordinate - camera.x),
         (float)(coordinate3 - camera.y),
         (float)(coordinate2 - camera.z),
         value10,
         value11,
         CircleColors.rgba(multiplyOpacity(index2, value14))
      );
   }

   private void detectJump() {
      boolean onGround = mc.player.onGround();
      boolean jumpPressed = mc.options.keyJump.isDown();
      boolean pressedOnGround = jumpPressed && !this.wasJumpPressed && onGround;
      boolean startedRising = this.wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0.01;
      if (onGround && !this.wasOnGround) {
         this.jumpEmitted = false;
      }

      if ((pressedOnGround || startedRising) && !this.jumpEmitted) {
         this.spawnEffect();
         this.jumpEmitted = true;
      }

      this.wasOnGround = onGround;
      this.wasJumpPressed = jumpPressed;
   }

   private void limitTerrainCircles() {
      int index = 0;

      for (TerrainCircle circle : this.terrainCircles) {
         if (!circle.fading) {
            index++;
         }
      }

      for (TerrainCircle circle2 : this.terrainCircles) {
         if (index < 8) {
            break;
         }

         if (!circle2.fading) {
            circle2.beginFade();
            index--;
         }
      }
   }

   private static int terrainGridSize(float progress) {
      float progress2 = progress + 0.5F;
      return Math.max(24, Math.min(56, (int)Math.ceil(2.0 * progress2 * 8.0) + 1));
   }

   private void renderLeeches(CircleFrame frame) {
      if (!this.leeches.isEmpty()) {
         long now = System.currentTimeMillis();
         float lifetime = this.leechLifetime.floatValue();
         this.leeches.removeIf(element -> (float)(now - element.bornAt) / lifetime >= 1.0F);
         if (!this.leeches.isEmpty()) {
            Vec3 camera = mc.gameRenderer.mainCamera().position();
            Matrix4f matrix = frame.pose().last().pose();
            float range = this.leechRange.floatValue();
            boolean deep = this.deepLight.enabled();
            String animation = this.leechAnimation.get();
            HashMap<Integer, List<LeechQuad>> batches = new HashMap<>();
            ArrayList<LeechQuad> glows = new ArrayList<>();

            for (Leech leech : this.leeches) {
               float progress = (float)(now - leech.bornAt) / lifetime;
               float remaining = 1.0F - progress;
               float pulse = trianglePulse(1.0F - remaining);
               float alpha;
               float size;
               float rotation;
               switch (animation) {
                  case "FadeInOutRotate":
                     alpha = easeOutCircular(trianglePulse(1.0F - remaining));
                     if (remaining < 0.5F) {
                        alpha *= easeInOutExponential(alpha);
                     }

                     float expansionProgress = pulse;
                     size = (remaining > 0.5F ? easeOutElastic(expansionProgress * expansionProgress) : easeOutBack(expansionProgress)) * range;
                     rotation = (float)(easeInOutElastic(expansionProgress) * 90.0 / (1.0 + expansionProgress));
                     break;
                  case "FadeInExponent":
                     alpha = easeInOutQuadratic(pulse);
                     float overshoot = 1.70158F;
                     float overshootScale = overshoot + 1.0F;
                     size = (float)(1.0 + overshootScale * Math.pow(remaining - 1.0, 3.0) + overshoot * Math.pow(remaining - 1.0, 2.0)) * range;
                     rotation = 0.0F;
                     break;
                  default:
                     alpha = progress < 0.5F ? easeInOutQuadratic(pulse) : pulse;
                     size = (1.0F - easeInOutQuadratic(progress)) * range;
                     rotation = (float)(easeInOutElastic(pulse) * -90.0);
               }

               if (!(alpha < 0.003921569F) && !(size < 0.02F)) {
                  float frameProgress = (remaining + 0.6F) % 1.0F;
                  if (frameProgress < 0.0F) {
                     frameProgress++;
                  }

                  int frameIndex = Mth.clamp((int)Math.min(frameProgress * 199.5F, 199.0F), 0, 199);
                  int colorPhase = leech.index * 30;
                  LeechQuad colors = new LeechQuad(leech.position, size, rotation, alpha, colorPhase, frameIndex);
                  batches.computeIfAbsent(frameIndex, element2 -> new ArrayList<>()).add(colors);
                  if (deep) {
                     glows.add(colors);
                  }
               }
            }

            for (Entry<Integer, List<LeechQuad>> batch : batches.entrySet()) {
               BufferBuilder vertices = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
               int vertexCount = 0;

               for (LeechQuad quad : batch.getValue()) {
                  vertexCount += this.appendLeechQuad(vertices, matrix, camera, quad);
               }

               this.drawTexturedQuads(vertices, LEECH_FRAMES[batch.getKey()], vertexCount);
            }

            if (deep) {
               for (LeechQuad glow : glows) {
                  float glowAlpha = 0.15F;
                  float glowScale = easeInOutQuadratic(glow.opacity);
                  float layers = Math.max(1.0F, 40.0F * glowScale);
                  float height = glow.size * glowScale / 4.0F;
                  float width = glow.size / 12.0F;
                  float rotationRadians = (float)Math.toRadians(glow.rotationDegrees);
                  BufferBuilder glowVertices = CircleGeometry.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                  int glowVertexCount = 0;
                  int quad2 = glow.colorPhase;
                  int[] colors2 = new int[]{
                     this.leechColor(quad2, 1.0F), this.leechColor(quad2 + 324, 1.0F), this.leechColor(quad2 + 648, 1.0F), this.leechColor(quad2 + 972, 1.0F)
                  };

                  for (int layer = 1; layer < (int)layers; layer++) {
                     float layerProgress = layer / layers;
                     float layerAlpha = Mth.lerp(layerProgress * ((layers - 1.0F) / layers), glowScale * glowAlpha, 0.0F);
                     if (!(layerAlpha * 255.0F < 1.0F)) {
                        float layerSize = glow.size + easeOutCircular(trianglePulse(layerProgress - 1.5F / layers)) * width;
                        float layerHeight = height * layer / layers - height / layers;
                        this.appendGradientQuad(
                           glowVertices,
                           matrix,
                           glow.position,
                           camera,
                           layerSize * 0.5F,
                           layerHeight,
                           rotationRadians,
                           CircleColors.rgba(scaleBrightness(colors2[0], layerAlpha)),
                           CircleColors.rgba(scaleBrightness(colors2[1], layerAlpha)),
                           CircleColors.rgba(scaleBrightness(colors2[2], layerAlpha)),
                           CircleColors.rgba(scaleBrightness(colors2[3], layerAlpha))
                        );
                        glowVertexCount += 4;
                     }
                  }

                  this.drawTexturedQuads(glowVertices, LEECH_FRAMES[glow.frameIndex], glowVertexCount);
               }
            }
         }
      }
   }

   private static float easeInOutQuadratic(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      return progress < 0.5F ? 2.0F * progress * progress : 1.0F - (float)Math.pow(-2.0F * progress + 2.0F, 2.0) / 2.0F;
   }

   private static Identifier[] leechFrameTextures() {
      Identifier[] frames = new Identifier[200];

      for (int index = 0; index < 200; index++) {
         frames[index] = Identifier.fromNamespaceAndPath("hustle263", "textures/jumpcircles/leeches/frames/circleframe_" + (index + 1) + ".png");
      }

      return frames;
   }

   private void drawTerrainQuads(BufferBuilder vertices, int index) {
      MeshData mesh = vertices.build();
      if (mesh != null) {
         if (index == 0) {
            mesh.close();
         } else {
            GpuBuffer buffer = null;

            try {
               DrawState drawState = mesh.drawState();
               int index2 = drawState.indexCount();
               AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
               GpuBuffer buffer2 = indices.getBuffer(index2);
               IndexType indexType = indices.type();
               buffer = RenderSystem.getDevice().createBuffer(() -> "jumpCircle2", 32, mesh.vertexBuffer());
               RenderTarget target = mc.gameRenderer.mainRenderTarget();
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               AbstractTexture texture = this.terrainTexture != null ? this.terrainTexture : mc.getTextureManager().getTexture(TERRAIN_TEXTURE);
               if (this.glass.enabled()) {
                  TextureTarget scene = this.resizeDistortionScene(target);
                  encoder.copyTextureToTexture(target.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, target.width, target.height);
                  RenderPass pass = encoder.createRenderPass(
                     () -> "jumpCircleHustleGlass", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                  );

                  try {
                     RenderSystem.bindDefaultUniforms(pass);
                     pass.setUniform("Sampler0", scene.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                     pass.setPipeline(RenderSystem.getCompiledPipeline(this.glassPipeline));
                     pass.setVertexBuffer(0, buffer.slice());
                     pass.setIndexBuffer(buffer2, indexType);
                     pass.drawIndexed(index2, 1, 0, 0, 0);
                  } catch (Throwable failure) {
                     if (pass != null) {
                        try {
                           pass.close();
                        } catch (Throwable failure2) {
                           failure.addSuppressed(failure2);
                        }
                     }

                     throw failure;
                  }

                  if (pass != null) {
                     pass.close();
                  }
               }

               RenderPass pass2 = encoder.createRenderPass(
                  () -> "jumpCircle2", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
               );

               try {
                  RenderSystem.bindDefaultUniforms(pass2);
                  pass2.setUniform("Sampler0", texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                  pass2.setPipeline(RenderSystem.getCompiledPipeline(this.terrainPipeline));
                  pass2.setVertexBuffer(0, buffer.slice());
                  pass2.setIndexBuffer(buffer2, indexType);
                  pass2.drawIndexed(index2, 1, 0, 0, 0);
               } catch (Throwable failure3) {
                  if (pass2 != null) {
                     try {
                        pass2.close();
                     } catch (Throwable failure4) {
                        failure3.addSuppressed(failure4);
                     }
                  }

                  throw failure3;
               }

               if (pass2 != null) {
                  pass2.close();
               }
            } finally {
               if (buffer != null) {
                  buffer.close();
               }

               mesh.close();
            }
         }
      }
   }

   private void appendQuad(BufferBuilder vertices, Matrix4f matrix, Vec3 position, Vec3 camera, float halfWidth, float halfHeight, float rotation, Vector4f color) {
      float value = Mth.cos(rotation);
      float value2 = Mth.sin(rotation);
      this.appendFlatVertex(vertices, matrix, position, camera, -halfWidth, -halfHeight, value, value2, 0.0F, 0.0F, color);
      this.appendFlatVertex(vertices, matrix, position, camera, halfWidth, -halfHeight, value, value2, 1.0F, 0.0F, color);
      this.appendFlatVertex(vertices, matrix, position, camera, halfWidth, halfHeight, value, value2, 1.0F, 1.0F, color);
      this.appendFlatVertex(vertices, matrix, position, camera, -halfWidth, halfHeight, value, value2, 0.0F, 1.0F, color);
   }

   private static int multiplyOpacity(int color, float alpha) {
      int index = Math.round((color >>> 24 & 0xFF) * alpha);
      index = Mth.clamp(index, 0, 255);
      return index << 24 | color & 16777215;
   }

   private float sampleGroundHeight(Entity entity, double x, double z, double y) {
      double startY = y + 2.8F + 0.6;

      for (int attempt = 0; attempt < 4; attempt++) {
         BlockHitResult hit = this.traceGround(entity, x, z, startY, y - 4.0);
         if (hit.getType() != Type.BLOCK) {
            return (float)y;
         }

         float surfaceY = (float)hit.getLocation().y;
         if (hit.isInside()) {
            double lowerStart = y + 1.0;
            BlockHitResult lowerHit = this.traceGround(entity, x, z, lowerStart, y - 4.0);
            if (lowerHit.getType() != Type.BLOCK) {
               return (float)y;
            }

            float lowerHeight = (float)lowerHit.getLocation().y;
            boolean inside = lowerHit.isInside();
            if (!inside && lowerHeight > lowerStart - 0.05) {
               BlockPos block = BlockPos.containing(x, lowerStart + 0.5, z);
               inside = !mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty();
            }

            if (inside) {
               return (float)(y + 2.8F + 4.0);
            }

            hit = lowerHit;
            surfaceY = lowerHeight;
         }

         BlockPos below = hit.getBlockPos().below();
         boolean overhang = surfaceY - y > 0.6 && mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty();
         if (!overhang) {
            return this.collisionSurfaceHeight(hit.getBlockPos(), x, z, surfaceY);
         }

         startY = surfaceY - 0.05;
      }

      return (float)y;
   }

   public void tick(net.minecraft.client.Minecraft client) {
      if (mc.level != null && mc.player != null) {
         if (this.circleTexture == null) {
            this.circleTexture = mc.getTextureManager().getTexture(CIRCLE_TEXTURE);
         }

         this.detectJump();
      } else {
         this.circles.clear();
         this.lightningBursts.clear();
         this.leeches.clear();
         this.terrainCircles.clear();
         this.waves.clear();
         this.wasOnGround = true;
         this.wasJumpPressed = false;
         this.jumpEmitted = false;
      }
   }

   private int waveColor(int phase) {
      if (this.waveColors.is("Радуга")) {
         float value = ((float)System.currentTimeMillis() / 8.0F + phase) % 360.0F / 360.0F;
         if (value < 0.0F) {
            value++;
         }

         return Color.HSBtoRGB(value, 1.0F, 1.0F) | -16777216;
      } else if (!this.waveColors.is("Клиент") && this.waveColors.is("Свой")) {
         int index = this.waveColorStart.color() | -16777216;
         int index2 = this.secondWaveColor.enabled() ? this.waveColorEnd.color() | -16777216 : index;
         if (index == index2) {
            return index;
         }

         int index3 = (int)((System.currentTimeMillis() / 8L + phase) % 360L);
         if (index3 >= 180) {
            index3 = 360 - index3;
         }

         return CircleColors.lerp(index, index2, index3 / 180.0F);
      } else {
         return CircleColors.saturatedPulse(
            CircleColors.primary(), CircleColors.secondary(), phase, 8
         );
      }
   }

   private static void smoothTerrainGrid(float[] samples, float[] samples2, boolean[] flags, int index) {
      float[] samples3 = (float[])samples.clone();
      float[] samples4 = (float[])samples2.clone();

      for (int index2 = 0; index2 < index; index2++) {
         for (int index3 = 0; index3 < index; index3++) {
            int index4 = index2 * index + index3;
            if (!flags[index4]) {
               float value = samples3[index4];
               float value2 = samples4[index4];

               for (int index5 = -1; index5 <= 1; index5++) {
                  for (int index6 = -1; index6 <= 1; index6++) {
                     if (index5 != 0 || index6 != 0) {
                        int index7 = index2 + index5;
                        int index8 = index3 + index6;
                        if (index7 >= 0 && index8 >= 0 && index7 < index && index8 < index) {
                           int index9 = index7 * index + index8;
                           if (!flags[index9] && samples3[index9] > value + 0.2F) {
                              value = samples3[index9];
                              value2 = Math.max(value2, samples4[index9]);
                           }
                        }
                     }
                  }
               }

               samples[index4] = value;
               samples2[index4] = value2;
            }
         }
      }
   }

   private void drawColoredQuads(BufferBuilder vertices, int vertexCount, RenderPipeline pipeline) {
      MeshData mesh = vertices.build();
      if (mesh != null) {
         if (vertexCount == 0) {
            mesh.close();
         } else {
            GpuBuffer vertexBuffer = null;

            try {
               DrawState drawState = mesh.drawState();
               int index = drawState.indexCount();
               AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
               GpuBuffer buffer = indices.getBuffer(index);
               IndexType indexType = indices.type();
               vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "jumpShapeVertices", 32, mesh.vertexBuffer());
               RenderTarget target = mc.gameRenderer.mainRenderTarget();
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               RenderPass pass = encoder.createRenderPass(
                  () -> "jumpShape", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()
               );

               try {
                  RenderSystem.bindDefaultUniforms(pass);
                  pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                  pass.setVertexBuffer(0, vertexBuffer.slice());
                  pass.setIndexBuffer(buffer, indexType);
                  pass.drawIndexed(index, 1, 0, 0, 0);
               } catch (Throwable failure) {
                  if (pass != null) {
                     try {
                        pass.close();
                     } catch (Throwable failure2) {
                        failure.addSuppressed(failure2);
                     }
                  }

                  throw failure;
               }

               if (pass != null) {
                  pass.close();
               }
            } finally {
               if (vertexBuffer != null) {
                  vertexBuffer.close();
               }

               mesh.close();
            }
         }
      }
   }

   private long terrainLifetimeMs() {
      return Math.round(this.terrainLifetime.floatValue()) * 100L;
   }

   private static float easeOutElastic(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      if (progress != 0.0F && progress != 1.0F) {
         float progress2 = ((float) (Math.PI * 2.0 / 3.0));
         return (float)(Math.pow(2.0, -10.0F * progress) * Math.sin((progress * 10.0F - 0.75) * progress2) + 1.0);
      } else {
         return progress;
      }
   }

   private void appendRotatedVertex(
      BufferBuilder vertices,
      Matrix4f matrix,
      Vec3 position,
      Vec3 camera,
      float x,
      float z,
      float y,
      float cos,
      float sin,
      float u,
      float v,
      Vector4f color
   ) {
      float rotatedX = x * cos - z * sin;
      float rotatedZ = x * sin + z * cos;
      this.appendVertex(vertices, matrix, (float)(position.x - camera.x) + rotatedX, y, (float)(position.z - camera.z) + rotatedZ, u, v, color);
   }

   private static float surfaceNoise(double x, double y, double z, float time) {
      float first = Mth.sin((float)(x * 2.3 + z * 1.7 + y * 1.1) + time * 0.9F);
      float second = Mth.sin((float)(x * 1.3 - z * 2.9 + y * 2.6) - time * 1.3F);
      float third = Mth.sin((float)(x * 3.7 + z * 0.9 - y * 1.8) + time * 0.6F);
      return (first + second + third) / 3.0F;
   }

   private void appendVertex(BufferBuilder vertices, Matrix4f matrix, float x, float y, float z, float u, float v, Vector4f color) {
      vertices.addVertex(matrix, x, y, z).setUv(u, v).setColor(color.x, color.y, color.z, color.w);
   }

   private static float trianglePulse(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      return progress > 0.5F ? (1.0F - progress) * 2.0F : progress * 2.0F;
   }

   private void appendBilinearTerrainVertex(
      BufferBuilder vertices,
      Matrix4f matrix,
      TerrainCircle circle,
      Vec3 camera,
      float radius,
      float opacity,
      int row,
      int column,
      float value,
      float value2,
      float value3,
      float value4,
      float value5,
      float value6,
      float value7,
      float value8,
      float value9,
      float value10
   ) {
      float value11 = Mth.lerp(value, Mth.lerp(value2, value3, value4), Mth.lerp(value2, value6, value5));
      float value12 = Mth.lerp(value, Mth.lerp(value2, value3, value4), Mth.lerp(value2, value6, value5));
      float value13 = Mth.lerp(value, Mth.lerp(value2, value7, value8), Mth.lerp(value2, value10, value9));
      this.appendTerrainVertex(vertices, matrix, circle, camera, radius, opacity, row, column, value, value2, value12, value13);
   }

   private void spawnEffect() {
      long now = System.currentTimeMillis();
      if (now - this.lastSpawnAt < 80L) return;
      this.lastSpawnAt = now;
      Vec3 position = new Vec3(mc.player.getX(), mc.player.getY() + 0.001, mc.player.getZ());
      this.circles.add(new Circle(position, 0L, 1.0F, 0.0F));
   }

   private TextureTarget resizeDistortionScene(RenderTarget target) {
      if (this.distortionScene == null) {
         this.distortionScene = new TextureTarget("jump-circle-distortion-scene", target.width, target.height, GpuFormat.RGBA8_UNORM, null);
      } else if (this.distortionScene.width != target.width || this.distortionScene.height != target.height) {
         this.distortionScene.resize(target.width, target.height);
      }

      return this.distortionScene;
   }

   private void appendFlatVertex(
      BufferBuilder vertices, Matrix4f matrix, Vec3 position, Vec3 camera, float x, float z, float cos, float sin, float u, float v, Vector4f color
   ) {
      float rotatedX = x * cos - z * sin;
      float rotatedZ = x * sin + z * cos;
      this.appendVertex(vertices, matrix, (float)(position.x - camera.x) + rotatedX, (float)(position.y - camera.y) + 0.04F, (float)(position.z - camera.z) + rotatedZ, u, v, color);
   }

   private static float easeInOutExponential(float progress) {
      progress = Mth.clamp(progress, 0.0F, 1.0F);
      if (progress != 0.0F && progress != 1.0F) {
         return progress < 0.5F
            ? (float)(Math.pow(2.0, 20.0F * progress - 10.0F) / 2.0)
            : (float)((2.0 - Math.pow(2.0, -20.0F * progress + 10.0F)) / 2.0);
      } else {
         return progress;
      }
   }

   private boolean hasSingleWaveColor() {
      if (!this.waveColors.is("Свой")) {
         return false;
      } else {
         return !this.secondWaveColor.enabled() ? true : this.waveColorStart.color() == this.waveColorEnd.color();
      }
   }

   public void render(CircleFrame frame) {
      if (!enabled()) return;
      if (mc.level != null && mc.player != null) {
         this.detectJump();
         this.renderCircles(frame);
      } else {
         this.circles.clear();
         this.lightningBursts.clear();
         this.leeches.clear();
         this.terrainCircles.clear();
         this.waves.clear();
         this.wasOnGround = true;
         this.wasJumpPressed = false;
         this.jumpEmitted = false;
      }
   }

   @Override
   public void close() { onDisable(); }

   protected void onDisable() {
      this.circles.clear();
      this.lightningBursts.clear();
      this.leeches.clear();
      this.terrainCircles.clear();
      this.waves.clear();
      this.wasOnGround = true;
      this.wasJumpPressed = false;
      this.jumpEmitted = false;
      this.lastSpawnAt = 0L;
      this.circleTexture = null;
      this.terrainTexture = null;
      if (this.distortionScene != null) {
         this.distortionScene.destroyBuffers();
         this.distortionScene = null;
      }

      if (this.waveScene != null) {
         this.waveScene.destroyBuffers();
         this.waveScene = null;
      }
   }

   @Override
   protected void onEnable() {
      this.circles.clear();
      this.lightningBursts.clear();
      this.leeches.clear();
      this.terrainCircles.clear();
      this.waves.clear();
      this.wasOnGround = true;
      this.wasJumpPressed = false;
      this.jumpEmitted = false;
      this.lastSpawnAt = 0L;
      this.circleTexture = null;
      this.terrainTexture = null;
   }

   private int appendLightningSegment(BufferBuilder vertices, Matrix4f matrix, Vec3 point, Vec3 point2, Vec3 point3, Vec3 point4, float value, float value2, Vector4f color) {
      double coordinate = point4.x - point3.x;
      double coordinate2 = point4.z - point3.z;
      double coordinate3 = Math.sqrt(coordinate * coordinate + coordinate2 * coordinate2);
      if (coordinate3 < 1.0E-4) {
         return 0;
      }

      double coordinate4 = -coordinate2 / coordinate3;
      double coordinate5 = coordinate / coordinate3;
      double coordinate6 = point.x - point2.x;
      double coordinate7 = point.y - point2.y + 0.03;
      double coordinate8 = point.z - point2.z;
      float value3 = (float)(coordinate6 + point3.x + coordinate4 * value * 0.5);
      float value4 = (float)(coordinate8 + point3.z + coordinate5 * value * 0.5);
      float value5 = (float)(coordinate6 + point3.x - coordinate4 * value * 0.5);
      float value6 = (float)(coordinate8 + point3.z - coordinate5 * value * 0.5);
      float value7 = (float)(coordinate6 + point4.x + coordinate4 * value2 * 0.5);
      float value8 = (float)(coordinate8 + point4.z + coordinate5 * value2 * 0.5);
      float value9 = (float)(coordinate6 + point4.x - coordinate4 * value2 * 0.5);
      float value10 = (float)(coordinate8 + point4.z - coordinate5 * value2 * 0.5);
      float value11 = (float)(coordinate7 + point3.y);
      float value12 = (float)(coordinate7 + point4.y);
      vertices.addVertex(matrix, value3, value11, value4).setUv(0.0F, 0.0F).setColor(color.x, color.y, color.z, color.w);
      vertices.addVertex(matrix, value7, value12, value8).setUv(1.0F, 0.0F).setColor(color.x, color.y, color.z, color.w);
      vertices.addVertex(matrix, value9, value12, value10).setUv(1.0F, 1.0F).setColor(color.x, color.y, color.z, color.w);
      vertices.addVertex(matrix, value5, value11, value6).setUv(0.0F, 1.0F).setColor(color.x, color.y, color.z, color.w);
      return 4;
   }

   private float easeOutExponential(float progress2) {
      float value = 1.0F - progress2;
      return 1.0F - value * value * value;
   }

   private int leechColor(int phase, float alpha) {
      String name = this.leechColors.get();

      int index = switch (name) {
         case "Клиент" -> CircleColors.opacity(CircleColors.primary(), alpha);
         case "Свой" -> CircleColors.opacity(this.leechColorStart.color(), CircleColors.alpha(this.leechColorStart.color()) / 255.0F * alpha);
         case "Свой+Fade" -> {
            int index2 = CircleColors.opacity(this.leechColorStart.color(), CircleColors.alpha(this.leechColorStart.color()) / 255.0F * alpha);
            int index3 = CircleColors.opacity(this.leechColorEnd.color(), CircleColors.alpha(this.leechColorEnd.color()) / 255.0F * alpha);
            float value = (Mth.sin(phase * 0.08F + (float)System.currentTimeMillis() / 300.0F) + 1.0F) * 0.5F;
            yield CircleColors.lerp(index2, index3, value);
         }
         default -> {
            float value2 = ((float)System.currentTimeMillis() / 8.0F + phase) % 360.0F / 360.0F;
            yield CircleColors.opacity(Color.HSBtoRGB(value2, 0.65F, 1.0F) | -16777216, alpha);
         }
      };
      return CircleColors.lerp(index, CircleColors.opacity(-1, CircleColors.alpha(index) / 255.0F), 0.125F);
   }

   private static boolean isFinitePosition(Vec3 position) {
      return isFinite(position.x) && isFinite(position.y) && isFinite(position.z);
   }

   private float collisionSurfaceHeight(BlockPos block, double x, double z, float fallback) {
      double coordinate = Mth.clamp(x - block.getX(), 0.0, 0.999);
      double coordinate2 = Mth.clamp(z - block.getZ(), 0.0, 0.999);

      for (BlockPos block2 : new BlockPos[]{block, block.above()}) {
         VoxelShape collision = mc.level.getBlockState(block2).getShape(mc.level, block2);
         if (!collision.isEmpty()) {
            double coordinate3 = block2.getY() + collision.bounds().maxY;
            if (coordinate3 > fallback && coordinate3 - fallback <= 0.3) {
               fallback = (float)coordinate3;
            }
         }
      }

      return fallback;
   }

   private static final class Leech {
      public final Vec3 position;
      public final long bornAt = System.currentTimeMillis();
      public final int index;

      public Leech(Vec3 position, int index) {
         this.position = position;
         this.index = index;
      }
   }

   private static final class LightningBurst {

      public final Vec3 position;
      public final long bornAt;
      public final float seed;
      public final List<LightningBranch> branches;

      public static Vec3 followTerrain(Vec3 origin, Vec3 offset) {
         if (mc.level == null) {
            return offset;
         }

         double x = origin.x + offset.x;
         double z = origin.z + offset.z;
         int surfaceY = mc.level.getHeight(Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
         double height = Mth.clamp(surfaceY - origin.y, -1.4F, 1.4F);
         return new Vec3(offset.x, height, offset.z);
      }

      public static List<LightningBranch> generateBranches(Vec3 origin, float radius) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         int branchCount = 7 + random.nextInt(3);
         ArrayList branches = new ArrayList(branchCount * 2);

         for (int index = 0; index < branchCount; index++) {
            double angle = (Math.PI * 2) / branchCount * index + random.nextDouble(-0.26, 0.26);
            double length = (0.85 + random.nextDouble() * 0.7) * radius;
            byte segments = 6;
            Vec3[] points = generateBranch(origin, Vec3.ZERO, angle, length, segments, random);
            branches.add(new LightningBranch(points, 0.0F));
            if (points.length > 3 && random.nextFloat() < 0.55F) {
               int forkIndex = 2 + random.nextInt(points.length - 3);
               double forkAngle = angle + (random.nextBoolean() ? 1 : -1) * (0.55 + random.nextDouble() * 0.45);
               double forkLength = length * (0.3 + random.nextDouble() * 0.25);
               Vec3[] forkPoints = generateBranch(origin, points[forkIndex], forkAngle, forkLength, 3, random);
               float delay = Mth.clamp((float)forkIndex / segments, 0.0F, 0.75F);
               branches.add(new LightningBranch(forkPoints, delay));
            }
         }

         return branches;
      }

      public static Vec3[] generateBranch(Vec3 origin, Vec3 start, double angle, double length, int segments, ThreadLocalRandom random) {
         Vec3[] points = new Vec3[segments + 1];
         points[0] = followTerrain(origin, start);
         double heading = angle;
         double step = length / segments;
         double x = start.x;
         double z = start.z;

         for (int segment = 1; segment <= segments; segment++) {
            heading += random.nextDouble(-0.16, 0.16);
            double distance = step * (0.85 + random.nextDouble() * 0.3);
            x += Math.cos(heading) * distance;
            z += Math.sin(heading) * distance;
            points[segment] = followTerrain(origin, new Vec3(x, 0.0, z));
         }

         return points;
      }

      public LightningBurst(Vec3 position, float radius) {
         this.position = position;
         this.bornAt = System.currentTimeMillis();
         this.seed = ThreadLocalRandom.current().nextFloat() * 1000.0F;
         this.branches = generateBranches(position, radius);
      }
   }

   private static final class LeechQuad {
      public final Vec3 position;
      public final float size;
      public final float rotationDegrees;
      public final float opacity;
      public final int colorPhase;
      public final int frameIndex;

      public LeechQuad(Vec3 position, float size, float rotationDegrees, float opacity, int colorPhase, int frameIndex) {
         this.position = position;
         this.size = size;
         this.rotationDegrees = rotationDegrees;
         this.opacity = opacity;
         this.colorPhase = colorPhase;
         this.frameIndex = frameIndex;
      }
   }

   private static final class Circle {
      public final Vec3 position;
      public final long bornAt;
      public final long delayMs;
      public final float opacity;
      public final float sizeOffset;

      public Circle(Vec3 position, long delayMs, float opacity, float sizeOffset) {
         this.position = position;
         this.bornAt = System.currentTimeMillis();
         this.delayMs = delayMs;
         this.opacity = opacity;
         this.sizeOffset = sizeOffset;
      }
   }

   private static final class Wave {
      public final Vec3 position;
      public final float size;
      public final long bornAt = System.currentTimeMillis();

      public Wave(Vec3 position, float size) {
         this.position = position;
         this.size = size;
      }
   }

   private static final class TerrainCircle {
      public final Vec3 position;
      public final float radius;
      public final double originX;
      public final double originZ;
      public final double gridStep;
      public final int gridSize;
      public final float[] heights;
      public final float[] visibility;
      public final boolean[] blocked;
      public final List<float[]> walls;
      public final long bornAt = System.currentTimeMillis();
      public boolean fading;
      public long fadeStartedAt;

      public boolean hasFaded(long now) {
         return this.fading && now - this.fadeStartedAt >= 350L;
      }

      public void beginFade() {
         if (!this.fading) {
            this.fading = true;
            this.fadeStartedAt = System.currentTimeMillis();
         }
      }

      public TerrainCircle(
         Vec3 position, float radius, double originX, double originZ, double gridStep, int gridSize, float[] heights, float[] visibility, boolean[] blocked, List<float[]> walls
      ) {
         this.position = position;
         this.radius = radius;
         this.originX = originX;
         this.originZ = originZ;
         this.gridStep = gridStep;
         this.gridSize = gridSize;
         this.heights = heights;
         this.visibility = visibility;
         this.blocked = blocked;
         this.walls = walls;
      }

      public float fadeOpacity(long now) {
         if (!this.fading) {
            return 1.0F;
         }

         float progress = Mth.clamp((float)(now - this.fadeStartedAt) / 350.0F, 0.0F, 1.0F);
         return 1.0F - progress;
      }
   }
}
