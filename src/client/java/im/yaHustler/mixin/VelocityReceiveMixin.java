package im.yaHustler.mixin;

import im.yaHustler.module.impl.movement.Velocity;
import im.yaHustler.port.CombatHooks;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class VelocityReceiveMixin {
    @Inject(method="genericsFtw",at=@At("HEAD"),cancellable=true)
    private static void hustle263$delayIncoming(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if (Velocity.receive(packet,listener) || CombatHooks.incoming(packet)) ci.cancel();
    }
}
