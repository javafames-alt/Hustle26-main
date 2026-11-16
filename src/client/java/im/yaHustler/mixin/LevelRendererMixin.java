package im.yaHustler.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.TargetESP;
import im.yaHustler.module.impl.render.Music3D;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void hustle263$submitWorld(PoseStack poseStack, LevelRenderState state,
                                     SubmitNodeCollector collector, CallbackInfo ci) {
        im.yaHustler.port.CombatVisuals.submit(poseStack, collector);
        Music3D music = Hustle26.MODULES.get(Music3D.class);
        if (music != null && music.enabled()) {
            try {
                music.submit(poseStack, collector, state.cameraRenderState);
            } catch (RuntimeException error) {
                Hustle26.LOGGER.debug("Music3D render failed", error);
            }
        }
        var angel=Hustle26.MODULES.get(im.yaHustler.module.impl.render.TotemAngel.class);
        if(angel!=null && angel.enabled()) angel.submit(poseStack,collector);
        TargetESP esp = Hustle26.MODULES.get(TargetESP.class);
        if (esp == null || !esp.enabled()) return;
        try {
            esp.submit(poseStack, collector);
        } catch (Throwable t) {
            Hustle26.LOGGER.debug("TargetESP render failed", t);
        }
    }
}
