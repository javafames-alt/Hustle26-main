package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import im.yaHustler.Hustle26;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.render.hands.HandsFx;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.Optional;

@ModuleInfo(id="saturation",name="Saturation",category=Category.RENDER,description="Насыщенность, сочность, контраст и яркость изображения")
public final class Saturation extends Module implements AutoCloseable {
    private final NumberSetting saturation=add(new NumberSetting("Насыщенность",1.3,0,3,.05));
    private final NumberSetting vibrance=add(new NumberSetting("Сочность",.2,0,1,.05));
    private final NumberSetting contrast=add(new NumberSetting("Контраст",1.05,.5,2,.05));
    private final NumberSetting brightness=add(new NumberSetting("Яркость",1,.5,1.5,.05));
    private TextureTarget output;
    private GpuBuffer uniforms;
    private boolean failed;
    private static RenderPipeline pipeline;

    public static RenderPipeline pipeline() {
        if(pipeline==null) pipeline=RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("hustle263","pipeline/saturation"))
            .withVertexShader(Identifier.fromNamespaceAndPath("hustle263","core/coliseimvisual/hands_fullscreen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("hustle263","core/saturation"))
            .withBindGroupLayout(HandsFx.layout(b->HandsFx.ubo(HandsFx.sampler(b,"Sampler0"),"SaturationData")))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,15))
            .withDepthStencilState(Optional.empty()).withCull(false).build();
        return pipeline;
    }
    @Override protected void onEnable() { failed=false; }
    @Override protected void onDisable() { close(); }
    public static boolean neutral(float saturation,float vibrance,float contrast,float brightness) {
        return Math.abs(saturation-1)<.001f && Math.abs(vibrance)<.001f && Math.abs(contrast-1)<.001f && Math.abs(brightness-1)<.001f;
    }
    public void render() {
        var mc=Minecraft.getInstance();
        if(!enabled() || failed || mc.level==null || mc.player==null || neutral(saturation.floatValue(),vibrance.floatValue(),contrast.floatValue(),brightness.floatValue()))return;
        var scene=mc.gameRenderer.mainRenderTarget();
        if(scene==null || scene.width<=0 || scene.height<=0 || scene.getColorTexture()==null || scene.getColorTextureView()==null || scene.getColorTexture().getFormat()!=GpuFormat.RGBA8_UNORM)return;
        try {
            if(output!=null && (output.width!=scene.width || output.height!=scene.height)) { output.destroyBuffers();output=null; }
            if(output==null)output=new TextureTarget("Saturation output",scene.width,scene.height,GpuFormat.RGBA8_UNORM,null);
            if(uniforms==null)uniforms=HandsFx.createUniforms("Saturation settings",16);
            HandsFx.writeUniforms(uniforms,16,b->b.putVec4(saturation.floatValue(),vibrance.floatValue(),contrast.floatValue(),brightness.floatValue()));
            HandsFx.pass("Hustle 26.3 Saturation",pipeline(),output.getColorTextureView(),pass->{
                pass.setUniform("Sampler0",scene.getColorTextureView(),HandsFx.linear());
                pass.setUniform("SaturationData",uniforms);
            });
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(output.getColorTexture(),scene.getColorTexture(),0,0,0,0,0,scene.width,scene.height);
        } catch(RuntimeException error) {
            failed=true;close();Hustle26.LOGGER.error("Saturation failed; toggle the module to retry",error);
        }
    }
    @Override public void close() {
        if(output!=null) { output.destroyBuffers();output=null; }
        if(uniforms!=null) { uniforms.close();uniforms=null; }
    }
}
