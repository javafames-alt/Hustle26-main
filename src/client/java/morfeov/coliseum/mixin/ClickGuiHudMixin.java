package morfeov.coliseum.mixin;

import morfeov.coliseum.ui.GuiVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class ClickGuiHudMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void coliseum$hideHudBehindMenu(CallbackInfo ci) {
        if(GuiVisibility.hidesHud()) ci.cancel();
    }
}
