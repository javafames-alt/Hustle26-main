package im.yaHustler.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CombatPacketListenerMixin {
    @Inject(method="handlePlayerCombatKill",at=@At("RETURN"))
    private void hustle263$wardenDeath(ClientboundPlayerCombatKillPacket packet,CallbackInfo ci) {im.yaHustler.port.CombatHooks.received(packet);}
    @Inject(method="handleEntityEvent",at=@At("RETURN"))
    private void hustle263$rwTotem(ClientboundEntityEventPacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleBossUpdate",at=@At("RETURN"))
    private void hustle263$rwBoss(ClientboundBossEventPacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleDamageEvent",at=@At("RETURN"))
    private void hustle263$spearDamage(ClientboundDamageEventPacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleSetEntityData",at=@At("RETURN"))
    private void hustle263$rwSprint(ClientboundSetEntityDataPacket packet,CallbackInfo ci) {
        im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.received(packet);
    }
    @Inject(method="handleSystemChat",at=@At("RETURN"))
    private void hustle263$keyFinderChat(ClientboundSystemChatPacket packet,CallbackInfo ci) {
        im.yaHustler.port.CombatHooks.received(packet);
    }
    @Inject(method="handleBlockUpdate",at=@At("RETURN"))
    private void hustle263$confirmBlock(ClientboundBlockUpdatePacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleChunkBlocksUpdate",at=@At("RETURN"))
    private void hustle263$confirmBlocks(ClientboundSectionBlocksUpdatePacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleSetHealth",at=@At("RETURN"))
    private void hustle263$confirmDamage(ClientboundSetHealthPacket packet,CallbackInfo ci) { im.yaHustler.port.CombatHooks.received(packet); }
    @Inject(method="handleMovePlayer",at=@At("RETURN"))
    private void hustle263$combatCorrection(ClientboundPlayerPositionPacket packet,CallbackInfo ci) { im.yaHustler.port.PacketBufferManager.INSTANCE.reset(); im.yaHustler.port.CombatHooks.received(packet); }
}
