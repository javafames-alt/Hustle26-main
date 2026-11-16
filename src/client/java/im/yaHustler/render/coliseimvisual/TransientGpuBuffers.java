package im.yaHustler.render.coliseimvisual;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;

public final class TransientGpuBuffers {

    private TransientGpuBuffers() {
    }

    public static GpuBufferSlice uploadVertices(ByteBuffer data, int vertexStride) {
        GpuDevice device = RenderSystem.getDevice();
        return device.createCommandEncoder().transientMemory().uploadStaging(data, 4L, 32, data.remaining(), vertexStride);
    }

    public static GpuBufferSlice uploadUniforms(ByteBuffer data) {
        GpuDevice device = RenderSystem.getDevice();
        long alignment = device.getDeviceInfo().limits().minUniformOffsetAlignment();
        return device.createCommandEncoder().transientMemory().uploadStaging(data, alignment, 128);
    }
}
