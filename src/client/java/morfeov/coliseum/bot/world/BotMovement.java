package morfeov.coliseum.bot.world;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BotMovement {
    private boolean onGround;
    private Input previousInput = Input.EMPTY;

    public void tick(BotWorld world, BotPlayerState player, Input input, Connection connection, int playerId) {
        synchronized (player) {
            if (!player.positioned() || world == null) return;
            if (!input.equals(previousInput)) {
                connection.send(new ServerboundPlayerInputPacket(input));
                if (input.sprint() != previousInput.sprint()) {
                    var data = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    try {
                        data.writeVarInt(playerId);
                        data.writeEnum(input.sprint() ? net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_SPRINTING : net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
                        data.writeVarInt(0);
                        connection.send(net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.STREAM_CODEC.decode(data));
                    } finally { data.release(); }
                }
                previousInput = input;
            }
            var state = player.snapshot();
            var position = state.position();
            if (!world.hasChunkAt(BlockPos.containing(position))) return;
            var box = new AABB(position.x - .3, position.y, position.z - .3, position.x + .3, position.y + (input.shift() ? 1.5 : 1.8), position.z + .3);
            var support = collisionShapes(world, box.expandTowards(0, -.05, 0));
            onGround = Shapes.collide(Direction.Axis.Y, box, support, -.05) > -.05;
            double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
            double sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
            double length = Math.max(1, Math.hypot(forward, sideways));
            boolean flying = player.abilities().flying();
            boolean swimming = !world.getFluidState(BlockPos.containing(position.add(0, .4, 0))).isEmpty();
            double acceleration = flying ? player.abilities().flyingSpeed() * 2 : swimming ? .02 : onGround ? player.abilities().walkingSpeed() : .02;
            if (input.sprint() && forward > 0 && !input.shift()) acceleration *= 1.3;
            if (input.shift()) acceleration *= .3;
            forward *= acceleration / length;
            sideways *= acceleration / length;
            double yaw = Math.toRadians(state.yRot());
            Vec3 velocity = state.deltaMovement();
            double vy = flying ? ((input.jump() ? 1 : 0) - (input.shift() ? 1 : 0)) * player.abilities().flyingSpeed() * 3 : swimming && input.jump() ? velocity.y + .04 : input.jump() && onGround ? .42 : velocity.y;
            Vec3 wanted = new Vec3(velocity.x + sideways * Math.cos(yaw) - forward * Math.sin(yaw), vy,
                velocity.z + forward * Math.cos(yaw) + sideways * Math.sin(yaw));
            var obstacles = collisionShapes(world, box.expandTowards(wanted));
            double dy = Shapes.collide(Direction.Axis.Y, box, obstacles, wanted.y);
            box = box.move(0, dy, 0);
            double dx = Shapes.collide(Direction.Axis.X, box, obstacles, wanted.x);
            box = box.move(dx, 0, 0);
            double dz = Shapes.collide(Direction.Axis.Z, box, obstacles, wanted.z);
            Vec3 moved = new Vec3(dx, dy, dz);
            if (!world.hasChunkAt(BlockPos.containing(position.add(moved)))) return;
            onGround = wanted.y < 0 && dy != wanted.y || onGround && wanted.y == 0;
            double drag = swimming ? .8 : onGround ? .546 : .91;
            double gravity = flying ? 0 : swimming ? .02 : .08;
            Vec3 nextVelocity = new Vec3(dx == wanted.x ? dx * drag : 0, (dy == wanted.y ? dy - gravity : -gravity) * (swimming ? .8 : .98), dz == wanted.z ? dz * drag : 0);
            Vec3 next = position.add(moved);
            player.movement(new PositionMoveRotation(next, nextVelocity, state.yRot(), state.xRot()));
            connection.send(new ServerboundMovePlayerPacket.PosRot(next, state.yRot(), state.xRot(), onGround, dx != wanted.x || dz != wanted.z));
        }
    }

    private static ArrayList<VoxelShape> collisionShapes(BotWorld world, AABB box) {
        var shapes = new ArrayList<VoxelShape>();
        for (var position : BlockPos.betweenClosed((int) Math.floor(box.minX) - 1, (int) Math.floor(box.minY) - 1, (int) Math.floor(box.minZ) - 1,
            (int) Math.floor(box.maxX) + 1, (int) Math.floor(box.maxY) + 1, (int) Math.floor(box.maxZ) + 1)) {
            var shape = world.getBlockState(position).getCollisionShape(world, position);
            if (!shape.isEmpty()) shapes.add(shape.move(position.getX(), position.getY(), position.getZ()));
        }
        return shapes;
    }
}
