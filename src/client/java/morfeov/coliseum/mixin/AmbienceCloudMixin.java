package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Ambience;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CloudRenderer.class)
public abstract class AmbienceCloudMixin {
    @Inject(method={"render", "renderOit"}, at=@At("HEAD"), cancellable=true)
    private void coliseum$hideClouds(CallbackInfo ci) {
        Ambience module = Coliseum.MODULES.get(Ambience.class);
        if (module != null && module.is183()) ci.cancel();
    }
}
