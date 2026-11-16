package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.Weather;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRenderingMixin {
    @ModifyExpressionValue(method="extractRenderState", at=@At(value="INVOKE", target="Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float hustle263$amount(float original) {
        Weather weather = Hustle26.MODULES.get(Weather.class);
        return weather != null && weather.active() ? (weather.isRain() ? weather.precipitationStrength() : 0) : original;
    }
    @ModifyExpressionValue(method="extractRenderState", at=@At(value="INVOKE", target="Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation hustle263$rainType(Biome.Precipitation original) {
        Weather weather = Hustle26.MODULES.get(Weather.class);
        return weather != null && weather.active() && weather.isRain() ? Biome.Precipitation.RAIN : original;
    }
    @ModifyArgs(method="prepareInstances", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private void hustle263$wind(Args args) {
        Weather weather = Hustle26.MODULES.get(Weather.class);
        if (weather == null || !weather.active() || !weather.isRain()) return;
        float shift = (float)args.get(1) * weather.windStrength() * .10f;
        args.set(0, (float)args.get(0) + shift);
        args.set(2, (float)args.get(2) + shift * .35f);
    }
}
