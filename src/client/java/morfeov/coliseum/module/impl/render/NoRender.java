package morfeov.coliseum.module.impl.render;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;









@morfeov.coliseum.module.ModuleInfo(id="no_render",name="NoRender",category=morfeov.coliseum.module.Category.RENDER,description="Скрытие визуальных эффектов")
public class NoRender extends morfeov.coliseum.module.Module {
   public static NoRender instance;
   public final morfeov.coliseum.setting.BooleanSetting hurtCam = add(new morfeov.coliseum.setting.BooleanSetting("Тряска камеры", true));
   public final morfeov.coliseum.setting.BooleanSetting fireOverlay = add(new morfeov.coliseum.setting.BooleanSetting("Огонь", true));
   public final morfeov.coliseum.setting.BooleanSetting waterOverlay = add(new morfeov.coliseum.setting.BooleanSetting("Размытие в воде", true));
   public final morfeov.coliseum.setting.BooleanSetting blockOverlay = add(new morfeov.coliseum.setting.BooleanSetting("Зрение в блоках", true));
   public final morfeov.coliseum.setting.BooleanSetting totemAnimation = add(new morfeov.coliseum.setting.BooleanSetting("Анимация тотема", true));
   public final morfeov.coliseum.setting.BooleanSetting cameraOverlays = add(new morfeov.coliseum.setting.BooleanSetting("Камераклип", false));
   private final java.util.Map<String,morfeov.coliseum.setting.BooleanSetting> hideElements = new java.util.LinkedHashMap<>();
   {
      hideElements.put("Босс-бар", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Босс-бар",false)));
      hideElements.put("Чат", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Чат",false)));
      hideElements.put("Хотбар", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Хотбар",false)));
      hideElements.put("Скорборд", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Скорборд",false)));
      hideElements.put("Иконки эффектов", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Иконки эффектов",false)));
      hideElements.put("Главная рука", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Главная рука",false)));
      hideElements.put("Вторая рука", add(new morfeov.coliseum.setting.BooleanSetting("Скрывать: Вторая рука",false)));
   }
   public final morfeov.coliseum.setting.BooleanSetting weather = add(new morfeov.coliseum.setting.BooleanSetting("Погода", false));
   public final morfeov.coliseum.setting.BooleanSetting blockParticles = add(new morfeov.coliseum.setting.BooleanSetting("Партиклы блоков", false));
   public final morfeov.coliseum.setting.BooleanSetting explosionParticles = add(new morfeov.coliseum.setting.BooleanSetting("Дым взрыва", false));
   public final morfeov.coliseum.setting.BooleanSetting allParticles = add(new morfeov.coliseum.setting.BooleanSetting("Все партиклы", false));
   public final morfeov.coliseum.setting.BooleanSetting lava = add(new morfeov.coliseum.setting.BooleanSetting("Лава", false));
   public final morfeov.coliseum.setting.BooleanSetting hitParticles = add(new morfeov.coliseum.setting.BooleanSetting("Партиклы ударов", false));
   public final morfeov.coliseum.setting.BooleanSetting badEffects = add(new morfeov.coliseum.setting.BooleanSetting("Плохие эффекты", false));
   public final morfeov.coliseum.setting.BooleanSetting plants = add(new morfeov.coliseum.setting.BooleanSetting("Растения", false));
   public final morfeov.coliseum.setting.BooleanSetting skyBackground = add(new morfeov.coliseum.setting.BooleanSetting("Фон неба", false));
   public final morfeov.coliseum.setting.BooleanSetting fog = add(new morfeov.coliseum.setting.BooleanSetting("Туман", false));
   public final morfeov.coliseum.setting.BooleanSetting darkness = add(new morfeov.coliseum.setting.BooleanSetting("Темнота", false));
   public final morfeov.coliseum.setting.BooleanSetting nausea = add(new morfeov.coliseum.setting.BooleanSetting("Тошнота", false));
   public final morfeov.coliseum.setting.BooleanSetting witherHearts = add(new morfeov.coliseum.setting.BooleanSetting("Сердца иссушения", false));
   public boolean plantsReloadState;

   public NoRender() {
      instance = this;
   }

   @Override public void tick(net.minecraft.client.Minecraft client) {
      boolean hide=noPlants();
      if(hide!=plantsReloadState){plantsReloadState=hide;reloadWorldRenderer();}
   }
   @Override
   public void onEnable() {
      
      this.plantsReloadState = noPlants();
      if (this.plantsReloadState) {
         reloadWorldRenderer();
      }
   }

   @Override
   public void onDisable() {
      
      boolean reload = this.plantsReloadState;
      this.plantsReloadState = false;
      
      if (reload) {
         reloadWorldRenderer();
      }
   }

   public static boolean on(morfeov.coliseum.setting.BooleanSetting setting) {
      return instance != null && instance.enabled() && setting.enabled();
   }

   public static boolean noHurtCam() {
      return instance != null && on(instance.hurtCam);
   }

   public static boolean noFireOverlay() {
      return instance != null && on(instance.fireOverlay);
   }

   public static boolean noWaterOverlay() {
      return instance != null && on(instance.waterOverlay);
   }

   public static boolean noBlockOverlay() {
      return instance != null && on(instance.blockOverlay);
   }

   public static boolean noTotemAnimation() {
      return instance != null && on(instance.totemAnimation);
   }

   public static boolean noCameraOverlays() {
      return instance != null && on(instance.cameraOverlays);
   }

   public static boolean hidden(String element) {
      return instance != null && instance.enabled() && instance.hideElements.containsKey(element) && instance.hideElements.get(element).enabled();
   }

   public static boolean noBossBar() {
      return hidden("Босс-бар");
   }

   public static boolean noChat() {
      return hidden("Чат");
   }

   public static boolean noHotbar() {
      return hidden("Хотбар");
   }

   public static boolean noEffectIcons() {
      return hidden("Иконки эффектов");
   }

   public static boolean noMainHand() {
      return hidden("Главная рука");
   }

   public static boolean noOffHand() {
      return hidden("Вторая рука");
   }

   public static boolean noHands() {
      return noMainHand() && noOffHand();
   }

   public static boolean noHand(InteractionHand hand) {
      return hand == InteractionHand.MAIN_HAND ? noMainHand() : hand == InteractionHand.OFF_HAND && noOffHand();
   }

   public static boolean noWeather() {
      return instance != null && on(instance.weather);
   }

   public static boolean noBlockParticles() {
      return instance != null && on(instance.blockParticles);
   }

   public static boolean noParticles() {
      return instance != null && on(instance.allParticles);
   }

   public static boolean hideParticle(ParticleOptions options) {
      return options != null && noHitParticles() && (options.getType() == ParticleTypes.CRIT || options.getType() == ParticleTypes.ENCHANTED_HIT || options.getType() == ParticleTypes.DAMAGE_INDICATOR || options.getType() == ParticleTypes.SWEEP_ATTACK) || noParticles()
         ? true
         : instance != null
            && on(instance.explosionParticles)
            && options != null
            && (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER);
   }

   public static boolean noLava() {
      return instance != null && on(instance.lava);
   }

   public static boolean noHitParticles() {
      return instance != null && on(instance.hitParticles);
   }

   public static boolean noBadEffects() {
      return instance != null && on(instance.badEffects);
   }

   public static boolean noPlants() {
      return instance != null && on(instance.plants);
   }

   public static boolean noSkyBackground() {
      return instance != null && on(instance.skyBackground);
   }

   public static boolean noFog() {
      return instance != null && on(instance.fog);
   }

   public static boolean noDarkness() {
      return instance != null && (on(instance.darkness) || noBadEffects());
   }

   public static boolean noNausea() {
      return instance != null && (on(instance.nausea) || noBadEffects());
   }

   public static boolean noScoreboard() {
      return hidden("Скорборд");
   }

   public static boolean noWitherHearts() {
      return instance != null && (on(instance.witherHearts) || noBadEffects());
   }

   public static boolean isPlantState(BlockState state) {
      return state == null
         ? false
         : state.getBlock() instanceof VegetationBlock
            || state.getBlock() instanceof BushBlock
            || state.getBlock() instanceof GrowingPlantBodyBlock
            || state.getBlock() instanceof CropBlock
            || state.getBlock() instanceof SaplingBlock
            || state.getBlock() instanceof MushroomBlock
            || state.getBlock() instanceof SweetBerryBushBlock
            || state.getBlock() instanceof TallGrassBlock
            || state.getBlock() instanceof DoublePlantBlock
            || state.getBlock() instanceof FlowerBlock
            || state.getBlock() instanceof FlowerPotBlock
            || state.is(BlockTags.FLOWERS)
            || state.getBlock() instanceof SaplingBlock;
   }

   public static void reloadWorldRenderer() {
      if (net.minecraft.client.Minecraft.getInstance().level != null && net.minecraft.client.Minecraft.getInstance().levelRenderer != null) {
         net.minecraft.client.Minecraft.getInstance().levelExtractor.allChanged();
      }
   }
}


