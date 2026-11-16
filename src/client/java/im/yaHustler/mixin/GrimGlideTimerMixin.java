package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import im.yaHustler.module.impl.movement.GrimGlide;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public abstract class GrimGlideTimerMixin {
    @ModifyExpressionValue(method = "advanceGameTime", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"))
    private float hustle263$grimGlideTickDuration(float millisecondsPerTick) {
        return millisecondsPerTick / Math.max(GrimGlide.MIN_TIMER_MULTIPLIER, GrimGlide.timerMultiplier() * im.yaHustler.port.TimerSpeed.multiplier());
    }
}
