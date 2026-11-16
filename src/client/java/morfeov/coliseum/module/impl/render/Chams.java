package morfeov.coliseum.module.impl.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;


import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.render.chams.*;

import net.minecraft.client.renderer.RenderPipelines;

@ModuleInfo(id="chams",name="Chams",description="Обводка моделей игроков из Coliseum",category=Category.RENDER)
public class Chams extends Module {
   public static final String OUTLINE = "Обводка";
   public static final Map<TypeKey, RenderType> TYPES = new HashMap<>();
   public static final RenderPipeline[] PIPELINES = new RenderPipeline[4];
   public static Chams instance;
   public static boolean worldSubmission;
   public static boolean pipelineFailed;
   /** Invisible avatar whose model is only captured for the outline (never drawn). */
   public static Object hiddenOutline;
   public final ModeSetting mode=new ModeSetting("Режим","Обводка","Обводка");
   public final TargetSelection targets=new TargetSelection();
   public final BooleanSetting themeColor=add(new BooleanSetting("Цвет из темы",false));
   public final ColorSetting color=add(new ColorSetting("Цвет",-8869121));
   public final BooleanSetting indication=add(new BooleanSetting("Индикация урона",true));
   public final ColorSetting damageColor=add(new ColorSetting("Цвет урона",-1711322283));
   public final BooleanSetting throughWalls=add(new BooleanSetting("Сквозь стены",false));
   public final BooleanSetting throughArmor=add(new BooleanSetting("Сквозь броню",false));
   public final NumberSetting fillOpacity=add(new NumberSetting("Плотность заливки",100,0,100,1));
   public final BooleanSetting glow=add(new BooleanSetting("Свечение",true));
   public final NumberSetting glowRadius=add(new NumberSetting("Радиус свечения",24,2,64,1));
   public final NumberSetting glowStrength=add(new NumberSetting("Сила свечения",1.6,.1,3,.1));
   public final BooleanSetting additive=add(new BooleanSetting("Аддитивный блендинг",true));
   public final ColorSetting blurColor=add(new ColorSetting("Цвет блюра",0xffa894ff));
   public final NumberSetting blurRadius=add(new NumberSetting("Сила блюра",5,1,12,1));
   public final NumberSetting blurTintAlpha=add(new NumberSetting("Прозрачность цвета",28,0,100,1));
   public final BooleanSetting blurMirror=add(new BooleanSetting("Отзеркаливание",false));
   public final NumberSetting outlineWidth=add(new NumberSetting("Толщина обводки",2,1,6,.5));
   public final NumberSetting outlineGlow=add(new NumberSetting("Свечение обводки",1.2,0,3,.1));
   public final NumberSetting outlineGlowRadius=add(new NumberSetting("Радиус свечения обводки",16,4,48,1));
   public final NumberSetting outlineFill=add(new NumberSetting("Заливка модели",0,0,60,1));
   public final class TargetSelection {
       private final Map<String,BooleanSetting> values=new HashMap<>();
       TargetSelection(){for(String name:new String[]{"Игроки","Голые","Невидимые","Друзья","Себя"})values.put(name,add(new BooleanSetting("Цели: "+name,name.equals("Игроки")||name.equals("Голые"))));}
       public boolean isEnabled(String name){return values.get(name).enabled();}
   }
   private record TypeKey(Identifier texture,int flags) {}
   public Chams() {
      instance = this;
      this.themeColor.visibleWhen(() -> !this.mode.is("Блюр"));
      this.color.visibleWhen(() -> !this.mode.is("Блюр") && !this.themeColor.get());
      this.throughArmor.visibleWhen(() -> this.mode.is("Свечение"));
      this.fillOpacity.visibleWhen(() -> this.mode.is("Свечение"));
      this.glow.visibleWhen(() -> this.mode.is("Свечение"));
      this.glowRadius.visibleWhen(() -> this.mode.is("Свечение") && this.glow.get());
      this.glowStrength.visibleWhen(() -> this.mode.is("Свечение") && this.glow.get());
      this.additive.visibleWhen(() -> this.mode.is("Flat"));
      this.damageColor.visibleWhen(this.indication::enabled);
      this.blurColor.visibleWhen(() -> this.mode.is("Блюр"));
      this.blurRadius.visibleWhen(() -> this.mode.is("Блюр"));
      this.blurTintAlpha.visibleWhen(() -> this.mode.is("Блюр"));
      this.blurMirror.visibleWhen(() -> this.mode.is("Блюр"));
      this.outlineWidth.visibleWhen(() -> this.mode.is(OUTLINE));
      this.outlineGlow.visibleWhen(() -> this.mode.is(OUTLINE));
      this.outlineGlowRadius.visibleWhen(() -> this.mode.is(OUTLINE) && this.outlineGlow.floatValue() > 0.0F);
      this.outlineFill.visibleWhen(() -> this.mode.is(OUTLINE));
   }

   private boolean rainbowOutline() {
      return false;
   }

@Override
   protected void onDisable() {
      
      worldSubmission = false;
      ChamsModels.close();
   }

   public static boolean applies(AvatarRenderState state) {
      Chams self = instance;
      if (self != null && self.enabled() && (self.mode.is("Flat") || worldSubmission)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            return false;
         }

         Entity entity = mc.level.getEntity(((morfeov.coliseum.render.coliseimvisual.EntityIdAccess)state).coliseum$getEntityId());
         boolean selected=targets(self, entity);
         if(selected&&!self.mode.is("Flat"))ChamsModels.prepare();
         return selected;
      } else {
         return false;
      }
   }

   public static boolean targets(Chams self, Entity entity) {
      Minecraft mc = Minecraft.getInstance();
      if (!(entity instanceof AbstractClientPlayer player && !player.isSpectator())) {
         return false;
      } else {
         if (player == mc.player) {
            return self.targets.isEnabled("Себя") && !mc.options.getCameraType().isFirstPerson();
         }

         if (morfeov.coliseum.friend.Friends.contains(player.getGameProfile().name())) {
            return self.targets.isEnabled("Друзья");
         }

         if (player.isInvisible()) {
            return self.targets.isEnabled("Невидимые");
         }

         boolean naked = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
            && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
            && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
            && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
         return self.targets.isEnabled(naked ? "Голые" : "Игроки");
      }
   }

   public static int tint(LivingEntityRenderState state) {
      Chams self = instance;
      if (self == null) {
         return -1;
      } else if (self.mode.is(OUTLINE)) {
         // Outline masks only carry a damage marker; the real colours are applied by the composite shader.
         return self.indication.get() && state.hasRedOverlay ? 0xFFFF0000 : 0xFFFFFFFF;
      } else {
         int tint = self.indication.get() && state.hasRedOverlay
            ? self.damageColor.get()
            : (self.mode.is("Блюр") ? self.blurColor.get() : self.flatColor());
         if (self.mode.is("Свечение")) {
            int alpha = Math.round(Math.clamp(self.fillOpacity.floatValue(), 0.0F, 100.0F) * 2.55F);
            return alpha << 24 | tint & 16777215;
         } else {
            return self.mode.is("Flat") ? tint : opaque(tint);
         }
      }
   }

   public static boolean glowApplies(AvatarRenderState state) {
      Chams self = instance;
      return self != null && self.mode.is("Свечение") && applies(state) && ChamsGlowPass.ready();
   }

   public static boolean replacesModel(AvatarRenderState state) {
      return applies(state) && (!instance.mode.is("Свечение") || ChamsGlowPass.ready()) && (!instance.mode.is("Блюр") || ChamsBlurPass.ready());
   }

   public static boolean outlineApplies(AvatarRenderState state) {
      Chams self = instance;
      return self != null && self.mode.is(OUTLINE) && applies(state) && ChamsOutlinePass.ready();
   }

   public static boolean capturesArmor(AvatarRenderState state) {
      return glowApplies(state) && !instance.throughArmor.get() || outlineApplies(state);
   }

   public static boolean ignoresOcclusion(Entity entity) {
      Chams self = instance;
      return self != null && self.enabled() && self.throughWalls.get() && targets(self, entity);
   }

   public int flatColor() {
      int chosen = this.color.get();
      return this.themeColor.get() ? chosen & 0xFF000000 | morfeov.coliseum.module.impl.themes.Theme.accent() & 16777215 : chosen;
   }

   public static RenderType renderType(AvatarRenderState state) {
      Chams self = instance;
      Identifier texture = state.skin.body().texturePath();
      if (self != null && self.mode.is(OUTLINE)) {
         return RenderTypes.entityTranslucent(texture);
      } else if (self != null && self.mode.is("Свечение")) {
         return ChamsGlowPass.surfaceType(texture);
      } else if (self != null && self.mode.is("Блюр")) {
         ChamsBlurPass.mark();
         return ChamsBlurPass.maskType(texture);
      } else {
         int flags = self == null ? 0 : (self.throughWalls.get() ? 1 : 0) | (self.additive.get() ? 2 : 0);
         return type(flags, texture);
      }
   }

   public static int overlay() {
      return OverlayTexture.NO_OVERLAY;
   }

   public static void beginBlurFrame() {
      Chams self = instance;
      worldSubmission = true;
      ChamsBlurPass.beginFrame(self != null && self.enabled() && self.mode.is("Блюр"));
      ChamsGlowPass.beginFrame(self != null && self.enabled() && self.mode.is("Свечение"));
      hiddenOutline = null;
      ChamsOutlinePass.beginFrame(self != null && self.enabled() && self.mode.is(OUTLINE));
   }

   public static void endWorldSubmission() {
      worldSubmission = false;
   }

   public static void renderBlur() {
      Chams self = instance;
      if (self != null && self.enabled() && self.mode.is("Свечение")) {
         ChamsGlowPass.render(
            self.fillOpacity.floatValue() / 100.0F,
            self.glowRadius.floatValue(),
            self.glow.get() ? self.glowStrength.floatValue() : 0.0F,
            self.throughWalls.get(),
            self.throughArmor.get()
         );
      } else {
         ChamsGlowPass.cancelFrame();
      }

      if (self != null && self.enabled() && self.mode.is("Блюр")) {
         ChamsBlurPass.render(
            self.blurColor.get(),
            self.blurRadius.floatValue(),
            self.blurTintAlpha.floatValue() / 100.0F,
            self.blurMirror.get(),
            self.throughWalls.get()
         );
      } else {
         ChamsBlurPass.cancelFrame();
      }

      if (self != null && self.enabled() && self.mode.is(OUTLINE)) {
         ChamsOutlinePass.render(
            self.flatColor(),
            self.flatColor(),
            self.damageColor.get(),
            self.indication.get(),
            self.outlineWidth.floatValue(),
            0,
            0.0F,
            self.outlineGlow.floatValue(),
            self.outlineGlowRadius.floatValue(),
            self.outlineFill.floatValue() / 100.0F,
            self.throughWalls.get()
         );
      } else {
         ChamsOutlinePass.cancelFrame();
      }
   }

   public static RenderType type(int flags, Identifier texture) {
      if (pipelineFailed) {
         return RenderTypes.entityTranslucentEmissive(texture);
      }

      TypeKey key = new TypeKey(texture, flags);
      RenderType cached = TYPES.get(key);
      if (cached != null) {
         return cached;
      }

      try {
         boolean through = (flags & 1) != 0;
         boolean add = (flags & 2) != 0;
         RenderPipeline pipeline = PIPELINES[flags];
         if (pipeline == null) {
            pipeline = RenderPipeline.builder(new Snippet[]{RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
               .withLocation(Identifier.fromNamespaceAndPath("coliseum", "pipeline/chams_" + flags))
               .withVertexShader(Identifier.fromNamespaceAndPath("coliseum", "core/chams_flat"))
               .withFragmentShader(Identifier.fromNamespaceAndPath("coliseum", "core/chams_flat"))
               .withShaderDefine("PER_FACE_LIGHTING")
               .withShaderDefine("NO_OVERLAY")
               .withColorTargetState(new ColorTargetState(add ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT))
               .withCull(false)
               .withDepthStencilState(new DepthStencilState(through ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL, false))
               .build();
            RenderPipelines.register(pipeline);
            PIPELINES[flags] = pipeline;
         }

         cached = RenderType.create(
            "coliseum_chams_" + flags, RenderSetup.builder(pipeline).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()
         );
         TYPES.put(key, cached);
         return cached;
      } catch (Throwable throwable) {
         pipelineFailed = true;
         throwable.printStackTrace();
         return RenderTypes.entityTranslucentEmissive(texture);
      }
   }

   public static int opaque(int color) {
      return 0xFF000000 | color & 16777215;
   }
}
