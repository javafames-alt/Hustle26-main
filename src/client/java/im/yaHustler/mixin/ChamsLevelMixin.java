package im.yaHustler.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class ChamsLevelMixin {
    @Inject(method="submitEntities",at=@At("HEAD")) private void hustle263$beginChams(CallbackInfo ci){im.yaHustler.render.chams.ChamsModels.begin();}
    @Inject(method="submitEntities",at=@At("RETURN")) private void hustle263$endChams(CallbackInfo ci){im.yaHustler.module.impl.render.Chams.endWorldSubmission();}
}
