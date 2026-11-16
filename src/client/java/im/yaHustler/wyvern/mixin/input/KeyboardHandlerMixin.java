package im.yaHustler.wyvern.mixin.input;

import im.yaHustler.wyvern.event.EventBus;
import im.yaHustler.wyvern.event.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void wyvern$onKeyPress(long handle, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) return;

		if (EventBus.INSTANCE.post(new KeyEvent(event.key(), action, event.modifiers())).isCancelled()) {
			ci.cancel();
		}
	}
}
