package morfeov.coliseum.bot.net;

import morfeov.coliseum.bot.world.BotPlayerState;
import morfeov.coliseum.bot.world.BotWorld;
import morfeov.coliseum.bot.world.BotInventory;
import morfeov.coliseum.bot.world.BotEntities;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

public final class BotWorldPackets {
    private final Connection connection;
    private final RegistryAccess registries;
    private final BotPlayerState player = new BotPlayerState();
    private final BotInventory inventory = new BotInventory();
    private final BotEntities entities = new BotEntities();
    private BotWorld world;
    private int playerId;

    public BotWorldPackets(Connection connection, RegistryAccess registries) {
        this.connection = connection;
        this.registries = registries;
    }
    public synchronized BotWorld world() { return world; }
    public BotPlayerState player() { return player; }
    public BotInventory inventory() { return inventory; }
    public BotEntities entities() { return entities; }
    public synchronized int playerId() { return playerId; }

    public synchronized boolean handle(Packet<?> packet) {
        if (packet instanceof ClientboundLoginPacket login) {
            playerId = login.playerId();
            inventory.reset();
            reset(login.commonPlayerSpawnInfo());
        } else if (packet instanceof ClientboundRespawnPacket respawn) {
            inventory.closeContainer();
            reset(respawn.commonPlayerSpawnInfo());
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            player.teleport(position, connection);
        } else if (packet instanceof ClientboundPlayerRotationPacket rotation) {
            synchronized (player) {
                var movement = player.snapshot();
                player.look(rotation.yRot() + (rotation.relativeY() ? movement.yRot() : 0), rotation.xRot() + (rotation.relativeX() ? movement.xRot() : 0));
            }
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == playerId) {
            synchronized (player) {
                var movement = player.snapshot();
                player.movement(new net.minecraft.world.entity.PositionMoveRotation(movement.position(), motion.movement(), movement.yRot(), movement.xRot()));
            }
        } else if (packet instanceof ClientboundSetHealthPacket health) {
            player.apply(health);
        } else if (packet instanceof ClientboundSetExperiencePacket experience) {
            player.apply(experience);
        } else if (packet instanceof ClientboundPlayerAbilitiesPacket abilities) {
            player.apply(abilities);
        } else if (inventory.handle(packet)) {
            return true;
        } else if (entities.handle(packet)) {
            return true;
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            connection.send(new ServerboundChunkBatchReceivedPacket(4));
        } else if (world == null) {
            return false;
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            world.load(chunk, registries);
        } else if (packet instanceof ClientboundForgetLevelChunkPacket chunk) {
            world.unload(chunk);
        } else if (packet instanceof ClientboundBlockUpdatePacket block) {
            world.apply(block);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket blocks) {
            world.apply(blocks);
        } else return false;
        return true;
    }

    private void reset(CommonPlayerSpawnInfo spawn) {
        var dimension = spawn.dimensionType().value();
        world = new BotWorld(dimension.minY(), dimension.height());
        player.reset();
        entities.clear();
    }
}
