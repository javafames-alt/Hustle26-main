package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Gamma;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class GammaLightmapMixin {
    @Inject(method="extract", at=@At("TAIL"))
    private void coliseum$gamma(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        Gamma module = Coliseum.MODULES.get(Gamma.class);
        if (module != null) module.apply(state);
    }
}
