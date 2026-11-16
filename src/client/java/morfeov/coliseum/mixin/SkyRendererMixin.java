package morfeov.coliseum.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Weather;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void coliseum$hideVanillaSky(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        Weather weather = Coliseum.MODULES.get(Weather.class);
        if (weather != null && weather.drawsSky()) {
            // Weather «Свой»: собственное шейдерное небо вместо ванильного.
            try { weather.renderSky(); } catch (RuntimeException ignored) { }
            ci.cancel();
            return;
        }
        var ambience = Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Ambience.class);
        if (ambience != null && ambience.is188()) ci.cancel();
    }
}
