package morfeov.coliseum.mixin;

import morfeov.coliseum.rotation.Look;
import morfeov.coliseum.module.impl.combat.aura.rotation.neuro.MotionTrace;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onMove", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/MouseHandler;accumulatedDX:D", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void coliseum$recordMouse(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        MotionTrace.capture(dx, dy);
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void coliseum$freeLook(LocalPlayer player, double yaw, double pitch) {
        if (Look.active()) Look.onMouse(yaw, pitch);
        else player.turn(yaw, pitch);
    }
}
