package morfeov.coliseum.render.coliseimvisual;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.*;
import morfeov.coliseum.render.hands.HandsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.*;

/** Original 1.21.11 unlit additive materials, batched before the world depth is cleared. */
public final class EspEffects {
    public static final RenderPipeline BILLBOARD = pipeline("target_billboard", "BillboardData", true,
        new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO));
    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};
    public static final RenderPipeline MUSIC_TEXT = pipeline("music_text", "BillboardData", true,
        new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA));
    private record Quad(float[] positions, float[] uv, int[] colors) {}
    private static final Map<Identifier,List<Quad>> batches = new LinkedHashMap<>();
    private EspEffects() {}
    private static RenderPipeline pipeline(String shader,String uniform,boolean textured,BlendFunction blend) {
        var builder=VisualPipelineBuilder.builder().withLocation("coliseum/pipeline/"+shader)
            .withVertexShader(Identifier.fromNamespaceAndPath("coliseum","core/"+shader))
            .withFragmentShader(Identifier.fromNamespaceAndPath("coliseum","core/"+shader))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR,PrimitiveTopology.TRIANGLES)
            .withUniform(uniform,UniformType.UNIFORM_BUFFER).withDepthTest(CompareOp.ALWAYS_PASS)
            .withDepthWrite(false).withCull(false).withBlend(blend);
        if(textured)builder.withSampler("Sampler0");
        return builder.buildPipeline();
    }
    public static void begin(){batches.clear();}
    public static void quad(Identifier texture,float[] xyz,float[] uv,int[] colors){
        batches.computeIfAbsent(texture,k->new ArrayList<>()).add(new Quad(xyz,uv,colors));
    }
    public static void render(Matrix4f projection,Matrix4f view){
        if(batches.isEmpty())return;
        var mc=Minecraft.getInstance();var target=mc.gameRenderer.mainRenderTarget();
        if(target==null||target.getColorTextureView()==null){batches.clear();return;}
        Matrix4f mvp=new Matrix4f(projection).mul(view);
        try (var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            // The camera matrix is shared by every texture batch in this frame.
            var data=com.mojang.blaze3d.buffers.Std140Builder.onStack(stack,64).putMat4f(mvp);
            var uniform=TransientGpuBuffers.uploadUniforms(data.get());
            for(var entry:batches.entrySet()){
                // A first-use texture can upload GPU data. Resolve it before
                // opening the pass: uploads inside a pass throw every frame.
                var texture=mc.getTextureManager().getTexture(entry.getKey());
                var sampler=HandsFx.linear();
                var compiled=RenderSystem.getCompiledPipeline(entry.getKey().getPath().startsWith("textures/font/")?MUSIC_TEXT:BILLBOARD);
                var vertices=SharedVertexBuffer.shared().beginVertices(PrimitiveTopology.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
                for(Quad q:entry.getValue())for(int i:QUAD_INDICES)
                    vertices.addVertex(q.positions[i*3],q.positions[i*3+1],q.positions[i*3+2]).setUv(q.uv[i*2],q.uv[i*2+1]).setColor(q.colors[i]);
                try(MeshData mesh=vertices.build()){
                    if(mesh==null)continue;
                    var vertexData=TransientGpuBuffers.uploadVertices(mesh.vertexBuffer(),DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
                    try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Coliseum original ESP",target.getColorTextureView(),Optional.empty())){
                        pass.setPipeline(compiled);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("BillboardData",uniform);
                        pass.setUniform("Sampler0",texture.getTextureView(),sampler);
                        pass.setVertexBuffer(0,vertexData);
                        pass.draw(mesh.drawState().vertexCount(),1,0,0);
                    }
                }
            }
        } finally {batches.clear();}
    }
}
