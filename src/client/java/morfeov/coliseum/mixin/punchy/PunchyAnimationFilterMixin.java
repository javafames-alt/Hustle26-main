package morfeov.coliseum.mixin.punchy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "punchy.client.state.AttackActionTracker", remap = false)
public abstract class PunchyAnimationFilterMixin {
    @Inject(method = "consumeCritical()Z", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void coliseum$noCritical(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "isCriticalHit(Lnet/minecraft/client/Minecraft;)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void coliseum$noCriticalHit(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
