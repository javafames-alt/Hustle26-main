package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import morfeov.coliseum.module.impl.movement.GrimGlide;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public abstract class GrimGlideTimerMixin {
    @ModifyExpressionValue(method = "advanceGameTime", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"))
    private float coliseum$grimGlideTickDuration(float millisecondsPerTick) {
        return millisecondsPerTick / Math.max(GrimGlide.MIN_TIMER_MULTIPLIER, GrimGlide.timerMultiplier() * morfeov.coliseum.port.TimerSpeed.multiplier());
    }
}
