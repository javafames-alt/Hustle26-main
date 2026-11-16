package im.yaHustler.wyvern.mixin.render;

import im.yaHustler.wyvern.module.impl.render.SwingAnimation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Trimmed port of wyvern's FirstPersonHandsAndItemsMixin: only the swing-animation hooks.
// The original hidden-tool and detached-camera handlers belong to wyvern modules that are
// not part of this port.
@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsAndItemsMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float wyvern$holdHeight(LocalPlayer player, float partialTicks) {
		return SwingAnimation.holdsHeight() ? 1.0F : player.getItemSwapScale(partialTicks);
	}

	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void wyvern$noUseDip(InteractionHand hand, CallbackInfo ci) {
		if (SwingAnimation.holdsHeight()) ci.cancel();
	}
}
