package im.yaHustler.mixin.punchy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchy.client.state.FireStateMachine", remap = false)
public abstract class PunchyFireMixin {
    @Inject(method = "tick(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hustle263$noFireTick(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "triggerPutOut()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hustle263$noPutOut(CallbackInfo ci) {
        ci.cancel();
    }
}
