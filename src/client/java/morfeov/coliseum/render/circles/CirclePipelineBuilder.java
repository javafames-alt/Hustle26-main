package morfeov.coliseum.render.circles;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Native 26.3 world pipelines used by the six jump effects. */
public final class CirclePipelineBuilder {
    public static final VertexFormat NO_VERTICES=VertexFormat.builder(0).build();
    public static final RenderPipeline.Snippet WORLD_PROJECTION=RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION).buildSnippet();
    private final RenderPipeline.Builder pipeline;
    private final BindGroupLayout.Builder bindings=BindGroupLayout.builder();
    private final Set<String> uniforms=new HashSet<>();
    private CompareOp depth=CompareOp.GREATER_THAN_OR_EQUAL;
    private boolean writeDepth=true;
    private BlendFunction blend;

    private CirclePipelineBuilder(RenderPipeline.Snippet... snippets) {pipeline=RenderPipeline.builder(snippets);}
    public static CirclePipelineBuilder builder(RenderPipeline.Snippet... snippets) {return new CirclePipelineBuilder(snippets);}
    public CirclePipelineBuilder location(String name) {
        pipeline.withLocation(Identifier.fromNamespaceAndPath("coliseum","pipeline/jumpcircles/"+name.replace("coliseum/coliseimvisual/","")));
        return this;
    }
    public CirclePipelineBuilder vertexShader(Identifier id) {pipeline.withVertexShader(id);return this;}
    public CirclePipelineBuilder fragmentShader(Identifier id) {pipeline.withFragmentShader(id);return this;}
    public CirclePipelineBuilder depthTest(CompareOp compare) {depth=compare;return this;}
    public CirclePipelineBuilder writeDepth(boolean enabled) {writeDepth=enabled;return this;}
    public CirclePipelineBuilder cull(boolean enabled) {pipeline.withCull(enabled);return this;}
    public CirclePipelineBuilder vertexFormat(VertexFormat format,PrimitiveTopology topology) {
        if(format.getVertexSize()>0) pipeline.withVertexBinding(0,format);
        pipeline.withPrimitiveTopology(topology);
        return this;
    }
    public CirclePipelineBuilder uniform(String name,UniformType type) {
        if(uniforms.add(name)) bindings.withUniform(name,type);
        return this;
    }
    public CirclePipelineBuilder sampler(String name) {return uniform(name,UniformType.COMBINED_IMAGE_SAMPLER);}
    public CirclePipelineBuilder blend(BlendFunction value) {blend=value;return this;}
    public CirclePipelineBuilder noBlend() {blend=null;return this;}
    public RenderPipeline build() {
        if(!uniforms.isEmpty()) pipeline.withBindGroupLayout(bindings.build());
        pipeline.withDepthStencilState(depth==CompareOp.ALWAYS_PASS?Optional.empty():Optional.of(new DepthStencilState(depth,writeDepth,0,0)));
        pipeline.withColorTargetState(new ColorTargetState(Optional.ofNullable(blend),GpuFormat.RGBA8_UNORM,15));
        return pipeline.build();
    }
}
