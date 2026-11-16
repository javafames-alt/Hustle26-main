package morfeov.coliseum.render.weather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import morfeov.coliseum.render.hands.HandsFx;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.nio.ByteBuffer;

public final class ColiseumSnowPipeline implements AutoCloseable {
    private GpuBuffer matrices, material;
    public void draw(RenderTarget target, Matrix4f view, Matrix4f projection, Vec3 camera,
                     Vec3 sun, float daylight, float sheen, ByteBuffer data, int count) {
        if (count == 0 || target.getDepthTextureView() == null) return;
        if (matrices == null) matrices = HandsFx.createUniforms("Weather snow matrices", 128);
        if (material == null) material = HandsFx.createUniforms("Weather snow material", 160);
        HandsFx.writeUniforms(matrices, 128, b -> b.putMat4f(view).putMat4f(projection));
        HandsFx.writeUniforms(material, 160, b -> b.putMat4f(new Matrix4f())
            .putVec4(wrap(camera.x), wrap(camera.y), wrap(camera.z), 0)
            .putVec4((float)sun.x, (float)sun.y, (float)sun.z, 0)
            .putVec4(daylight, daylight*.98f, daylight*.94f, 0)
            .putVec4(.16f+daylight*.36f, .18f+daylight*.38f, .22f+daylight*.40f, 0)
            .putVec4(24, 6144, 1, .6f).putVec4(.19f, .3f, sheen, 0));
        WeatherPass.draw(WeatherPipelines.SNOW, data, 16, count, p -> {
            p.setUniform("SnowMatrices", matrices); p.setUniform("ColiseumData", material);
        });
    }
    private static float wrap(double x) { return (float)(x-Math.floor(x/256)*256); }
    @Override public void close() {
        if (matrices != null) { matrices.close(); matrices = null; }
        if (material != null) { material.close(); material = null; }
    }
}
