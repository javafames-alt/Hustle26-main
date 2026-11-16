package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.render.SwingAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {
    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true)
    private void coliseum$duration(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == Minecraft.getInstance().player && SwingAnimations.modifiesSwingDuration())
            cir.setReturnValue(SwingAnimations.swingDuration(cir.getReturnValueI()));
    }
}
