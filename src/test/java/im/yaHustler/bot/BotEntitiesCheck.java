package im.yaHustler.bot;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import im.yaHustler.bot.world.BotEntities;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

final class BotEntitiesCheck {
    static void run() {
        var first = new BotEntities();
        var other = new BotEntities();
        var uuid = UUID.randomUUID();
        first.handle(new ClientboundAddEntityPacket(4, uuid, -2, 64, 3, 10, 20, EntityTypes.PLAYER, 0, Vec3.ZERO, 30));
        assertEquals(uuid, first.snapshot(4).uuid());
        assertEquals(new Vec3(-2, 64, 3), first.snapshot(4).movement().position());
        assertNull(other.snapshot(4));
        var encoder = new VecDeltaCodec();
        encoder.setBase(new Vec3(-2, 64, 3));
        var target = new Vec3(-1.5, 64.25, 2.5);
        first.move(4, new ClientboundMoveEntityPacket.Pos(4, encoder.tryEncode(target), true));
        assertEquals(target, first.snapshot(4).movement().position());
        assertTrue(first.snapshot(4).onGround());
        assertEquals(20, first.snapshot(4).movement().yRot(), 2);
        first.move(4, new ClientboundMoveEntityPacket.Rot(4, (byte) 64, (byte) 0, false));
        assertEquals(target, first.snapshot(4).movement().position());
        assertEquals(90, first.snapshot(4).movement().yRot());
        first.handle(new ClientboundEntityPositionSyncPacket(4, PositionPath.of(new Vec3(100, 70, -20)), 45, 10, true));
        encoder.setBase(new Vec3(100, 70, -20));
        target = new Vec3(101, 71, -21);
        first.move(4, new ClientboundMoveEntityPacket.Pos(4, encoder.tryEncode(target), true));
        assertEquals(target, first.snapshot(4).movement().position());
        first.handle(new ClientboundTeleportEntityPacket(4, new PositionMoveRotation(new Vec3(1, 2, 3), Vec3.ZERO, 0, 0), Set.of(Relative.X, Relative.Y, Relative.Z), false));
        assertEquals(new Vec3(102, 73, -18), first.snapshot(4).movement().position());
        first.handle(new ClientboundSetEntityMotionPacket(4, new Vec3(0, 0.5, 0)));
        assertEquals(new Vec3(0, 0.5, 0), first.snapshot(4).movement().deltaMovement());
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        first.handle(new ClientboundSetEquipmentPacket(4, List.of(Pair.of(EquipmentSlot.MAINHAND, sword))));
        sword.setCount(0);
        first.snapshot(4).equipment().get(EquipmentSlot.MAINHAND).setCount(0);
        assertEquals(1, first.snapshot(4).equipment().get(EquipmentSlot.MAINHAND).getCount());
        var stack = new ItemStack(Items.APPLE, 3);
        first.handle(new ClientboundSetEntityDataPacket(4, List.of(new SynchedEntityData.DataValue<>(8, EntityDataSerializers.ITEM_STACK, stack))));
        stack.setCount(0);
        ((ItemStack) first.snapshot(4).metadata().getFirst().value()).setCount(0);
        assertEquals(3, ((ItemStack) first.snapshot(4).metadata().getFirst().value()).getCount());
        first.handle(new ClientboundSetEntityMotionPacket(99, Vec3.ZERO));
        assertEquals(1, first.size());
        first.handle(new ClientboundRemoveEntitiesPacket(4));
        first.move(4, new ClientboundMoveEntityPacket.Rot(4, (byte) 0, (byte) 0, false));
        assertTrue(first.snapshots().isEmpty());
        System.out.println("Bot entity spawning, relative movement, teleport codec reset, metadata and equipment isolation checks passed.");
    }
}
