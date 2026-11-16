package morfeov.coliseum.render.coliseimvisual;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;

public final class RainRoofHeightmap implements AutoCloseable {
    private static final String RAIN_ROOF = "RainRoof";
    private static final String ROOF_SAMPLER = "RoofSampler";
    private static final String AMBIENCE_RAIN_ROOF_UNIFORMS = "Ambience rain roof uniforms";
    private static final String AMBIENCE_RAIN_HEIGHTMAP = "Ambience rain heightmap";

    private final int[] cachedWorldX = new int[16384];
    private final int[] cachedWorldZ = new int[16384];
    private final MutableBlockPos samplePosition = new MutableBlockPos();
    private NativeImage heightPixels;
    private GpuTexture heightTexture;
    private GpuTextureView heightTextureView;
    private GpuBuffer roofUniforms;
    private ClientLevel level;
    private int originX;
    private int originZ;
    private int refreshBand;
    private long nextRefreshNanos;
    private boolean initialized;

    @Override
    public void close() {
        if (this.heightTextureView != null) {
            this.heightTextureView.close();
        }

        if (this.heightTexture != null) {
            this.heightTexture.close();
        }

        if (this.heightPixels != null) {
            this.heightPixels.close();
        }

        if (this.roofUniforms != null && !this.roofUniforms.isClosed()) {
            this.roofUniforms.close();
        }

        this.heightTextureView = null;
        this.heightTexture = null;
        this.heightPixels = null;
        this.roofUniforms = null;
        this.level = null;
        this.initialized = false;
        this.nextRefreshNanos = 0L;
        this.refreshBand = 0;
    }

    public void updateHeightmap(ClientLevel world, Vec3 cameraPosition, CommandEncoder encoder) {
        if (this.level != world) {
            this.close();
            this.level = world;
        }

        this.ensureHeightmapResources();
        int cameraOriginX = Mth.floor(cameraPosition.x) - 64;
        int cameraOriginZ = Mth.floor(cameraPosition.z) - 64;
        long nowNanos = System.nanoTime();
        boolean refreshDue = nowNanos >= this.nextRefreshNanos;
        if (!this.initialized || cameraOriginX != this.originX || cameraOriginZ != this.originZ || refreshDue) {
            this.originX = cameraOriginX;
            this.originZ = cameraOriginZ;

            for (int chunkZ = this.originZ >> 4; chunkZ <= this.originZ + 128 - 1 >> 4; chunkZ++) {
                int minZ = Math.max(this.originZ, chunkZ << 4);
                int maxZ = Math.min(this.originZ + 128, chunkZ + 1 << 4);

                for (int chunkX = this.originX >> 4; chunkX <= this.originX + 128 - 1 >> 4; chunkX++) {
                    int minX = Math.max(this.originX, chunkX << 4);
                    int maxX = Math.min(this.originX + 128, chunkX + 1 << 4);
                    boolean chunkLoaded = world.hasChunkAt(this.samplePosition.set(chunkX << 4, 0, chunkZ << 4));

                    for (int worldZ = minZ; worldZ < maxZ; worldZ++) {
                        int pixelZ = worldZ & 127;
                        boolean refreshRow = refreshDue && pixelZ / 32 == this.refreshBand;

                        for (int worldX = minX; worldX < maxX; worldX++) {
                            int pixelX = worldX & 127;
                            int pixelIndex = pixelZ * 128 + pixelX;
                            if (!this.initialized || refreshRow || this.cachedWorldX[pixelIndex] != worldX || this.cachedWorldZ[pixelIndex] != worldZ) {
                                int encodedHeight = chunkLoaded ? Mth.clamp(world.getHeight(Types.MOTION_BLOCKING, worldX, worldZ) + 32768, 0, 65535) : 65535;
                                this.heightPixels.setPixel(pixelX, pixelZ, -16777216 | (encodedHeight & 0xFF) << 16 | encodedHeight >> 8 << 8);
                                this.cachedWorldX[pixelIndex] = worldX;
                                this.cachedWorldZ[pixelIndex] = worldZ;
                            }
                        }
                    }
                }
            }

            encoder.writeToTexture(this.heightTexture, this.heightPixels);
            this.initialized = true;
            if (refreshDue) {
                this.refreshBand = this.refreshBand + 1 & 3;
                this.nextRefreshNanos = nowNanos + 100000000L;
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(
                this.roofUniforms.slice(),
                Std140Builder.onStack(stack, 16)
                    .putVec4((float)(cameraPosition.x - Math.floor(cameraPosition.x / 128.0) * 128.0), (float)(cameraPosition.z - Math.floor(cameraPosition.z / 128.0) * 128.0), (float)cameraPosition.y, 0.0078125F)
                    .get()
            );
        }
    }

    private void ensureHeightmapResources() {
        if (this.heightPixels == null) {
            Arrays.fill(this.cachedWorldX, Integer.MIN_VALUE);
            Arrays.fill(this.cachedWorldZ, Integer.MIN_VALUE);
            this.heightPixels = new NativeImage(Format.RGBA, 128, 128, false);
            this.heightTexture = RenderSystem.getDevice().createTexture(() -> AMBIENCE_RAIN_HEIGHTMAP, 5, GpuFormat.RGBA8_UNORM, 128, 128, 1, 1);
            this.heightTextureView = RenderSystem.getDevice().createTextureView(this.heightTexture);
            this.roofUniforms = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_RAIN_ROOF_UNIFORMS, 136, 16L);
        }
    }

    public void bindHeightmap(RenderPass pass) {
        pass.setUniform(RAIN_ROOF, this.roofUniforms);
        pass.setUniform(ROOF_SAMPLER, this.heightTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
}
