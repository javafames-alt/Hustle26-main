package im.yaHustler.render.chams;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import im.yaHustler.module.impl.render.Chams;
import im.yaHustler.render.coliseimvisual.TransientGpuBuffers;
import im.yaHustler.render.coliseimvisual.WorldRenderMatrices;
import java.util.*;
import java.util.OptionalDouble;
import org.joml.Matrix4f;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class ChamsModels {
    private record Draw(ByteBufferBuilder storage,MeshData mesh,Identifier texture,boolean armor,boolean retained) implements AutoCloseable {public void close(){mesh.close();if(retained)storage.clear();else storage.close();}}
    private static final List<Draw> commands=new ArrayList<>();
    private static final List<ByteBufferBuilder> storagePool=new ArrayList<>();
    private static boolean prepared;
    private ChamsModels(){}
    public static void begin(){clear();prepared=false;ChamsDepth.beginFrame();Chams.worldSubmission=true;}
    public static void prepare(){if(prepared)return;prepared=true;Chams.beginBlurFrame();}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void capture(Model model,Object state,PoseStack pose,Identifier texture,boolean armor){
        if(!(state instanceof AvatarRenderState avatar)||!Chams.applies(avatar))return;
        boolean outline=Chams.instance.mode.is(Chams.OUTLINE);
        if(outline&&(texture==null||!ChamsOutlinePass.ready()))return;
        if(outline)ChamsOutlinePass.drawn=true;
        if(!armor&&Chams.instance.mode.is("Свечение"))ChamsGlowPass.drawn=true;
        if(!armor&&Chams.instance.mode.is("Блюр")){ChamsBlurPass.maskType(texture);ChamsBlurPass.mark();}
        int index=commands.size(); boolean retained=index<64;
        if(retained&&index==storagePool.size())storagePool.add(new ByteBufferBuilder(65536));
        ByteBufferBuilder storage=retained?storagePool.get(index):new ByteBufferBuilder(65536);
        try{BufferBuilder vertices=new BufferBuilder(storage,PrimitiveTopology.QUADS,DefaultVertexFormat.ENTITY);model.setupAnim(state);model.renderToBuffer(pose,vertices,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,armor&&!outline?-1:Chams.tint(avatar));MeshData mesh=vertices.build();if(mesh==null){if(retained)storage.clear();else storage.close();return;}commands.add(new Draw(storage,mesh,texture,armor,retained));}catch(RuntimeException error){if(retained)storage.clear();else storage.close();throw error;}
    }
    /** True when extra geometry of this avatar (cosmetics, 3D skin layers) should join the Chams outline. */
    public static boolean capturesOutline(Object state){return state instanceof AvatarRenderState avatar&&Chams.outlineApplies(avatar);}
    /** Captures custom geometry (cosmetics) for «Обводка» with the pose it was submitted with. */
    public static void captureGeometry(Object state,Identifier texture,PoseStack.Pose pose,SubmitNodeCollector.CustomGeometryRenderer geometry){
        if(pose==null||geometry==null||!capturesOutline(state))return;
        PoseStack.Pose snapshot=pose.copy();
        captureMesh((AvatarRenderState)state,texture,vertices->geometry.render(snapshot,vertices));
    }
    /** Captures geometry written directly into the given consumer (3D skin layers) for «Обводка». */
    public static void captureGeometry(Object state,Identifier texture,Consumer<VertexConsumer> geometry){
        if(geometry==null||!capturesOutline(state))return;
        captureMesh((AvatarRenderState)state,texture,geometry);
    }
    private static void captureMesh(AvatarRenderState avatar,Identifier texture,Consumer<VertexConsumer> geometry){
        if(texture==null)return;
        int index=commands.size(); boolean retained=index<64;
        if(retained&&index==storagePool.size())storagePool.add(new ByteBufferBuilder(65536));
        ByteBufferBuilder storage=retained?storagePool.get(index):new ByteBufferBuilder(65536);
        MeshData mesh;
        try{BufferBuilder vertices=new BufferBuilder(storage,PrimitiveTopology.QUADS,DefaultVertexFormat.ENTITY);geometry.accept(vertices);mesh=vertices.build();}
        catch(RuntimeException error){release(storage,retained);return;}
        if(mesh==null){release(storage,retained);return;}
        try{recolor(mesh,Chams.tint(avatar));}catch(RuntimeException ignored){}
        commands.add(new Draw(storage,mesh,texture,false,retained));
        ChamsOutlinePass.drawn=true;
    }
    private static void release(ByteBufferBuilder storage,boolean retained){if(retained)storage.clear();else storage.close();}
    /** Cosmetics write their own vertex colours; the outline mask only needs the damage marker colour. */
    private static void recolor(MeshData mesh,int argb){
        var state=mesh.drawState();var color=state.format().getElement("Color");
        if(color==null)return;
        ByteBuffer data=mesh.vertexBuffer();int stride=state.format().getVertexSize(),offset=color.offset(),base=data.position();
        byte r=(byte)(argb>>16),g=(byte)(argb>>8),b=(byte)argb,a=(byte)(argb>>>24);
        for(int i=0,count=state.vertexCount();i<count;i++){int at=base+i*stride+offset;data.put(at,r);data.put(at+1,g);data.put(at+2,b);data.put(at+3,a);}
    }
    public static void render(){
        Chams.endWorldSubmission();
        try{
            if(Chams.instance==null||!Chams.instance.enabled()||commands.isEmpty())return;
            if(Chams.instance.mode.is("Flat")){Chams.renderBlur();ChamsDepth.clear();return;}
            var mc=Minecraft.getInstance();var encoder=RenderSystem.getDevice().createCommandEncoder();var main=mc.gameRenderer.mainRenderTarget();
            ChamsDepth.view();
            if(ChamsGlowPass.ready())encoder.copyTextureToTexture(main.getDepthTexture(),ChamsGlowPass.terrainDepth,0,0,0,0,0,main.width,main.height);
            com.mojang.renderpearl.api.buffers.GpuBufferSlice transforms;
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){transforms=TransientGpuBuffers.uploadUniforms(com.mojang.blaze3d.buffers.Std140Builder.onStack(stack,160).putMat4f(WorldRenderMatrices.viewMatrix()).putMat4f(new Matrix4f()).putVec4(1,1,1,1).putVec4(0,0,0,0).get());}
            boolean blurMode=Chams.instance.mode.is("Блюр"),outlineMode=Chams.instance.mode.is(Chams.OUTLINE);
            for(Draw draw:commands){RenderTarget target=blurMode?ChamsBlurPass.mask:outlineMode?ChamsOutlinePass.mask:draw.armor?ChamsGlowPass.armor:ChamsGlowPass.body;RenderPipeline pipeline=blurMode?ChamsBlurPass.maskPipeline:ChamsGlowPass.maskPipeline;if(target==null||pipeline==null)continue;
                var mesh=draw.mesh;var state=mesh.drawState();var data=TransientGpuBuffers.uploadVertices(mesh.vertexBuffer(),state.format().getVertexSize());var indices=RenderSystem.getSequentialBuffer(state.primitiveTopology());var indexBuffer=indices.getBuffer(state.indexCount());var texture=mc.getTextureManager().getTexture(draw.texture);
                try(var pass=encoder.createRenderPass(()->"Hustle 26.3 Chams model",target.getColorTextureView(),Optional.empty(),target.getDepthTextureView(),OptionalDouble.empty())){pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));RenderSystem.bindDefaultUniforms(pass);pass.setUniform("DynamicTransforms",transforms);pass.setUniform("Sampler0",texture.getTextureView(),texture.getSampler());pass.setVertexBuffer(0,data);pass.setIndexBuffer(indexBuffer,indices.type());pass.drawIndexed(state.indexCount(),1,0,0,0);}
            }
            Chams.renderBlur();
        }finally{clear();}
    }
    public static void clear(){for(Draw draw:commands)draw.close();commands.clear();}
    public static void close(){
        clear();for(var storage:storagePool)storage.close();storagePool.clear();prepared=false;ChamsBlurPass.clear();ChamsGlowPass.cancelFrame();ChamsOutlinePass.close();ChamsDepth.clear();
        for(var buffer:new com.mojang.renderpearl.api.buffers.GpuBuffer[]{ChamsBlurPass.settings,ChamsBlurPass.vertex,ChamsGlowPass.vertex,ChamsGlowPass.visibilitySettings,ChamsGlowPass.horizontalSettings,ChamsGlowPass.verticalSettings,ChamsGlowPass.wideHorizontalSettings,ChamsGlowPass.wideVerticalSettings,ChamsGlowPass.compositeSettings})if(buffer!=null)buffer.close();
        ChamsBlurPass.settings=null;ChamsBlurPass.vertex=null;ChamsGlowPass.vertex=null;ChamsGlowPass.visibilitySettings=null;ChamsGlowPass.horizontalSettings=null;ChamsGlowPass.verticalSettings=null;ChamsGlowPass.wideHorizontalSettings=null;ChamsGlowPass.wideVerticalSettings=null;ChamsGlowPass.compositeSettings=null;
    }
}