package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.gui.Hud.class) public abstract class NoRenderHudMixin {
@Inject(method="extractCameraOverlays",at=@At("HEAD"),cancellable=true) private void coliseum$noCameraOverlays(CallbackInfo ci){if(NoRender.noCameraOverlays())ci.cancel();}
@Inject(method="extractBossOverlay",at=@At("HEAD"),cancellable=true) private void coliseum$noBossBar(CallbackInfo ci){if(NoRender.noBossBar())ci.cancel();}
@Inject(method="extractHotbarAndDecorations",at=@At("HEAD"),cancellable=true) private void coliseum$noHotbar(CallbackInfo ci){if(NoRender.noHotbar())ci.cancel();}
@Inject(method="extractEffects",at=@At("HEAD"),cancellable=true) private void coliseum$noEffectIcons(CallbackInfo ci){if(NoRender.noEffectIcons())ci.cancel();}
@Inject(method="extractChat",at=@At("HEAD"),cancellable=true) private void coliseum$noChat(CallbackInfo ci){if(NoRender.noChat())ci.cancel();}
@Inject(method="extractConfusionOverlay",at=@At("HEAD"),cancellable=true) private void coliseum$noNausea(CallbackInfo ci){if(NoRender.noNausea())ci.cancel();}
@Inject(method="extractScoreboardSidebar",at=@At("HEAD"),cancellable=true) private void coliseum$noScoreboard(CallbackInfo ci){if(NoRender.noScoreboard())ci.cancel();}
}
