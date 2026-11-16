package im.yaHustler.bot;

import java.util.Set;
import im.yaHustler.bot.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

final class BotMovementCheck {
    static void run() {
        var world = new BotWorld(0, 128);
        world.replaceChunk(0, 0, new BlockState[8][]);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState());
        for (int y = 64; y < 68; y++) world.setBlock(new BlockPos(2, y, 4), Blocks.STONE.defaultBlockState());
        var connection = new BotLoginCheck.TestConnection();
        var player = new BotPlayerState();
        player.teleport(new ClientboundPlayerPositionPacket(1, new PositionMoveRotation(new Vec3(2.5, 64, 2.5), Vec3.ZERO, 0, 0), Set.of()), connection);
        var movement = new BotMovement();
        for (int i = 0; i < 20; i++) movement.tick(world, player, Input.EMPTY, connection, 1);
        assertEquals(64, player.snapshot().position().y, .001);
        var forward = new Input(true, false, false, false, false, false, false);
        for (int i = 0; i < 30; i++) movement.tick(world, player, forward, connection, 1);
        assertTrue(player.snapshot().position().z <= 3.701);
        assertTrue(player.snapshot().position().z > 3);
        player.look(180, 0);
        movement.tick(world, player, new Input(false, false, false, false, true, false, false), connection, 1);
        assertTrue(player.snapshot().position().y > 64);
        for (int i = 0; i < 40; i++) movement.tick(world, player, Input.EMPTY, connection, 1);
        assertEquals(64, player.snapshot().position().y, .001);
        System.out.println("Bot movement ground support, wall collision, jumping and landing checks passed.");
    }
}
