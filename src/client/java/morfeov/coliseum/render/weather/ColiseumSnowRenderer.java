package morfeov.coliseum.render.weather;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import morfeov.coliseum.render.weather.ColiseumSnowPipeline;

/** Terrain snow cover with tapered edges at drops in the terrain. */
public final class ColiseumSnowRenderer implements AutoCloseable {
    private static final int CHUNK_RADIUS = 3;
    private static final double EDGE_WIDTH = .16;
    private final ColiseumSnowPipeline pipeline = new ColiseumSnowPipeline();
    private final List<Surface> surfaces = new ArrayList<>();
    private final Map<Long, Double> elevations = new HashMap<>();
    private final Map<Long, ChunkSurface> chunks = new HashMap<>();
    private ByteBuffer vertices;
    private ClientLevel lastWorld;
    private long nextScan;
    private int centerX, centerZ, count;
    private final WeatherFrustum visible = new WeatherFrustum();
    private record Surface(int x, double y, int z, double h00, double h10, double h11, double h01, int taperedCorners) {}
    private record ChunkSurface(LevelChunk chunk, List<Surface> surfaces) {}

    public boolean covers(ClientLevel world, BlockPos pos) {
        if (world != lastWorld) return false;
        Double height = elevations.get(key(pos.getX(), pos.getZ()));
        return height != null && Math.abs(height - pos.getY() - .5) <= .51;
    }

    public void render(Camera camera, Matrix4f view, Matrix4f projection, float depth, float sheen, long time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 origin = camera.position();
        int x = (int)Math.floor(origin.x) >> 4, z = (int)Math.floor(origin.z) >> 4;
        long now = System.currentTimeMillis();
        if (lastWorld != mc.level || now >= nextScan || x != centerX || z != centerZ) {
            if (lastWorld != mc.level || now >= nextScan) {
                chunks.clear();
                nextScan = now + 3500;
            }
            scan(mc.level, x, z);
            lastWorld = mc.level;
        }
        if (vertices == null) vertices = MemoryUtil.memAlloc(49 * 256 * 30 * 16);
        vertices.clear();
        count = 0;
        visible.update(projection, view);
        for (Surface surface : surfaces) {
            if (Math.abs(surface.y - origin.y) > 48) continue;
            float sx = (float)(surface.x - origin.x), sy = (float)(surface.y + .006 - origin.y), sz = (float)(surface.z - origin.z);
            float maxHeight = (float)(Math.max(Math.max(surface.h00, surface.h10), Math.max(surface.h11, surface.h01)) * depth);
            if (!visible.box(sx - .01f, sy - .01f, sz - .01f, sx + 1.01f, sy + maxHeight + .01f, sz + 1.01f)) continue;
            appendSurface(surface, origin, depth);
        }
        vertices.flip();
        double angle = (Math.floorMod(time, 24000L) - 6000.0) / 24000.0 * Math.PI * 2;
        Vec3 sun = new Vec3(-Math.sin(angle), Math.cos(angle), .15).normalize();
        float daylight = (float)Math.max(.03, Math.min(1, sun.y * 1.3 + .25));
        pipeline.draw(mc.gameRenderer.mainRenderTarget(), view, projection, origin, sun, daylight, sheen, vertices, count);
    }

    private void appendSurface(Surface surface, Vec3 origin, float depth) {
        double x = surface.x, z = surface.z;
        double y = surface.y + .006;
        double y00 = y + surface.h00 * depth;
        double y10 = y + surface.h10 * depth;
        double y11 = y + surface.h11 * depth;
        double y01 = y + surface.h01 * depth;
        int corners = surface.taperedCorners;
        if (corners == 0) {
            quad(origin, x, y00, z, x, y01, z + 1, x + 1, y11, z + 1, x + 1, y10, z);
            return;
        }

        double left = x + EDGE_WIDTH, right = x + 1 - EDGE_WIDTH;
        double near = z + EDGE_WIDTH, far = z + 1 - EDGE_WIDTH;
        double edge00 = (corners & 1) == 0 ? y00 : y;
        double edge10 = (corners & 2) == 0 ? y10 : y;
        double edge11 = (corners & 4) == 0 ? y11 : y;
        double edge01 = (corners & 8) == 0 ? y01 : y;
        quad(origin, left, y00, near, left, y01, far, right, y11, far, right, y10, near);
        quad(origin, x, edge00, z, left, y00, near, right, y10, near, x + 1, edge10, z);
        quad(origin, x, edge01, z + 1, x + 1, edge11, z + 1, right, y11, far, left, y01, far);
        quad(origin, x, edge00, z, x, edge01, z + 1, left, y01, far, left, y00, near);
        quad(origin, right, y10, near, right, y11, far, x + 1, edge11, z + 1, x + 1, edge10, z);
    }

    private void scan(ClientLevel world, int x, int z) {
        surfaces.clear();
        elevations.clear();
        centerX = x; centerZ = z;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        chunks.keySet().removeIf(id -> Math.abs((int)(id >> 32) - x) > CHUNK_RADIUS
                || Math.abs((int)(long)id - z) > CHUNK_RADIUS);
        for (int chunkX = x - CHUNK_RADIUS; chunkX <= x + CHUNK_RADIUS; chunkX++)
            for (int chunkZ = z - CHUNK_RADIUS; chunkZ <= z + CHUNK_RADIUS; chunkZ++) {
            long chunkKey = key(chunkX, chunkZ);
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            LevelChunk chunk = world.getChunk(chunkX, chunkZ);
            ChunkSurface snapshot = chunks.get(chunkKey);
            if (snapshot == null || snapshot.chunk != chunk) {
                snapshot = new ChunkSurface(chunk, scanChunk(world, chunk, chunkX, chunkZ, pos));
                chunks.put(chunkKey, snapshot);
            }
            surfaces.addAll(snapshot.surfaces);
            for (Surface surface : snapshot.surfaces) elevations.put(key(surface.x, surface.z), surface.y);
        }
        // Sample all four columns at a corner so adjacent surfaces agree on its height.
        for (int i = 0; i < surfaces.size(); i++) {
            Surface s = surfaces.get(i);
            int corners = (taperAt(s.x, s.z, s.y) ? 1 : 0)
                    | (taperAt(s.x + 1, s.z, s.y) ? 2 : 0)
                    | (taperAt(s.x + 1, s.z + 1, s.y) ? 4 : 0)
                    | (taperAt(s.x, s.z + 1, s.y) ? 8 : 0);
            surfaces.set(i, new Surface(s.x, s.y, s.z, s.h00, s.h10, s.h11, s.h01, corners));
        }
    }

    private List<Surface> scanChunk(ClientLevel world, LevelChunk chunk, int chunkX, int chunkZ, BlockPos.MutableBlockPos pos) {
        List<Surface> result = new ArrayList<>(256);
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int bx = (chunkX << 4) + dx, bz = (chunkZ << 4) + dz;
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15) + 1;
            pos.set(bx, top - 1, bz);
            // A single snow layer has no collision and can sit above the heightmap.
            if (chunk.getBlockState(pos.above()).is(Blocks.SNOW)) pos.move(0, 1, 0);
            var state = chunk.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) continue;
            // Snow's collision shape is one layer shorter than its visible shape.
            // Starting there embeds the surface in vanilla snow and causes z-fighting.
            var shape = state.is(Blocks.SNOW) ? state.getShape(world, pos) : state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;
            // Cover only a full block footprint; thin fences and plants must stay visible.
            var box = shape.bounds();
            if (box.minX > .001 || box.minZ > .001 || box.maxX < .999 || box.maxZ < .999) continue;
            double y = pos.getY() + box.maxY;
            int sx = pos.getX(), sz = pos.getZ();
            result.add(new Surface(sx, y, sz, height(sx, sz), height(sx + 1, sz),
                    height(sx + 1, sz + 1), height(sx, sz + 1), 0));
        }
        return result;
    }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    private boolean taperAt(int x, int z, double y) {
        return below(x - 1, z - 1, y) || below(x, z - 1, y) || below(x - 1, z, y) || below(x, z, y);
    }

    private boolean below(int x, int z, double y) {
        return elevations.getOrDefault(key(x, z), Double.NEGATIVE_INFINITY) < y - .001;
    }

    // Zenith's wind-oriented coarse/fine dune blend, in readable coordinates.
    private static double height(int x, int z) {
        double along = x * .9439 + z * .3304, across = -x * .3304 + z * .9439;
        double coarse = noise(along * .24, across * .58) * .68
                + noise(along * .24 * 3.1 + 11.3, across * .58 * 2.4 + 4.1) * .32;
        double fine = noise(along * .9 + 31.1, across * 2.2 + 7.7);
        return .167 + .147 * coarse + .054 * fine;
    }
    private static double noise(double x, double z) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        return lerp(lerp(hash(ix, iz), hash(ix + 1, iz), fx), lerp(hash(ix, iz + 1), hash(ix + 1, iz + 1), fx), fz);
    }
    private static double hash(int x, int z) { long n = x * 374761393L + z * 668265263L; n = (n ^ (n >> 13)) * 1274126177L; return ((n ^ (n >> 16)) & 0xffffff) / (double)0xffffff; }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private void quad(Vec3 origin, double x0, double y0, double z0, double x1, double y1, double z1,
                      double x2, double y2, double z2, double x3, double y3, double z3) {
        vertex(origin,x0,y0,z0); vertex(origin,x1,y1,z1); vertex(origin,x2,y2,z2);
        vertex(origin,x0,y0,z0); vertex(origin,x2,y2,z2); vertex(origin,x3,y3,z3);
    }
    private void vertex(Vec3 o, double x, double y, double z) {
        vertices.putFloat((float)(x-o.x)).putFloat((float)(y-o.y)).putFloat((float)(z-o.z));
        vertices.put((byte)244).put((byte)247).put((byte)255).put((byte)255);
        count++;
    }
    @Override public void close() {
        pipeline.close();
        if (vertices != null) { MemoryUtil.memFree(vertices); vertices = null; }
        surfaces.clear(); elevations.clear(); chunks.clear(); lastWorld = null; nextScan = 0;
    }
}
