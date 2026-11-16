package im.yaHustler.bot.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import im.yaHustler.mixin.BotEntityPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class BotEntities {
    public record Snapshot(int id, UUID uuid, EntityType<?> type, PositionMoveRotation movement,
                           float headYaw, boolean onGround, Map<EquipmentSlot, ItemStack> equipment,
                           List<SynchedEntityData.DataValue<?>> metadata) {}
    private static final class Entry {
        final int id;
        final UUID uuid;
        final EntityType<?> type;
        final VecDeltaCodec positionCodec = new VecDeltaCodec();
        final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        final Int2ObjectOpenHashMap<SynchedEntityData.DataValue<?>> metadata = new Int2ObjectOpenHashMap<>();
        PositionMoveRotation movement;
        float headYaw;
        boolean onGround;

        Entry(ClientboundAddEntityPacket packet) {
            id = packet.getId();
            uuid = packet.getUUID();
            type = packet.getType();
            movement = new PositionMoveRotation(new Vec3(packet.getX(), packet.getY(), packet.getZ()), packet.getMovement(), packet.getYRot(), packet.getXRot());
            headYaw = packet.getYHeadRot();
            positionCodec.setBase(movement.position());
        }
        Snapshot snapshot() {
            var items = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
            equipment.forEach((slot, item) -> items.put(slot, item.copy()));
            var data = new ArrayList<SynchedEntityData.DataValue<?>>(metadata.size());
            metadata.values().forEach(value -> data.add(copy(value)));
            return new Snapshot(id, uuid, type, movement, headYaw, onGround, Map.copyOf(items), List.copyOf(data));
        }
    }
    private final Int2ObjectOpenHashMap<Entry> entities = new Int2ObjectOpenHashMap<>();

    public synchronized boolean handle(Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket spawn) {
            entities.put(spawn.getId(), new Entry(spawn));
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            remove.entityIds().forEach((int id) -> entities.remove(id));
        } else if (packet instanceof ClientboundMoveEntityPacket move) {
            move(((BotEntityPacketAccessor) move).hustle263BotEntityId(), move);
        } else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
            var entry = entities.get(sync.id());
            if (entry != null) {
                entry.movement = new PositionMoveRotation(sync.position().endPosition(), entry.movement.deltaMovement(), sync.yRot(), sync.xRot());
                entry.positionCodec.setBase(entry.movement.position());
                entry.onGround = sync.onGround();
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            var entry = entities.get(teleport.id());
            if (entry != null) {
                entry.movement = PositionMoveRotation.calculateAbsolute(entry.movement, teleport.change(), teleport.relatives());
                entry.positionCodec.setBase(entry.movement.position());
                entry.onGround = teleport.onGround();
            }
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            var entry = entities.get(motion.id());
            if (entry != null) entry.movement = new PositionMoveRotation(entry.movement.position(), motion.movement(), entry.movement.yRot(), entry.movement.xRot());
        } else if (packet instanceof ClientboundRotateHeadPacket head) {
            var entry = entities.get(((BotEntityPacketAccessor) head).hustle263BotEntityId());
            if (entry != null) entry.headYaw = head.getYHeadRot();
        } else if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            var entry = entities.get(equipment.getEntity());
            if (entry != null) equipment.getSlots().forEach(pair -> entry.equipment.put(pair.getFirst(), pair.getSecond().copy()));
        } else if (packet instanceof ClientboundSetEntityDataPacket data) {
            var entry = entities.get(data.id());
            if (entry != null) data.packedItems().forEach(value -> entry.metadata.put(value.id(), copy(value)));
        } else return false;
        return true;
    }

    public synchronized void move(int id, ClientboundMoveEntityPacket packet) {
        var entry = entities.get(id);
        if (entry == null) return;
        var current = entry.movement;
        Vec3 position = packet.hasPosition() ? packet.getPositionDelta().decode(entry.positionCodec).endPosition() : current.position();
        if (packet.hasPosition()) entry.positionCodec.setBase(position);
        entry.movement = new PositionMoveRotation(position, current.deltaMovement(), packet.hasRotation() ? packet.getYRot() : current.yRot(), packet.hasRotation() ? packet.getXRot() : current.xRot());
        entry.onGround = packet.isOnGround();
    }
    public synchronized Snapshot snapshot(int id) {
        var entry = entities.get(id);
        return entry == null ? null : entry.snapshot();
    }

    public synchronized PositionMoveRotation playerMovement(UUID uuid) {
        for (Entry entry : entities.values()) {
            if (entry.type == EntityTypes.PLAYER && entry.uuid.equals(uuid)) return entry.movement;
        }
        return null;
    }
    public synchronized List<Snapshot> snapshots() {
        var result = new ArrayList<Snapshot>(entities.size());
        entities.values().forEach(entry -> result.add(entry.snapshot()));
        return List.copyOf(result);
    }
    public synchronized int size() { return entities.size(); }
    public synchronized void clear() { entities.clear(); }
    private static <T> SynchedEntityData.DataValue<T> copy(SynchedEntityData.DataValue<T> value) {
        return new SynchedEntityData.DataValue<>(value.id(), value.serializer(), value.serializer().copy(value.value()));
    }
}
