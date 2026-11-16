package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Ambience;
import morfeov.coliseum.module.impl.render.NoRender;
import morfeov.coliseum.module.impl.render.Weather;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WeatherEffectRenderer.class)
public abstract class AmbienceWeatherMixin {
    @Inject(method={"render", "renderOit"}, at=@At("HEAD"), cancellable=true)
    private void coliseum$customWeather(CallbackInfo ci) {
        var weather = Coliseum.MODULES.get(Weather.class);
        if (NoRender.noWeather() || weather != null && weather.active() && !weather.isRain()) {
            ci.cancel();
            return;
        }
        Ambience module = Coliseum.MODULES.get(Ambience.class);
        if (module != null && module.is179()) ci.cancel();
    }
}
