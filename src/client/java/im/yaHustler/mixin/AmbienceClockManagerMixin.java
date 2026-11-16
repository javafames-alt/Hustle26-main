package im.yaHustler.mixin;
import im.yaHustler.render.coliseimvisual.AmbienceClock;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientClockManager.class)
public abstract class AmbienceClockManagerMixin {
    @Inject(method="getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;", at=@At("RETURN"))
    private void hustle263$clockIdentity(Holder<WorldClock> clock, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
        if (clock.is(WorldClocks.OVERWORLD)) ((AmbienceClock)cir.getReturnValue()).hustle263$markOverworldClock();
    }
}
