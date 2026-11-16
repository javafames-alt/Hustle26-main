package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.Gamma;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class GammaLightmapMixin {
    @Inject(method="extract", at=@At("TAIL"))
    private void hustle263$gamma(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        Gamma module = Hustle26.MODULES.get(Gamma.class);
        if (module != null) module.apply(state);
    }
}
