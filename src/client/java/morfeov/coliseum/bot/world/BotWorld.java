package morfeov.coliseum.bot.world;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.material.FluidState;

public final class BotWorld implements BlockGetter {
    private final int minY, height;
    private final Map<Long, BlockState[][]> chunks = new HashMap<>();
    private long revision;

    public BotWorld(int minY, int height) {
        if ((minY & 15) != 0 || height <= 0 || height > 4096 || (height & 15) != 0) throw new IllegalArgumentException("Invalid bot world height");
        this.minY = minY;
        this.height = height;
    }

    public void load(ClientboundLevelChunkWithLightPacket packet, RegistryAccess registries) {
        var factory = PalettedContainerFactory.create(registries);
        var sections = new BlockState[height >> 4][];
        var data = packet.chunkData().getReadBuffer();
        try {
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                var section = new LevelChunkSection(factory);
                section.read(data);
                if (section.hasOnlyAir()) continue;
                BlockState[] states = new BlockState[4096];
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    states[y << 8 | z << 4 | x] = section.getBlockState(x, y, z);
                sections[sectionIndex] = states;
            }
            replaceChunk(packet.x(), packet.z(), sections);
        } finally { data.release(); }
    }

    public synchronized void replaceChunk(int x, int z, BlockState[][] sections) {
        if (sections.length != height >> 4) throw new IllegalArgumentException("Incorrect section count");
        var copy = new BlockState[sections.length][];
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) continue;
            if (sections[i].length != 4096) throw new IllegalArgumentException("Incorrect section size");
            copy[i] = sections[i].clone();
        }
        chunks.put(ChunkPos.pack(x, z), copy);
        revision++;
    }

    public synchronized void unload(ClientboundForgetLevelChunkPacket packet) { unload(packet.pos().x(), packet.pos().z()); }
    public synchronized void unload(int x, int z) { if (chunks.remove(ChunkPos.pack(x, z)) != null) revision++; }
    public synchronized void apply(ClientboundBlockUpdatePacket packet) { setBlock(packet.getPos(), packet.getBlockState()); }
    public synchronized void apply(ClientboundSectionBlocksUpdatePacket packet) { packet.runUpdates(this::setBlock); }

    public synchronized boolean setBlock(BlockPos position, BlockState state) {
        if (state == null || position.getY() < minY || position.getY() >= minY + height) return false;
        var sections = chunks.get(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4));
        if (sections == null) return false;
        int section = (position.getY() - minY) >> 4;
        if (sections[section] == null) {
            if (state.isAir()) return false;
            sections[section] = new BlockState[4096];
        }
        int index = (position.getY() & 15) << 8 | (position.getZ() & 15) << 4 | position.getX() & 15;
        if (sections[section][index] == state) return false;
        sections[section][index] = state;
        revision++;
        return true;
    }

    @Override public synchronized BlockState getBlockState(BlockPos position) {
        if (position.getY() < minY || position.getY() >= minY + height) return Blocks.AIR.defaultBlockState();
        var sections = chunks.get(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4));
        if (sections == null) return Blocks.AIR.defaultBlockState();
        var states = sections[(position.getY() - minY) >> 4];
        if (states == null) return Blocks.AIR.defaultBlockState();
        var state = states[(position.getY() & 15) << 8 | (position.getZ() & 15) << 4 | position.getX() & 15];
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }
    public synchronized BlockState[][] snapshot(int x, int z) {
        var sections = chunks.get(ChunkPos.pack(x, z));
        if (sections == null) return null;
        var snapshot = new BlockState[sections.length][];
        for (int i = 0; i < sections.length; i++) if (sections[i] != null) snapshot[i] = sections[i].clone();
        return snapshot;
    }
    public synchronized long revision() { return revision; }
    public synchronized int chunkCount() { return chunks.size(); }
    public synchronized boolean hasChunkAt(BlockPos position) { return chunks.containsKey(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4)); }
    @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
    @Override public int getHeight() { return height; }
    @Override public int getMinY() { return minY; }
}
