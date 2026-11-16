package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.movement.GrimGlide;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class GrimGlidePlayerMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    private void coliseum$grimGlideTick(CallbackInfo ci) {
        GrimGlide.beforePlayerTick((LocalPlayer) (Object) this);
    }
}
