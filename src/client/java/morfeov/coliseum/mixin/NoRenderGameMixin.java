package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.renderer.GameRenderer.class) public abstract class NoRenderGameMixin {
@Inject(method="bobView",at=@At("HEAD"),cancellable=true) private void coliseum$criticalBobbing(CallbackInfo ci){if(morfeov.coliseum.module.impl.combat.Criticals.suppressViewBobbing())ci.cancel();}
@Inject(method="bobHurt",at=@At("HEAD"),cancellable=true) private void coliseum$noHurtCam(CallbackInfo ci){if(NoRender.noHurtCam())ci.cancel();}
@Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true) private void coliseum$noHands(CallbackInfo ci){if(NoRender.noHands())ci.cancel();}
}
