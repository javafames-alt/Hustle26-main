package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class) public abstract class NoRenderRainMixin {
@Inject(method="tickWeatherEffects",at=@At("HEAD"),cancellable=true) private void coliseum$noWeather(CallbackInfo ci){if(NoRender.noWeather())ci.cancel();}
}
