package im.yaHustler.mixin;

import im.yaHustler.rotation.MoveFix;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void hustle263$correctMovement(CallbackInfo ci) {
        im.yaHustler.module.impl.player.ElytraHelper.afterInput((KeyboardInput)(Object)this);
        im.yaHustler.port.CombatHooks.input((KeyboardInput)(Object)this);
        if (MoveFix.isListening()) MoveFix.apply((KeyboardInput) (Object) this);
    }
}
