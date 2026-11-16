package morfeov.coliseum.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Ambience;
import morfeov.coliseum.render.coliseimvisual.AmbienceClock;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class AmbienceClockMixin implements AmbienceClock {
    @Unique private boolean coliseum$overworld;
    public void coliseum$markOverworldClock() { coliseum$overworld = true; }
    @ModifyReturnValue(method="totalTicks", at=@At("RETURN"))
    private long coliseum$visualTime(long original) {
        var weather = Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Weather.class);
        if (coliseum$overworld && weather != null && weather.active()) return weather.timeOfDay();
        Ambience module = Coliseum.MODULES.get(Ambience.class);
        return coliseum$overworld && module != null ? module.compute107(original) : original;
    }
}
