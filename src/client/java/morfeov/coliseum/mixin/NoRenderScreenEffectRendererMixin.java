package morfeov.coliseum.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import morfeov.coliseum.module.impl.render.NoRender;


@Mixin(ScreenEffectRenderer.class)
public class NoRenderScreenEffectRendererMixin {
   @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
   private static void coliseum$noFireOverlay(CallbackInfo ci) {
      if (NoRender.noFireOverlay()) {
         ci.cancel();
      }
   }

   @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
   private static void coliseum$noWaterOverlay(CallbackInfo ci) {
      if (NoRender.noWaterOverlay()) {
         ci.cancel();
      }
   }

   @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
   private static void coliseum$noBlockOverlay(CallbackInfo ci) {
      if (NoRender.noBlockOverlay()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
   private void coliseum$noTotemAnimation(CallbackInfo ci) {
      if (NoRender.noTotemAnimation()) {
         ci.cancel();
      }
   }
}

