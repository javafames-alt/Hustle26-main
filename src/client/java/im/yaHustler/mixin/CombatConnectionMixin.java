package im.yaHustler.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class CombatConnectionMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"),cancellable=true)
    private void hustle263$combatPackets(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci) {
        if(im.yaHustler.port.CombatHooks.outgoing((Connection)(Object)this,packet)) ci.cancel();
    }
}
