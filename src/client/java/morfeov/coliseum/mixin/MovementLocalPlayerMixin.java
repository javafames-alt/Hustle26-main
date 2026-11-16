package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.movement.NoPush;
import morfeov.coliseum.module.impl.player.ElytraHelper;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MovementLocalPlayerMixin {
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void coliseum$rwSprintBlock(CallbackInfoReturnable<Boolean> cir) {
        if (morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.isBlocking()) cir.setReturnValue(false);
    }
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void coliseum$blockPush(double x, double z, CallbackInfo ci) {
        if (NoPush.blocksSuffocationPush()) {
            ci.cancel();
        }
    }

    @Inject(method = "isAutoJumpEnabled", at = @At("HEAD"), cancellable = true)
    private void coliseum$disableAutoJump(CallbackInfoReturnable<Boolean> cir) {
        ElytraHelper helper = Coliseum.MODULES.get(ElytraHelper.class);
        if (helper != null && helper.isAutoTakeoffActive()) {
            cir.setReturnValue(false);
        }
    }
}
