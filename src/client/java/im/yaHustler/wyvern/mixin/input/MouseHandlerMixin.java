package im.yaHustler.wyvern.mixin.input;

import im.yaHustler.wyvern.event.EventBus;
import im.yaHustler.wyvern.event.KeyEvent;
import im.yaHustler.wyvern.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Trimmed port of wyvern's MouseHandlerMixin: only the mouse-key event used for module
// keybinds. The original turnPlayer redirect belongs to the DetachedCamera feature and
// is not part of this port (the Hustle client redirects the same call already).
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void wyvern$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) return;
		if (EventBus.INSTANCE.post(new KeyEvent(Keys.mouse(info.button()), action, info.modifiers())).isCancelled()) {
			ci.cancel();
		}
	}
}
