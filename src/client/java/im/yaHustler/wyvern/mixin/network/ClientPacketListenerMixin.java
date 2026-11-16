package im.yaHustler.wyvern.mixin.network;

import im.yaHustler.wyvern.event.EntityStatusEvent;
import im.yaHustler.wyvern.event.EventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Trimmed port of wyvern's ClientPacketListenerMixin: only the entity-status hook that
// feeds the ported Particles module (totem pops). The macros/crystal/server-rotation
// handlers belong to wyvern features that are not part of this port.
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void wyvern$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity entity = packet.getEntity(mc.level);
		if (entity != null) EventBus.INSTANCE.post(new EntityStatusEvent(entity, packet.getEventId()));
	}
}
