package im.yaHustler.wyvern.mixin.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Trimmed port of wyvern's LivingEntityMixin: only the swing-duration hook used by
// the ported SwingAnimation module. The original jump/elytra/sprint handlers belong
// to wyvern modules that are not part of this port.
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true)
	private void wyvern$swingDuration(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == Minecraft.getInstance().player) {
			cir.setReturnValue(im.yaHustler.wyvern.module.impl.render.SwingAnimation.duration(cir.getReturnValue()));
		}
	}
}
