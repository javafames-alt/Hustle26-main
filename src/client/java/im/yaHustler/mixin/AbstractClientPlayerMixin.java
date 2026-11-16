package im.yaHustler.mixin;

import im.yaHustler.skin.SkinManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void hustle263$customSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!SkinManager.isListening()) return;
        PlayerSkin skin = SkinManager.apply((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (skin != cir.getReturnValue()) cir.setReturnValue(skin);
    }
}
