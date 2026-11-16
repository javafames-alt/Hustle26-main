package morfeov.coliseum.render.coliseimvisual;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class AuroraRenderer implements AutoCloseable {
    private static final String COLISEUM_NIGHTIX_AURORA = "coliseum/coliseimvisual/nightix_aurora";
    private static final String NIGHTIX_AURORA = "nightix_aurora";
    private static final String SHADER_SKY_DATA = "ShaderSkyData";
    private static final String AURORA_VIEW = "AuroraView";
    private static final String AURORA_PASS_LABEL = "Nightix Aurora";
    private static final String AURORA_CAMERA = "Aurora camera";
    private static final String AURORA_SETTINGS = "Aurora settings";

    private final RenderPipeline auroraPipeline;
    private GpuBuffer skyUniforms;
    private GpuBuffer cameraUniforms;
    private final Matrix4f inverseProjection;
    private final Matrix4f inverseView;

    public AuroraRenderer() {
        this.auroraPipeline = VisualPipelineBuilder.builder()
            .withLocation(COLISEUM_NIGHTIX_AURORA)
            .withVertexShader(VisualResources.shader(NIGHTIX_AURORA))
            .withFragmentShader(VisualResources.shader(NIGHTIX_AURORA))
            .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
            .withDepthTest(CompareOp.ALWAYS_PASS)
            .withDepthWrite(false)
            .withCull(false)
            .withoutBlend()
            .withUniform(SHADER_SKY_DATA, UniformType.UNIFORM_BUFFER)
            .withUniform(AURORA_VIEW, UniformType.UNIFORM_BUFFER)
            .buildPipeline();
        this.inverseProjection = new Matrix4f();
        this.inverseView = new Matrix4f();
    }

    public void renderAurora(
        RenderTarget target, Matrix4f projection, Matrix4f view, long startMillis, float speed, float scale, float intensity, float height, int firstColor, int secondColor, boolean darkened
    ) {
        if (this.skyUniforms == null) {
            this.skyUniforms = RenderSystem.getDevice().createBuffer(() -> AURORA_SETTINGS, 136, 96L);
            this.cameraUniforms = RenderSystem.getDevice().createBuffer(() -> AURORA_CAMERA, 136, 128L);
        }

        Vector4f firstTint = VisualColors.toRgbaVector(firstColor);
        Vector4f secondTint = VisualColors.toRgbaVector(secondColor);
        float opacity = darkened ? 0.78F : 1.0F;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(
                this.skyUniforms.slice(),
                Std140Builder.onStack(stack, 96)
                    .putVec4(target.width, target.height, (float)(System.currentTimeMillis() - startMillis) / 1000.0F, opacity)
                    .putVec4(firstTint.x, firstTint.y, firstTint.z, firstTint.w * opacity)
                    .putVec4(secondTint.x, secondTint.y, secondTint.z, secondTint.w * opacity)
                    .putVec4(1.0F, 1.0F, 1.0F, opacity)
                    .putVec4(0.0F, scale, intensity, speed)
                    .putVec4(height, darkened ? 1.0F : 0.0F, 0.0F, 0.0F)
                    .get()
            );
            encoder.writeToBuffer(
                this.cameraUniforms.slice(), Std140Builder.onStack(stack, 128).putMat4f(this.inverseProjection.set(projection).invert()).putMat4f(this.inverseView.set(view).invert()).get()
            );
        }

        try (RenderPass pass = encoder.createRenderPass(() -> AURORA_PASS_LABEL, target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(this.auroraPipeline));
            pass.setUniform(SHADER_SKY_DATA, this.skyUniforms);
            pass.setUniform(AURORA_VIEW, this.cameraUniforms);
            pass.draw(3, 1, 0, 0);
        }
    }

    @Override
    public void close() {
        if (this.skyUniforms != null) {
            this.skyUniforms.close();
        }

        if (this.cameraUniforms != null) {
            this.cameraUniforms.close();
        }

        this.skyUniforms = this.cameraUniforms = null;
    }
}
