package im.yaHustler.render.coliseimvisual;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class VolumetricFogRenderer implements AutoCloseable {
    private static final String HUSTLE_AMBIENCE_VOLUME_FOG = "hustle263/coliseimvisual/ambience_volume_fog";
    private static final String HANDS_FULLSCREEN = "hands_fullscreen";
    private static final String AMBIENCE_VOLUME_FOG = "ambience_volume_fog";
    private static final String DEPTH_SAMPLER = "DepthSampler";
    private static final String DENSITY_SAMPLER = "DensitySampler";
    private static final String VOLUME_FOG = "VolumeFog";
    private static final String HUSTLE_AMBIENCE_FOG_DENSITY = "hustle263/coliseimvisual/ambience_fog_density";
    private static final String FOG_DENSITY_PASS = "FOG_DENSITY_PASS";
    private static final String AMBIENCE_FOG_DENSITY = "Ambience fog density";
    private static final String AMBIENCE_VOLUMETRIC_FOG = "Ambience volumetric fog";
    private static final String AMBIENCE_VOLUME_FOG_UNIFORMS = "Ambience volume fog uniforms";

    private final RenderPipeline compositePipeline;
    private final RenderPipeline densityPipeline;
    private final Matrix4f inverseProjection;
    private final Matrix4f inverseView;
    private GpuBuffer fogUniforms;
    private TextureTarget densityTarget;

    public void renderVolumeFog(RenderTarget target, Matrix4f projection, Matrix4f view, Vec3 cameraPosition, float density, int color, float start, float end) {
        if (target != null
            && target.getColorTextureView() != null
            && target.getDepthTextureView() != null
            && !target.getDepthTexture().isClosed()
            && projection != null
            && view != null
            && !(Math.abs(projection.determinant()) < 1.0E-8F)
            && !(Math.abs(view.determinant()) < 1.0E-8F)) {
            if (this.fogUniforms == null || this.fogUniforms.isClosed()) {
                this.fogUniforms = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_VOLUME_FOG_UNIFORMS, 136, 176L);
            }

            int densityWidth = Math.max(1, (target.width + 3) / 4);
            int densityHeight = Math.max(1, (target.height + 3) / 4);
            if (this.densityTarget == null) {
                this.densityTarget = new TextureTarget(AMBIENCE_FOG_DENSITY, densityWidth, densityHeight, GpuFormat.RGBA8_UNORM, null);
            } else if (this.densityTarget.width != densityWidth || this.densityTarget.height != densityHeight) {
                this.densityTarget.resize(densityWidth, densityHeight);
            }

            this.inverseProjection.set(projection).invert();
            this.inverseView.set(view).invert();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer uniformData = Std140Builder.onStack(stack, 176)
                    .putMat4f(this.inverseProjection)
                    .putMat4f(this.inverseView)
                    .putVec4((float)(cameraPosition.x % 8192.0), (float)(cameraPosition.y % 8192.0), (float)(cameraPosition.z % 8192.0), density)
                    .putVec4((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F)
                    .putVec4(start, end * 2.0F, 3.0F / Math.max(1.0F, end - start), 0.0F)
                    .get();
                encoder.writeToBuffer(this.fogUniforms.slice(), uniformData);
            }

            try (RenderPass densityPass = encoder.createRenderPass(() -> AMBIENCE_FOG_DENSITY, this.densityTarget.getColorTextureView(), Optional.empty())) {
                densityPass.setPipeline(RenderSystem.getCompiledPipeline(this.densityPipeline));
                densityPass.setUniform(VOLUME_FOG, this.fogUniforms);
                densityPass.setUniform(DEPTH_SAMPLER, target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                densityPass.draw(3, 1, 0, 0);
            }

            try (RenderPass compositePass = encoder.createRenderPass(() -> AMBIENCE_VOLUMETRIC_FOG, target.getColorTextureView(), Optional.empty())) {
                compositePass.setPipeline(RenderSystem.getCompiledPipeline(this.compositePipeline));
                compositePass.setUniform(VOLUME_FOG, this.fogUniforms);
                compositePass.setUniform(DEPTH_SAMPLER, target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                compositePass.setUniform(DENSITY_SAMPLER, this.densityTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                compositePass.draw(3, 1, 0, 0);
            }
        }
    }

    public VolumetricFogRenderer() {
        this.compositePipeline = VisualPipelineBuilder.builder()
            .withLocation(HUSTLE_AMBIENCE_VOLUME_FOG)
            .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN))
            .withFragmentShader(VisualResources.shader(AMBIENCE_VOLUME_FOG))
            .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
            .withDepthTest(CompareOp.ALWAYS_PASS)
            .withDepthWrite(false)
            .withCull(false)
            .withBlend(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE))
            .withSampler(DEPTH_SAMPLER)
            .withSampler(DENSITY_SAMPLER)
            .withUniform(VOLUME_FOG, UniformType.UNIFORM_BUFFER)
            .buildPipeline();
        this.densityPipeline = VisualPipelineBuilder.builder()
            .withLocation(HUSTLE_AMBIENCE_FOG_DENSITY)
            .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN))
            .withFragmentShader(VisualResources.shader(AMBIENCE_VOLUME_FOG))
            .withDefine(FOG_DENSITY_PASS)
            .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
            .withDepthTest(CompareOp.ALWAYS_PASS)
            .withDepthWrite(false)
            .withCull(false)
            .withoutBlend()
            .withSampler(DEPTH_SAMPLER)
            .withUniform(VOLUME_FOG, UniformType.UNIFORM_BUFFER)
            .buildPipeline();
        this.inverseProjection = new Matrix4f();
        this.inverseView = new Matrix4f();
    }

    @Override
    public void close() {
        if (this.densityTarget != null) {
            this.densityTarget.destroyBuffers();
            this.densityTarget = null;
        }

        if (this.fogUniforms != null) {
            this.fogUniforms.close();
            this.fogUniforms = null;
        }
    }
}
