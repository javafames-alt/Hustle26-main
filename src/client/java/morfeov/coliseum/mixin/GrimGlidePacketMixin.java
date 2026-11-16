package morfeov.coliseum.mixin;

import io.netty.channel.ChannelFutureListener;
import morfeov.coliseum.module.impl.movement.GrimGlide;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class GrimGlidePacketMixin {
    @Unique private final ThreadLocal<Boolean> coliseum$grimGlideBypass = ThreadLocal.withInitial(() -> false);

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void coliseum$grimGlideMovement(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (!(packet instanceof ServerboundMovePlayerPacket) || coliseum$grimGlideBypass.get()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.player == null || client.player.connection.getConnection() != (Object) this
                || !GrimGlide.replaceMovementPacket(client.player)) return;
        // Bypass only our replacement. Otherwise StatusOnly re-enters this hook indefinitely.
        coliseum$grimGlideBypass.set(true);
        try {
            ((Connection) (Object) this).send(new ServerboundMovePlayerPacket.StatusOnly(true, true), listener, flush);
        } finally {
            coliseum$grimGlideBypass.remove();
        }
        ci.cancel();
    }

}
