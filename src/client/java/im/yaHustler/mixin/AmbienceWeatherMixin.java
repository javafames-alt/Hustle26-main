package im.yaHustler.mixin;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.Ambience;
import im.yaHustler.module.impl.render.NoRender;
import im.yaHustler.module.impl.render.Weather;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WeatherEffectRenderer.class)
public abstract class AmbienceWeatherMixin {
    @Inject(method={"render", "renderOit"}, at=@At("HEAD"), cancellable=true)
    private void hustle263$customWeather(CallbackInfo ci) {
        var weather = Hustle26.MODULES.get(Weather.class);
        if (NoRender.noWeather() || weather != null && weather.active() && !weather.isRain()) {
            ci.cancel();
            return;
        }
        Ambience module = Hustle26.MODULES.get(Ambience.class);
        if (module != null && module.is179()) ci.cancel();
    }
}
