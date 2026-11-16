package im.yaHustler.bot.net;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ClientInformation;

public final class BotPlayHandler extends BotCommonHandler implements ClientGamePacketListener {
    private final BotConfigHandler.GameState state;
    private final BotLoginHandler.ConfigurationFactory configuration;
    private final BotWorldPackets worldPackets;
    private final Map<Class<?>, Packet<?>> presentation = new HashMap<>();
    private final ArrayDeque<String> chat = new ArrayDeque<>();
    private final net.minecraft.network.chat.LastSeenMessagesTracker lastSeen = new net.minecraft.network.chat.LastSeenMessagesTracker(20);
    private final im.yaHustler.bot.world.BotMovement movement = new im.yaHustler.bot.world.BotMovement();
    private int interactionSequence;

    public BotPlayHandler(Connection connection, BotConfigHandler.GameState state,
                          BotLoginHandler.ConfigurationFactory configuration, Consumer<DisconnectionDetails> disconnected) {
        super(connection, state.cookies(), disconnected);
        this.state = state;
        this.configuration = configuration;
        worldPackets = new BotWorldPackets(connection, state.registries());
    }
    public BotWorldPackets worldPackets() { return worldPackets; }
    public synchronized List<String> chat() { return List.copyOf(chat); }
    public synchronized <T extends Packet<?>> T presentation(Class<T> type) { return type.cast(presentation.get(type)); }
    private void addChat(String text) {
        if (chat.size() == 100) chat.removeFirst();
        chat.addLast(text);
    }
    private synchronized void receive(Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var nested : bundle.subPackets()) nested.handle(this);
            return;
        }
        if (packet instanceof ClientboundStartConfigurationPacket) {
            var login = new BotLoginHandler.LoginState(state.login().profile(), state.login().sessionId(), Map.copyOf(cookies));
            connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, configuration.create(connection, login));
            connection.send(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
            connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
            return;
        }
        if (packet instanceof ClientboundLoginPacket) {
            connection.send(new ServerboundClientInformationPacket(ClientInformation.createDefault()));
            connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")));
        }
        if (worldPackets.handle(packet)) return;
        if (packet instanceof ClientboundSystemChatPacket message) addChat(message.content().getString());
        else if (packet instanceof ClientboundDisguisedChatPacket message) addChat(message.message().getString());
        else if (packet instanceof ClientboundPlayerChatPacket message) {
            addChat(message.unsignedContent().map(Component::getString).orElse(message.body().content()));
            message.signature().ifPresent(signature -> lastSeen.addPending(signature, true));
            if (lastSeen.offset() >= 64) connection.send(new ServerboundChatAckPacket(lastSeen.getAndClearOffset()));
        }
        else presentation.put(packet.getClass(), packet);
    }
    public synchronized void sendChat(String text) {
        if (text.isBlank() || text.length() > 256) return;
        if (text.startsWith("/")) connection.send(new ServerboundChatCommandPacket(text.substring(1)));
        else connection.send(new ServerboundChatPacket(text, java.time.Instant.now(), 0, java.util.Optional.empty(), lastSeen.generateAndApplyUpdate().update()));
    }
    public synchronized void tick(net.minecraft.world.entity.player.Input input) {
        if (connection.getPacketListener() == this && connection.isConnected()) movement.tick(worldPackets.world(), worldPackets.player(), input, connection, worldPackets.playerId());
    }
    public synchronized void selectSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        worldPackets.inventory().handle(new ClientboundSetHeldSlotPacket(slot));
        connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
    public synchronized void respawn() { connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)); }
    public synchronized void interact(boolean attack) {
        var world = worldPackets.world();
        if (world == null || !worldPackets.player().positioned()) return;
        var player = worldPackets.player().snapshot();
        var start = player.position().add(0, 1.62, 0);
        var direction = net.minecraft.world.phys.Vec3.directionFromRotation(player.xRot(), player.yRot());
        var end = start.add(direction.scale(4.5));
        var block = world.clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        double distance = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? start.distanceToSqr(block.getLocation()) : 20.25;
        int target = -1;
        for (var entity : worldPackets.entities().snapshots()) {
            var position = entity.movement().position();
            var dimensions = entity.type().getDimensions();
            var box = new net.minecraft.world.phys.AABB(position.x - dimensions.width() / 2, position.y, position.z - dimensions.width() / 2,
                position.x + dimensions.width() / 2, position.y + dimensions.height(), position.z + dimensions.width() / 2);
            var hit = box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < distance) { target = entity.id(); distance = start.distanceToSqr(hit.get()); }
        }
        if (target >= 0) {
            if (attack) connection.send(new ServerboundAttackPacket(target));
            else connection.send(new ServerboundInteractPacket(target, net.minecraft.world.InteractionHand.MAIN_HAND, null, false));
        } else if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            if (attack) connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, block.getBlockPos(), block.getDirection(), ++interactionSequence));
            else connection.send(new ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND, block, ++interactionSequence));
        } else if (!attack) connection.send(new ServerboundUseItemPacket(net.minecraft.world.InteractionHand.MAIN_HAND, ++interactionSequence, player.yRot(), player.xRot()));
    }
    @Override public void handleUpdateTags(ClientboundUpdateTagsPacket packet) { receive(packet); }
    @Override public void handlePongResponse(net.minecraft.network.protocol.ping.ClientboundPongResponsePacket packet) { receive(packet); }
    @Override public void handleAddEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) { receive(packet); }
    @Override public void handleAddObjective(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket packet) { receive(packet); }
    @Override public void handleAnimate(net.minecraft.network.protocol.game.ClientboundAnimatePacket packet) { receive(packet); }
    @Override public void handleHurtAnimation(net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket packet) { receive(packet); }
    @Override public void handleSwingAnimation(net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket packet) { receive(packet); }
    @Override public void handleAwardStats(net.minecraft.network.protocol.game.ClientboundAwardStatsPacket packet) { receive(packet); }
    @Override public void handleRecipeBookAdd(net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket packet) { receive(packet); }
    @Override public void handleRecipeBookRemove(net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket packet) { receive(packet); }
    @Override public void handleRecipeBookSettings(net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket packet) { receive(packet); }
    @Override public void handleBlockDestruction(net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket packet) { receive(packet); }
    @Override public void handleOpenSignEditor(net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket packet) { receive(packet); }
    @Override public void handleBlockEntityData(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) { receive(packet); }
    @Override public void handleBlockEvent(net.minecraft.network.protocol.game.ClientboundBlockEventPacket packet) { receive(packet); }
    @Override public void handleBlockUpdate(net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet) { receive(packet); }
    @Override public void handleSystemChat(net.minecraft.network.protocol.game.ClientboundSystemChatPacket packet) { receive(packet); }
    @Override public void handlePlayerChat(net.minecraft.network.protocol.game.ClientboundPlayerChatPacket packet) { receive(packet); }
    @Override public void handleDisguisedChat(net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket packet) { receive(packet); }
    @Override public void handleDeleteChat(net.minecraft.network.protocol.game.ClientboundDeleteChatPacket packet) { receive(packet); }
    @Override public void handleChunkBlocksUpdate(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket packet) { receive(packet); }
    @Override public void handleMapItemData(net.minecraft.network.protocol.game.ClientboundMapItemDataPacket packet) { receive(packet); }
    @Override public void handleContainerClose(net.minecraft.network.protocol.game.ClientboundContainerClosePacket packet) { receive(packet); }
    @Override public void handleContainerContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet) { receive(packet); }
    @Override public void handleMountScreenOpen(net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket packet) { receive(packet); }
    @Override public void handleContainerSetData(net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket packet) { receive(packet); }
    @Override public void handleContainerSetSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet) { receive(packet); }
    @Override public void handleEntityEvent(net.minecraft.network.protocol.game.ClientboundEntityEventPacket packet) { receive(packet); }
    @Override public void handleEntityLinkPacket(net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket packet) { receive(packet); }
    @Override public void handleSetEntityPassengersPacket(net.minecraft.network.protocol.game.ClientboundSetPassengersPacket packet) { receive(packet); }
    @Override public void handleAddTransientBlockPacket(net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket packet) { receive(packet); }
    @Override public void handleExplosion(net.minecraft.network.protocol.game.ClientboundExplodePacket packet) { receive(packet); }
    @Override public void handleGameEvent(net.minecraft.network.protocol.game.ClientboundGameEventPacket packet) { receive(packet); }
    @Override public void handleLevelChunkWithLight(net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket packet) { receive(packet); }
    @Override public void handleChunksBiomes(net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket packet) { receive(packet); }
    @Override public void handleForgetLevelChunk(net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket packet) { receive(packet); }
    @Override public void handleLevelEvent(net.minecraft.network.protocol.game.ClientboundLevelEventPacket packet) { receive(packet); }
    @Override public void handleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet) { receive(packet); }
    @Override public void handleMoveEntity(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket packet) { receive(packet); }
    @Override public void handleMinecartAlongTrack(net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket packet) { receive(packet); }
    @Override public void handleMovePlayer(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet) { receive(packet); }
    @Override public void handleRotatePlayer(net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket packet) { receive(packet); }
    @Override public void handleParticleEvent(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) { receive(packet); }
    @Override public void handlePlayerAbilities(net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket packet) { receive(packet); }
    @Override public void handleGameRuleValues(net.minecraft.network.protocol.game.ClientboundGameRuleValuesPacket packet) { receive(packet); }
    @Override public void handlePlayerInfoRemove(net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket packet) { receive(packet); }
    @Override public void handlePlayerInfoUpdate(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket packet) { receive(packet); }
    @Override public void handleRemoveEntities(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket packet) { receive(packet); }
    @Override public void handleRemoveMobEffect(net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket packet) { receive(packet); }
    @Override public void handleRespawn(net.minecraft.network.protocol.game.ClientboundRespawnPacket packet) { receive(packet); }
    @Override public void handleRotateMob(net.minecraft.network.protocol.game.ClientboundRotateHeadPacket packet) { receive(packet); }
    @Override public void handleSetHeldSlot(net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket packet) { receive(packet); }
    @Override public void handleSetDisplayObjective(net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket packet) { receive(packet); }
    @Override public void handleSetEntityData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet) { receive(packet); }
    @Override public void handleSetEntityMotion(net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket packet) { receive(packet); }
    @Override public void handleSetEquipment(net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket packet) { receive(packet); }
    @Override public void handleSetExperience(net.minecraft.network.protocol.game.ClientboundSetExperiencePacket packet) { receive(packet); }
    @Override public void handleSetHealth(net.minecraft.network.protocol.game.ClientboundSetHealthPacket packet) { receive(packet); }
    @Override public void handleSetPlayerTeamPacket(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket packet) { receive(packet); }
    @Override public void handleSetScore(net.minecraft.network.protocol.game.ClientboundSetScorePacket packet) { receive(packet); }
    @Override public void handleResetScore(net.minecraft.network.protocol.game.ClientboundResetScorePacket packet) { receive(packet); }
    @Override public void handleSetSpawn(net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket packet) { receive(packet); }
    @Override public void handleSetTime(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet) { receive(packet); }
    @Override public void handleSoundEvent(net.minecraft.network.protocol.game.ClientboundSoundPacket packet) { receive(packet); }
    @Override public void handleSoundEntityEvent(net.minecraft.network.protocol.game.ClientboundSoundEntityPacket packet) { receive(packet); }
    @Override public void handleTakeItemEntity(net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket packet) { receive(packet); }
    @Override public void handleEntityPositionSync(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket packet) { receive(packet); }
    @Override public void handleTeleportEntity(net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket packet) { receive(packet); }
    @Override public void handleTickingState(net.minecraft.network.protocol.game.ClientboundTickingStatePacket packet) { receive(packet); }
    @Override public void handleTickingStep(net.minecraft.network.protocol.game.ClientboundTickingStepPacket packet) { receive(packet); }
    @Override public void handleUpdateAttributes(net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket packet) { receive(packet); }
    @Override public void handleUpdateMobEffect(net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket packet) { receive(packet); }
    @Override public void handlePlayerCombatEnd(net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket packet) { receive(packet); }
    @Override public void handlePlayerCombatEnter(net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket packet) { receive(packet); }
    @Override public void handlePlayerCombatKill(net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket packet) { receive(packet); }
    @Override public void handleChangeDifficulty(net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket packet) { receive(packet); }
    @Override public void handleSetCamera(net.minecraft.network.protocol.game.ClientboundSetCameraPacket packet) { receive(packet); }
    @Override public void handleInitializeBorder(net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet) { receive(packet); }
    @Override public void handleSetBorderLerpSize(net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket packet) { receive(packet); }
    @Override public void handleSetBorderSize(net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket packet) { receive(packet); }
    @Override public void handleSetBorderWarningDelay(net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket packet) { receive(packet); }
    @Override public void handleSetBorderWarningDistance(net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket packet) { receive(packet); }
    @Override public void handleSetBorderCenter(net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket packet) { receive(packet); }
    @Override public void handleTabListCustomisation(net.minecraft.network.protocol.game.ClientboundTabListPacket packet) { receive(packet); }
    @Override public void handleBossUpdate(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet) { receive(packet); }
    @Override public void handleItemCooldown(net.minecraft.network.protocol.game.ClientboundCooldownPacket packet) { receive(packet); }
    @Override public void handleMoveVehicle(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket packet) { receive(packet); }
    @Override public void handleUpdateAdvancementsPacket(net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket packet) { receive(packet); }
    @Override public void handleSelectAdvancementsTab(net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket packet) { receive(packet); }
    @Override public void handlePlaceRecipe(net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket packet) { receive(packet); }
    @Override public void handleCommands(net.minecraft.network.protocol.game.ClientboundCommandsPacket packet) { receive(packet); }
    @Override public void handleStopSoundEvent(net.minecraft.network.protocol.game.ClientboundStopSoundPacket packet) { receive(packet); }
    @Override public void handleCommandSuggestions(net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket packet) { receive(packet); }
    @Override public void handleUpdateRecipes(net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet) { receive(packet); }
    @Override public void handleLookAt(net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket packet) { receive(packet); }
    @Override public void handleTagQueryPacket(net.minecraft.network.protocol.game.ClientboundTagQueryPacket packet) { receive(packet); }
    @Override public void handleLightUpdatePacket(net.minecraft.network.protocol.game.ClientboundLightUpdatePacket packet) { receive(packet); }
    @Override public void handleOpenBook(net.minecraft.network.protocol.game.ClientboundOpenBookPacket packet) { receive(packet); }
    @Override public void handleOpenScreen(net.minecraft.network.protocol.game.ClientboundOpenScreenPacket packet) { receive(packet); }
    @Override public void handleMerchantOffers(net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket packet) { receive(packet); }
    @Override public void handleSetChunkCacheRadius(net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket packet) { receive(packet); }
    @Override public void handleSetSimulationDistance(net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket packet) { receive(packet); }
    @Override public void handleSetChunkCacheCenter(net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket packet) { receive(packet); }
    @Override public void handleBlockChangedAck(net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket packet) { receive(packet); }
    @Override public void setActionBarText(net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket packet) { receive(packet); }
    @Override public void setSubtitleText(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet) { receive(packet); }
    @Override public void setTitleText(net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket packet) { receive(packet); }
    @Override public void setTitlesAnimation(net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket packet) { receive(packet); }
    @Override public void handleTitlesClear(net.minecraft.network.protocol.game.ClientboundClearTitlesPacket packet) { receive(packet); }
    @Override public void handleServerData(net.minecraft.network.protocol.game.ClientboundServerDataPacket packet) { receive(packet); }
    @Override public void handleCustomChatCompletions(net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket packet) { receive(packet); }
    @Override public void handleBundlePacket(net.minecraft.network.protocol.game.ClientboundBundlePacket packet) { receive(packet); }
    @Override public void handleDamageEvent(net.minecraft.network.protocol.game.ClientboundDamageEventPacket packet) { receive(packet); }
    @Override public void handleConfigurationStart(net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket packet) { receive(packet); }
    @Override public void handleChunkBatchStart(net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket packet) { receive(packet); }
    @Override public void handleChunkBatchFinished(net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket packet) { receive(packet); }
    @Override public void handleDebugSample(net.minecraft.network.protocol.game.ClientboundDebugSamplePacket packet) { receive(packet); }
    @Override public void handleProjectilePowerPacket(net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket packet) { receive(packet); }
    @Override public void handleSetCursorItem(net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket packet) { receive(packet); }
    @Override public void handleSetPlayerInventory(net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket packet) { receive(packet); }
    @Override public void handleTestInstanceBlockStatus(net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus packet) { receive(packet); }
    @Override public void handleWaypoint(net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket packet) { receive(packet); }
    @Override public void handleDebugChunkValue(net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket packet) { receive(packet); }
    @Override public void handleDebugBlockValue(net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket packet) { receive(packet); }
    @Override public void handleDebugEntityValue(net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket packet) { receive(packet); }
    @Override public void handleDebugEvent(net.minecraft.network.protocol.game.ClientboundDebugEventPacket packet) { receive(packet); }
    @Override public void handleGameTestHighlightPos(net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket packet) { receive(packet); }
    @Override public void handleLowDiskSpaceWarning(net.minecraft.network.protocol.game.ClientboundLowDiskSpaceWarningPacket packet) { receive(packet); }
}
