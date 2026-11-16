package morfeov.coliseum.render.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import morfeov.coliseum.render.coliseimvisual.TransientGpuBuffers;
import morfeov.coliseum.render.hands.HandsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Draws only immutable uploaded data; native staging storage lives until the frame completes. */
public final class WeatherPass implements AutoCloseable {
    private ByteBufferBuilder storage;
    private GpuBuffer matrix;
    public BufferBuilder begin(boolean textured) {
        if (storage == null) storage = new ByteBufferBuilder(262144);
        return new BufferBuilder(storage, PrimitiveTopology.QUADS,
            textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
    }
    public void draw(BufferBuilder builder, RenderPipeline pipeline, Matrix4f vp, Identifier texture) {
        try (MeshData mesh = builder.build()) {
            if (mesh == null) return;
            if (matrix == null) matrix = HandsFx.createUniforms("Weather camera", 64);
            HandsFx.writeUniforms(matrix, 64, b -> b.putMat4f(vp));
            var state = mesh.drawState();
            var vertex = TransientGpuBuffers.uploadVertices(mesh.vertexBuffer(), state.format().getVertexSize());
            var indices = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            var indexBuffer = indices.getBuffer(state.indexCount());
            try (RenderPass pass = open(pipeline)) {
                pass.setVertexBuffer(0, vertex);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setUniform("EffectMatrices", matrix);
                if (texture != null) {
                    var image = Minecraft.getInstance().getTextureManager().getTexture(texture);
                    pass.setUniform("FlakeSampler", image.getTextureView(), image.getSampler());
                }
                pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
            }
        }
    }
    public static void draw(RenderPipeline pipeline, ByteBuffer vertices, int stride, int count, Consumer<RenderPass> bindings) {
        if (count == 0) return;
        var data = TransientGpuBuffers.uploadVertices(vertices, stride);
        try (RenderPass pass = open(pipeline)) {
            pass.setVertexBuffer(0, data);
            bindings.accept(pass);
            pass.draw(count, 1, 0, 0);
        }
    }
    private static RenderPass open(RenderPipeline pipeline) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderPass pass = pipeline.getDepthStencilState() != null
            ? encoder.createRenderPass(() -> "Coliseum weather", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())
            : encoder.createRenderPass(() -> "Coliseum weather overlay", target.getColorTextureView(), Optional.empty());
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
    }
    @Override public void close() {
        if (storage != null) { storage.close(); storage = null; }
        if (matrix != null) { matrix.close(); matrix = null; }
    }
}
