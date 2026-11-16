package im.yaHustler.mixin;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.Ambience;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CloudRenderer.class)
public abstract class AmbienceCloudMixin {
    @Inject(method={"render", "renderOit"}, at=@At("HEAD"), cancellable=true)
    private void hustle263$hideClouds(CallbackInfo ci) {
        Ambience module = Hustle26.MODULES.get(Ambience.class);
        if (module != null && module.is183()) ci.cancel();
    }
}
