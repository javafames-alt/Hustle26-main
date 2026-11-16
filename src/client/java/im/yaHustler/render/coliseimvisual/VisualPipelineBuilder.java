package im.yaHustler.render.coliseimvisual;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class VisualPipelineBuilder {
    public static final VertexFormat VERTEXFORMAT = VertexFormat.builder(0).build();
    public static final Snippet SNIPPET = RenderPipeline.builder(new Snippet[0]).withBindGroupLayout(BindGroupLayouts.PROJECTION).buildSnippet();
    private final Builder builder;
    private final com.mojang.renderpearl.api.pipeline.BindGroupLayout.Builder bindGroupBuilder = BindGroupLayout.builder();
    private final Set<String> uniformNames = new HashSet<>();
    private boolean bindGroupDirty;
    private CompareOp depthTest = CompareOp.GREATER_THAN_OR_EQUAL;
    private boolean writeDepth = true;
    private boolean depthStateDirty;
    private float depthBiasSlope;
    private float depthBiasConstant;
    private Optional<BlendFunction> blendFunction = Optional.empty();
    private int colorWriteMask = 15;
    private boolean colorStateDirty;
    private GpuFormat colorFormat = GpuFormat.RGBA8_UNORM;

    public VisualPipelineBuilder withColorFormat(GpuFormat format) {
        this.colorFormat = format;
        this.colorStateDirty = true;
        return this;
    }

    public VisualPipelineBuilder withPolygonMode(PolygonMode polygonMode) {
        this.builder.withPolygonMode(polygonMode);
        return this;
    }

    private VisualPipelineBuilder(Snippet... snippets) {
        this.builder = RenderPipeline.builder(snippets);

        for (Snippet snippet : snippets) {
            snippet.bindGroupLayouts().ifPresent(state -> state.forEach(layout -> {

                layout.uniforms().forEach(uniform -> this.uniformNames.add(uniform.name()));
            }));
            snippet.depthStencilState().ifPresent(state -> {
                this.depthTest = state.depthTest();
                this.writeDepth = state.writeDepth();
                this.depthBiasSlope = state.depthBiasScaleFactor();
                this.depthBiasConstant = state.depthBiasConstant();
            });
            if (snippet.colorTargetStates() != null && snippet.activeColorTargetStateCount() > 0 && snippet.colorTargetStates()[0] != null) {
                this.colorFormat = snippet.colorTargetStates()[0].format();
                this.blendFunction = snippet.colorTargetStates()[0].blendFunction();
                this.colorWriteMask = snippet.colorTargetStates()[0].writeMask();
            }
        }
    }

    public static VisualPipelineBuilder builder(Snippet... snippets) {
        return new VisualPipelineBuilder(snippets);
    }

    public VisualPipelineBuilder withDepthTest(CompareOp comparison) {
        this.depthTest = comparison;
        this.depthStateDirty = true;
        return this;
    }

    public VisualPipelineBuilder withColorWrite(boolean rgb, boolean alpha) {
        this.colorWriteMask = (rgb ? 7 : 0) | (alpha ? 8 : 0);
        this.colorStateDirty = true;
        return this;
    }

    public VisualPipelineBuilder withDepthBias(float slope, float constant) {
        this.depthBiasSlope = slope;
        this.depthBiasConstant = constant;
        this.depthStateDirty = true;
        return this;
    }

    public RenderPipeline buildPipeline() {
        this.applyPendingState();
        return this.builder.build();
    }

    public VisualPipelineBuilder withFloatDefine(String name, float value) {
        this.builder.withShaderDefine(name, value);
        return this;
    }

    private void applyPendingState() {
        if (this.bindGroupDirty) {
            this.builder.withBindGroupLayout(this.bindGroupBuilder.build());
            this.bindGroupDirty = false;
        }

        if (this.depthStateDirty) {
            this.builder
                .withDepthStencilState(
                    this.depthTest == CompareOp.ALWAYS_PASS ? Optional.empty() : Optional.of(new DepthStencilState(this.depthTest, this.writeDepth, this.depthBiasSlope, this.depthBiasConstant))
                );
        }

        if (this.colorStateDirty) {
            this.builder.withColorTargetState(new ColorTargetState(this.blendFunction, this.colorFormat, this.colorWriteMask));
        }
    }

    public VisualPipelineBuilder withCull(boolean cull) {
        this.builder.withCull(cull);
        return this;
    }

    public VisualPipelineBuilder withLocation(String location) {
        this.builder.withLocation(location);
        return this;
    }

    public VisualPipelineBuilder withoutBlend() {
        this.blendFunction = Optional.empty();
        this.colorStateDirty = true;
        return this;
    }

    public Snippet buildSnippet() {
        this.applyPendingState();
        return this.builder.buildSnippet();
    }

    public VisualPipelineBuilder withUniform(String name, UniformType type) {
        if (this.uniformNames.add(name)) {
            this.bindGroupBuilder.withUniform(name, type);
            this.bindGroupDirty = true;
        }

        return this;
    }

    public VisualPipelineBuilder withFragmentShader(String shader) {
        this.builder.withFragmentShader(shader);
        return this;
    }

    public VisualPipelineBuilder withBlend(BlendFunction blend) {
        this.blendFunction = Optional.of(blend);
        this.colorStateDirty = true;
        return this;
    }

    public VisualPipelineBuilder withIntDefine(String name, int value) {
        this.builder.withShaderDefine(name, value);
        return this;
    }

    public VisualPipelineBuilder withVertexShader(Identifier shader) {
        this.builder.withVertexShader(shader);
        return this;
    }

    public VisualPipelineBuilder withDefine(String name) {
        this.builder.withShaderDefine(name);
        return this;
    }

    public VisualPipelineBuilder withDepthWrite(boolean enabled) {
        this.writeDepth = enabled;
        this.depthStateDirty = true;
        return this;
    }

    public VisualPipelineBuilder withVertexShader(String shader) {
        this.builder.withVertexShader(shader);
        return this;
    }

    public VisualPipelineBuilder withVertexFormat(VertexFormat format, PrimitiveTopology topology) {
        if (format.getVertexSize() != 0) {
            this.builder.withVertexBinding(0, format);
        }

        this.builder.withPrimitiveTopology(topology);
        return this;
    }

    public VisualPipelineBuilder withFragmentShader(Identifier shader) {
        this.builder.withFragmentShader(shader);
        return this;
    }

    public VisualPipelineBuilder withSampler(String name) {
        if (this.uniformNames.add(name)) {
            this.bindGroupBuilder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
            this.bindGroupDirty = true;
        }

        return this;
    }

    public VisualPipelineBuilder withLocation(Identifier location) {
        this.builder.withLocation(location);
        return this;
    }
}
