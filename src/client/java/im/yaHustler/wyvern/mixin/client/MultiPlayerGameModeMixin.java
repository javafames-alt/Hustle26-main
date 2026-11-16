package im.yaHustler.wyvern.mixin.client;

import im.yaHustler.wyvern.event.AttackEntityEvent;
import im.yaHustler.wyvern.event.EventBus;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Trimmed port of wyvern's MultiPlayerGameModeMixin: only the attack hooks that feed
// the ported Particles module (AttackEntityEvent). The dig/use-item handlers belong
// to wyvern combat modules that are not part of this port.
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void wyvern$attackPre(Player player, Entity entity, CallbackInfo ci) {
		EventBus.INSTANCE.post(new AttackEntityEvent.Pre(entity));
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void wyvern$attackPost(Player player, Entity entity, CallbackInfo ci) {
		EventBus.INSTANCE.post(new AttackEntityEvent.Post(entity));
	}
}
