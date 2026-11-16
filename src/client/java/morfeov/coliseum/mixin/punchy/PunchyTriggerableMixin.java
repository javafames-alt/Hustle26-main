package morfeov.coliseum.mixin.punchy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchy.client.state.TriggerableActionTracker", remap = false)
public abstract class PunchyTriggerableMixin {
    @Inject(method = "record(Lnet/minecraft/world/InteractionHand;I)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void coliseum$blockTrigger(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "record(Lnet/minecraft/world/InteractionHand;ILjava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void coliseum$blockTriggerNamed(CallbackInfo ci) {
        ci.cancel();
    }
}
