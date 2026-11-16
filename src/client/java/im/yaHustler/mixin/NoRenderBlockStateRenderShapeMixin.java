package im.yaHustler.mixin;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import im.yaHustler.module.impl.render.NoRender;


@Mixin(BlockStateBase.class)
public abstract class NoRenderBlockStateRenderShapeMixin {
   @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
   private void hustle263$hidePlants(CallbackInfoReturnable<RenderShape> cir) {
      if (NoRender.noPlants() && NoRender.isPlantState((BlockState)(Object)this)) {
         cir.setReturnValue(RenderShape.INVISIBLE);
      }
   }
}


