package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import im.yaHustler.module.impl.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class NoSlowMovementMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    private void hustle263$advanceNoSlow(CallbackInfo ci) {
        NoSlow.beforePlayerTick((LocalPlayer) (Object) this);
    }

    @ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F"))
    private float hustle263$itemUseSlowdown(float multiplier) {
        return NoSlow.itemUseMultiplier((LocalPlayer) (Object) this, multiplier);
    }
}
