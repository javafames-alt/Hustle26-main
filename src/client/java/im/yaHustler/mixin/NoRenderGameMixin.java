package im.yaHustler.mixin;
import im.yaHustler.module.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.renderer.GameRenderer.class) public abstract class NoRenderGameMixin {
@Inject(method="bobView",at=@At("HEAD"),cancellable=true) private void hustle263$criticalBobbing(CallbackInfo ci){if(im.yaHustler.module.impl.combat.Criticals.suppressViewBobbing())ci.cancel();}
@Inject(method="bobHurt",at=@At("HEAD"),cancellable=true) private void hustle263$noHurtCam(CallbackInfo ci){if(NoRender.noHurtCam())ci.cancel();}
@Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true) private void hustle263$noHands(CallbackInfo ci){if(NoRender.noHands())ci.cancel();}
}
