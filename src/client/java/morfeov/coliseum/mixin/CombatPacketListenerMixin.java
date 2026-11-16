package morfeov.coliseum.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CombatPacketListenerMixin {
    @Inject(method="handlePlayerCombatKill",at=@At("RETURN"))
    private void coliseum$wardenDeath(ClientboundPlayerCombatKillPacket packet,CallbackInfo ci) {morfeov.coliseum.port.CombatHooks.received(packet);}
    @Inject(method="handleEntityEvent",at=@At("RETURN"))
    private void coliseum$rwTotem(ClientboundEntityEventPacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleBossUpdate",at=@At("RETURN"))
    private void coliseum$rwBoss(ClientboundBossEventPacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleDamageEvent",at=@At("RETURN"))
    private void coliseum$spearDamage(ClientboundDamageEventPacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleSetEntityData",at=@At("RETURN"))
    private void coliseum$rwSprint(ClientboundSetEntityDataPacket packet,CallbackInfo ci) {
        morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.received(packet);
    }
    @Inject(method="handleSystemChat",at=@At("RETURN"))
    private void coliseum$keyFinderChat(ClientboundSystemChatPacket packet,CallbackInfo ci) {
        morfeov.coliseum.port.CombatHooks.received(packet);
    }
    @Inject(method="handleBlockUpdate",at=@At("RETURN"))
    private void coliseum$confirmBlock(ClientboundBlockUpdatePacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleChunkBlocksUpdate",at=@At("RETURN"))
    private void coliseum$confirmBlocks(ClientboundSectionBlocksUpdatePacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleSetHealth",at=@At("RETURN"))
    private void coliseum$confirmDamage(ClientboundSetHealthPacket packet,CallbackInfo ci) { morfeov.coliseum.port.CombatHooks.received(packet); }
    @Inject(method="handleMovePlayer",at=@At("RETURN"))
    private void coliseum$combatCorrection(ClientboundPlayerPositionPacket packet,CallbackInfo ci) { morfeov.coliseum.port.PacketBufferManager.INSTANCE.reset(); morfeov.coliseum.port.CombatHooks.received(packet); }
}
