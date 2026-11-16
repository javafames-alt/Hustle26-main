package im.yaHustler.mixin;

import im.yaHustler.module.impl.movement.NoPush;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MovementPacketMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void hustle263$fishingPull(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        if (NoPush.blocksFishingPull() && packet.getEventId() == 31 && client.level != null && client.player != null
                && packet.getEntity(client.level) instanceof FishingHook hook && hook.getHookedIn() == client.player) ci.cancel();
    }
}
