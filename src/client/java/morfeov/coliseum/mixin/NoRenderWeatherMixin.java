package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.renderer.WeatherEffectRenderer.class) public abstract class NoRenderWeatherMixin {
@Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true) private void coliseum$noWeather(CallbackInfo ci){if(NoRender.noWeather())ci.cancel();}
}
