package morfeov.coliseum.render.weather;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public final class FallingSnowRenderer implements AutoCloseable {
    private static final Identifier FLAKE = Identifier.fromNamespaceAndPath("coliseum", "textures/weather/flake.png");
    private final WeatherFlakePipeline pipeline = new WeatherFlakePipeline();
    private ByteBuffer vertices;
    private ClientLevel world;
    private long started;
    private int count;
    private final WeatherFrustum visible = new WeatherFrustum();
    private final PrecipitationPattern pattern = new PrecipitationPattern();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f right = new Vector3f(), up = new Vector3f();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public void render(Camera camera, Matrix4f view, Matrix4f projection, float amount, float wind, float size, float speed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || amount <= 0) return;
        if (world != mc.level) { world = mc.level; started = System.nanoTime(); }
        if (vertices == null) vertices = MemoryUtil.memAlloc(1024 * 1024);
        vertices.clear(); count = 0;
        Vec3 origin = camera.position();
        visible.update(projection, view);
        inverse.set(view).invert();
        inverse.transformDirection(right.set(1,0,0)).normalize();
        inverse.transformDirection(up.set(0,1,0)).normalize();
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        double driftX = seconds * wind * .35, driftZ = seconds * wind * .1225;
        int cx = (int)Math.floor((origin.x - driftX) / 2.5), cz = (int)Math.floor((origin.z - driftZ) / 2.5);
        int layers = Math.clamp(Math.round(amount * 6), 1, 6);
        pattern.update(cx,cz,layers);
        int sample = 0;
        for (int gx = cx - 6; gx <= cx + 6; gx++) for (int gz = cz - 6; gz <= cz + 6; gz++) {
            for (int layer = 0; layer < layers; layer++, sample++) {
                double seed = pattern.seed(sample);
                double phase = pattern.phase(sample);
                double sway = .22 + seed * .24;
                double x = gx * 2.5 + seed * 2.5 + driftX + Math.sin(seconds*(.4+seed*.25) + phase)*sway;
                double z = gz * 2.5 + pattern.offset(sample) + driftZ + Math.cos(seconds*(.35+seed*.2) + phase)*sway;
                double y = origin.y + wrap(seed*24 - seconds*(.65+seed*.7)*speed - origin.y + 12,24) - 12;
                float dx = (float)(x-origin.x), dy = (float)(y-origin.y), dz = (float)(z-origin.z);
                float distance = (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                float alpha = Math.max(0f,Math.min(1f,(18f-distance)/4f)) * Math.min(1f,distance/.9f);
                if (alpha <= .01f) continue;
                float halfSize = (.045f + (float)seed * .045f) * size;
                // Reject zero-alpha and off-camera flakes before querying world
                // lighting/roof visibility or building their six vertices.
                if (!visible.particle(dx, dy, dz, halfSize, 1)) continue;
                pos.set((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
                if (!world.hasChunk(pos.getX() >> 4,pos.getZ() >> 4) || !world.canSeeSky(pos)) continue;
                double angle = phase + seconds*(seed-.5)*.35;
                float c=(float)Math.cos(angle)*halfSize, s=(float)Math.sin(angle)*halfSize;
                float rx=right.x*c+up.x*s, ry=right.y*c+up.y*s, rz=right.z*c+up.z*s;
                float ux=up.x*c-right.x*s, uy=up.y*c-right.y*s, uz=up.z*c-right.z*s;
                // flake.png is a 64x64 atlas with four 32x32 sprites, not one sprite.
                int sprite = pattern.sprite(sample);
                float u0 = (sprite & 1)*.5f + .5f/64f;
                float v0 = (sprite >> 1)*.5f + .5f/64f;
                float u1 = u0 + .5f - 1f/64f, v1 = v0 + .5f - 1f/64f;
                vertex(dx-rx-ux,dy-ry-uy,dz-rz-uz,u0,v1,alpha);
                vertex(dx+rx-ux,dy+ry-uy,dz+rz-uz,u1,v1,alpha);
                vertex(dx+rx+ux,dy+ry+uy,dz+rz+uz,u1,v0,alpha);
                vertex(dx-rx-ux,dy-ry-uy,dz-rz-uz,u0,v1,alpha);
                vertex(dx+rx+ux,dy+ry+uy,dz+rz+uz,u1,v0,alpha);
                vertex(dx-rx+ux,dy-ry+uy,dz-rz+uz,u0,v0,alpha);
            }
        }
        vertices.flip();
        pipeline.draw(mc.gameRenderer.mainRenderTarget(), mc.getTextureManager().getTexture(FLAKE), visible.matrix(), vertices, count);
    }
    private void vertex(float x,float y,float z,float u,float v,float alpha) {
        vertices.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        vertices.put((byte)245).put((byte)249).put((byte)255).put((byte)Math.round(alpha*255));
        count++;
    }
    private static double wrap(double value,double period) { return value-Math.floor(value/period)*period; }
    @Override public void close() {
        pipeline.close();
        if (vertices != null) { MemoryUtil.memFree(vertices); vertices=null; }
        world=null; pattern.clear();
    }
}
