package im.yaHustler.render.hands;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

public final class HandsFx {
    private HandsFx() {}

    public static BindGroupLayout layout(Consumer<BindGroupLayout.Builder> setup) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder();
        setup.accept(builder);
        return builder.build();
    }

    public static BindGroupLayout.Builder sampler(BindGroupLayout.Builder b, String name) {
        return b.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
    }

    public static BindGroupLayout.Builder ubo(BindGroupLayout.Builder b, String name) {
        return b.withUniform(name, UniformType.UNIFORM_BUFFER);
    }

    public static RenderPipeline pipeline(String name, String fragment, BindGroupLayout layout, ColorTargetState target) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("hustle263", "pipeline/hands/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("hustle263", "core/hands/" + fragment))
            .withBindGroupLayout(layout)
            .withColorTargetState(target)
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();
    }

    public static void pass(String label, RenderPipeline pipeline, GpuTextureView target, Consumer<RenderPass> setup) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> label, target, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            setup.accept(pass);
            pass.draw(3, 1, 0, 0);
        }
    }

    public static GpuBuffer createUniforms(String label, int size) {
        return RenderSystem.getDevice().createBuffer(() -> label,
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }

    public static void writeUniforms(GpuBuffer buffer, int size, Consumer<Std140Builder> writer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size);
            writer.accept(builder);
            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    public static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    public static float time() {
        return (System.currentTimeMillis() % 100000L) / 1000.0f;
    }
}
