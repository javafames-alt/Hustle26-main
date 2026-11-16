package im.yaHustler.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.Ambience;
import im.yaHustler.render.coliseimvisual.AmbienceClock;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class AmbienceClockMixin implements AmbienceClock {
    @Unique private boolean hustle263$overworld;
    public void hustle263$markOverworldClock() { hustle263$overworld = true; }
    @ModifyReturnValue(method="totalTicks", at=@At("RETURN"))
    private long hustle263$visualTime(long original) {
        var weather = Hustle26.MODULES.get(im.yaHustler.module.impl.render.Weather.class);
        if (hustle263$overworld && weather != null && weather.active()) return weather.timeOfDay();
        Ambience module = Hustle26.MODULES.get(Ambience.class);
        return hustle263$overworld && module != null ? module.compute107(original) : original;
    }
}
