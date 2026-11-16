package morfeov.coliseum.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import morfeov.coliseum.module.impl.render.NoRender;


@Mixin(LightmapRenderStateExtractor.class)
public abstract class NoRenderLightmapRenderStateExtractorMixin {
   @Inject(method = "calculateDarknessScale", at = @At("HEAD"), cancellable = true)
   private void coliseum$noDarkness(LivingEntity entity, float factor, float partialTicks, CallbackInfoReturnable<Float> cir) {
      if (NoRender.noDarkness()) {
         cir.setReturnValue(0.0F);
      }
   }
}

