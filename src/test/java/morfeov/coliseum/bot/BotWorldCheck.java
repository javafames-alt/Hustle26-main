package morfeov.coliseum.bot;

import morfeov.coliseum.bot.world.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static org.junit.jupiter.api.Assertions.*;

final class BotWorldCheck {
    static void run() {
        var first = new BotWorld(-64, 384);
        var second = new BotWorld(-64, 384);
        var position = new BlockPos(-1, -63, -17);
        assertFalse(first.setBlock(position, Blocks.STONE.defaultBlockState()));
        var sections = new BlockState[24][];
        first.replaceChunk(-1, -2, sections);
        second.replaceChunk(-1, -2, sections);
        assertTrue(first.setBlock(position, Blocks.STONE.defaultBlockState()));
        assertEquals(Blocks.STONE.defaultBlockState(), first.getBlockState(position));
        assertTrue(second.getBlockState(position).isAir());
        assertTrue(first.getBlockState(position.above()).isAir());
        assertFalse(first.setBlock(new BlockPos(-1, -65, -17), Blocks.STONE.defaultBlockState()));
        assertFalse(first.setBlock(new BlockPos(-1, 320, -17), Blocks.STONE.defaultBlockState()));
        var snapshot = first.snapshot(-1, -2);
        snapshot[0][1 << 8 | 15 << 4 | 15] = Blocks.DIRT.defaultBlockState();
        assertEquals(Blocks.STONE.defaultBlockState(), first.getBlockState(position));
        long revision = first.revision();
        first.unload(-1, -2);
        assertEquals(0, first.chunkCount());
        assertTrue(first.revision() > revision);
        assertTrue(first.getBlockState(position).isAir());
        assertThrows(IllegalArgumentException.class, () -> new BotWorld(-63, 384));
        assertThrows(IllegalArgumentException.class, () -> first.replaceChunk(0, 0, new BlockState[1][]));
        System.out.println("Bot world negative coordinates, height bounds, snapshots and world isolation checks passed.");
    }
}
