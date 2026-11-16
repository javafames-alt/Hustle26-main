package morfeov.coliseum.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class ChamsLevelMixin {
    @Inject(method="submitEntities",at=@At("HEAD")) private void coliseum$beginChams(CallbackInfo ci){morfeov.coliseum.render.chams.ChamsModels.begin();}
    @Inject(method="submitEntities",at=@At("RETURN")) private void coliseum$endChams(CallbackInfo ci){morfeov.coliseum.module.impl.render.Chams.endWorldSubmission();}
}
