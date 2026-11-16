package morfeov.coliseum.mixin;

import morfeov.coliseum.ui.ClickGuiScreen;
import morfeov.coliseum.ui.FigmaBackdropBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiBackdropMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void coliseum$backdrop(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClickGuiScreen screen = mc.gui.screen() instanceof ClickGuiScreen gui ? gui
            : mc.gui.screen() instanceof morfeov.coliseum.ui.ThemePickerScreen picker ? picker.backdropScreen()
            : mc.gui.screen() instanceof morfeov.coliseum.ui.HudEditorScreen editor ? editor.backdropScreen() : null;
        float[][] surfaces = morfeov.coliseum.hud.HudBackdrop.drain();
        var hud = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Hud.class);
        var output = mc.gameRenderer.mainRenderTarget();
        var tags = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Tags.class);
        if (tags != null) tags.renderBackdrop(mc);
        boolean hudBlur = hud != null && hud.enabled() && hud.blur.enabled() && surfaces.length > 0;
        if (hudBlur) FigmaBackdropBlur.INSTANCE.render(output, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), surfaces, 5.5f, hud.blurRadius.floatValue(), hud.themeBlur.enabled() ? .12f : 0);
        if (screen != null && screen.blurEnabled()) FigmaBackdropBlur.INSTANCE.render(output, screen);
        else if (!hudBlur) FigmaBackdropBlur.INSTANCE.close();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void coliseum$releaseBackdrop(CallbackInfo ci) {
        FigmaBackdropBlur.INSTANCE.close();
        var tags = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Tags.class);
        if (tags != null) tags.close();
    }
}
