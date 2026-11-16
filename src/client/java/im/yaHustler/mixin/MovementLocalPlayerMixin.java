package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.movement.NoPush;
import im.yaHustler.module.impl.player.ElytraHelper;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MovementLocalPlayerMixin {
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void hustle263$rwSprintBlock(CallbackInfoReturnable<Boolean> cir) {
        if (im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.isBlocking()) cir.setReturnValue(false);
    }
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void hustle263$blockPush(double x, double z, CallbackInfo ci) {
        if (NoPush.blocksSuffocationPush()) {
            ci.cancel();
        }
    }

    @Inject(method = "isAutoJumpEnabled", at = @At("HEAD"), cancellable = true)
    private void hustle263$disableAutoJump(CallbackInfoReturnable<Boolean> cir) {
        ElytraHelper helper = Hustle26.MODULES.get(ElytraHelper.class);
        if (helper != null && helper.isAutoTakeoffActive()) {
            cir.setReturnValue(false);
        }
    }
}
