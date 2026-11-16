package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.render.hands.HandsFx;
import morfeov.coliseum.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;

@ModuleInfo(id="block_overlay", name="BlockOverlay", category=Category.RENDER,
    description="Подсветка блока под прицелом: плазма, кристаллический шейдер и заливка")
public final class BlockOverlay extends Module implements AutoCloseable {
    private final ModeSetting mode = add(new ModeSetting("Режим","Плазма","Плазма","Шейдер","Заливка"));
    private final BooleanSetting outline = add(new BooleanSetting("Обводка темы",true));
    private final NumberSetting opacity = add(new NumberSetting("Прозрачность",.72,.1,1,.05));
    private final NumberSetting plasmaSpeed = add(new NumberSetting("Скорость плазмы",1.15,.1,5,.1));
    private final NumberSetting plasmaScale = add(new NumberSetting("Масштаб плазмы",2.4,.5,5,.1));
    private final NumberSetting outlineWidth = add(new NumberSetting("Ширина оутлайна",2.75,.5,8,.25));
    private final NumberSetting outlineOpacity = add(new NumberSetting("Прозрачность оутлайна",70,5,100,1));
    private final NumberSetting glow = add(new NumberSetting("Сила свечения",1.05,.2,2.5,.05));
    private final BooleanSetting smooth = add(new BooleanSetting("Плавность",true));
    private final NumberSetting smoothSpeed = add(new NumberSetting("Скорость плавности",10,2,25,.5));
    private final RenderPipeline fillPipeline = geometryPipeline("fill",true);
    private final RenderPipeline maskPipeline = geometryPipeline("mask",false);
    private final RenderPipeline plasmaPipeline = effectPipeline("plasma","BlockOverlayPlasma");
    private final RenderPipeline crystalPipeline = effectPipeline("crystal","BlockOverlayCrystal");
    private TextureTarget mask;
    private GpuBuffer plasmaUniforms, crystalUniforms;
    private ByteBufferBuilder storage;
    private AABB animatedBounds;
    private long lastFrame;
    private Object level;
    private boolean failed;

    public BlockOverlay() {
        plasmaSpeed.visibleWhen(() -> mode.is("Плазма"));
        plasmaScale.visibleWhen(() -> mode.is("Плазма"));
        outlineWidth.visibleWhen(outline::enabled);
        outlineOpacity.visibleWhen(outline::enabled);
        glow.visibleWhen(() -> outline.enabled() && !mode.is("Заливка"));
        smoothSpeed.visibleWhen(smooth::enabled);
    }
    private static Identifier shader(String name) { return Identifier.fromNamespaceAndPath("coliseum","core/coliseimvisual/"+name); }
    private static RenderPipeline geometryPipeline(String name, boolean depth) {
        return RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("coliseum","pipeline/block_overlay_"+name))
            .withVertexShader(shader("block_overlay_mask")).withFragmentShader(shader("block_overlay_mask"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withVertexBinding(0,DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(depth ? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false,0,0)) : Optional.empty())
            .withCull(false).build();
    }
    private static RenderPipeline effectPipeline(String name, String uniform) {
        return RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("coliseum","pipeline/block_overlay_"+name))
            .withVertexShader(shader("hands_fullscreen")).withFragmentShader(shader("block_overlay_"+name))
            .withBindGroupLayout(HandsFx.layout(b -> HandsFx.ubo(HandsFx.sampler(b,"MaskSampler"),uniform)))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty()).withCull(false).build();
    }
    @Override protected void onEnable() { failed=false; reset(); }
    @Override protected void onDisable() { close(); }
    private void reset() { animatedBounds=null; lastFrame=0; level=null; }

    public void render(Matrix4fc view) {
        Minecraft mc = Minecraft.getInstance();
        AABB target = targetBounds(mc);
        if (!enabled() || failed || target == null) { reset(); return; }
        if (level != mc.level) reset();
        level=mc.level;
        long now=System.nanoTime();
        if (animatedBounds == null || !smooth.enabled()) animatedBounds=target;
        else {
            double t=1-Math.exp(-smoothSpeed.get()*Math.max(0,(now-lastFrame)/1e9));
            animatedBounds=new AABB(lerp(animatedBounds.minX,target.minX,t),lerp(animatedBounds.minY,target.minY,t),lerp(animatedBounds.minZ,target.minZ,t),
                lerp(animatedBounds.maxX,target.maxX,t),lerp(animatedBounds.maxY,target.maxY,t),lerp(animatedBounds.maxZ,target.maxZ,t));
        }
        lastFrame=now;
        var camera=mc.gameRenderer.mainCamera().position();
        AABB bounds=animatedBounds.inflate(.01).move(-camera.x,-camera.y,-camera.z);
        Matrix4f matrix=new Matrix4f(view);
        RenderTarget scene=mc.gameRenderer.mainRenderTarget();
        if (scene==null || scene.width<=0 || scene.height<=0) return;
        try {
            int color=Theme.accent();
            if (mode.is("Заливка")) drawBox(matrix,bounds,color,opacity.floatValue(),scene,true);
            if (mode.is("Заливка") && !outline.enabled()) return;
            if (mask!=null && (mask.width!=scene.width || mask.height!=scene.height)) { mask.destroyBuffers(); mask=null; }
            if (mask==null) mask=new TextureTarget("BlockOverlay mask",scene.width,scene.height,GpuFormat.RGBA8_UNORM,null);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorTexture(),new Vector4f(0));
            drawBox(matrix,bounds,0xffffffff,1,mask,false);
            boolean plasma=mode.is("Плазма");
            int size=plasma?80:64;
            if (plasma && plasmaUniforms==null) plasmaUniforms=HandsFx.createUniforms("BlockOverlay plasma",size);
            if (!plasma && crystalUniforms==null) crystalUniforms=HandsFx.createUniforms("BlockOverlay crystal",size);
            GpuBuffer uniforms=plasma?plasmaUniforms:crystalUniforms;
            float red=(color>>16&255)/255f, green=(color>>8&255)/255f, blue=(color&255)/255f;
            // Fill mode reuses the mask outline with zero shader fill and glow.
            float fillOpacity=mode.is("Заливка")?0:opacity.floatValue();
            HandsFx.writeUniforms(uniforms,size,b -> {
                b.putVec4(red,green,blue,fillOpacity);
                if(plasma) b.putVec4(Math.min(1,red+70/255f),Math.min(1,green+55/255f),Math.min(1,blue+40/255f),1);
                if(plasma) b.putVec4(HandsFx.time(),plasmaSpeed.floatValue(),plasmaScale.floatValue(),outline.enabled()?1:0);
                else b.putVec4(HandsFx.time(),outline.enabled()?1:0,0,0);
                b.putVec4(outlineWidth.floatValue(),outlineOpacity.floatValue()/100,mode.is("Заливка")?0:glow.floatValue(),0);
                b.putVec2(mask.width,mask.height);
                // std140 rounds a block ending in vec2 to the next 16 byte boundary.
                b.putVec2(0,0);
            });
            HandsFx.pass("BlockOverlay",plasma?plasmaPipeline:crystalPipeline,scene.getColorTextureView(),pass -> {
                pass.setUniform("MaskSampler",mask.getColorTextureView(),HandsFx.linear());
                pass.setUniform(plasma?"BlockOverlayPlasma":"BlockOverlayCrystal",uniforms);
            });
        } catch(RuntimeException error) {
            failed=true; reset();
            morfeov.coliseum.Coliseum.LOGGER.error("BlockOverlay failed; toggle the module to retry",error);
        }
    }
    private static double lerp(double from,double to,double t) { return from+(to-from)*t; }

    private AABB targetBounds(Minecraft mc) {
        if (mc.level==null || mc.player==null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK || hit.isInside()) return null;
        Vec3 camera=mc.gameRenderer.mainCamera().position();
        if (cameraInsideBlock(mc,camera)) return null;
        BlockPos position=hit.getBlockPos();
        if(mc.player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)>36) return null;
        var state=mc.level.getBlockState(position);
        if(state.isAir()) return null;
        var shape=state.getShape(mc.level,position);
        AABB bounds=shape.isEmpty()?new AABB(position):shape.bounds().move(position);
        return bounds.contains(camera)?null:bounds;
    }
    private static boolean cameraInsideBlock(Minecraft mc,Vec3 camera) {
        for(BlockPos position:BlockPos.betweenClosed(BlockPos.containing(camera.x-.001,camera.y-.001,camera.z-.001),
                BlockPos.containing(camera.x+.001,camera.y+.001,camera.z+.001))) {
            var shape=mc.level.getBlockState(position).getCollisionShape(mc.level,position);
            if(!shape.isEmpty() && shape.bounds().contains(camera.x-position.getX(),camera.y-position.getY(),camera.z-position.getZ())) return true;
        }
        return false;
    }
    public boolean replacesVanillaOutline() {
        Minecraft mc=Minecraft.getInstance();
        return enabled() && !failed && targetBounds(mc)!=null;
    }

    private void drawBox(Matrix4f matrix,AABB bounds,int color,float alpha,RenderTarget target,boolean depth) {
        if(storage==null) storage=new ByteBufferBuilder(8192);
        BufferBuilder vertices=new BufferBuilder(storage,PrimitiveTopology.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        box(vertices,matrix,bounds,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);
        try(MeshData mesh=vertices.build()) {
            if(mesh==null) return;
            var draw=mesh.drawState();
            var indices=RenderSystem.getSequentialBuffer(draw.primitiveTopology());
            var indexBuffer=indices.getBuffer(draw.indexCount());
            try(GpuBuffer buffer=RenderSystem.getDevice().createBuffer(() -> "BlockOverlay vertices",GpuBuffer.USAGE_VERTEX,mesh.vertexBuffer());
                var pass=depth?RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "BlockOverlay fill",target.getColorTextureView(),Optional.empty(),target.getDepthTextureView(),OptionalDouble.empty())
                    :RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "BlockOverlay mask",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(depth?fillPipeline:maskPipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setVertexBuffer(0,buffer.slice()); pass.setIndexBuffer(indexBuffer,indices.type());
                pass.drawIndexed(draw.indexCount(),1,0,0,0);
            }
        }
    }
    private static void box(BufferBuilder vertices,Matrix4f matrix,AABB bounds,float red,float green,float blue,float alpha) {
        float x0=(float)bounds.minX,y0=(float)bounds.minY,z0=(float)bounds.minZ;
        float x1=(float)bounds.maxX,y1=(float)bounds.maxY,z1=(float)bounds.maxZ;
        quad(vertices,matrix,red,green,blue,alpha,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
        quad(vertices,matrix,red,green,blue,alpha,x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0);
        quad(vertices,matrix,red,green,blue,alpha,x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0);
        quad(vertices,matrix,red,green,blue,alpha,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);
        quad(vertices,matrix,red,green,blue,alpha,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
        quad(vertices,matrix,red,green,blue,alpha,x1,y0,z0,x1,y1,z0,x1,y1,z1,x1,y0,z1);
    }
    private static void quad(BufferBuilder vertices,Matrix4f matrix,float red,float green,float blue,float alpha,float... xyz) {
        for(int i=0;i<4;i++) vertices.addVertex(matrix,xyz[i*3],xyz[i*3+1],xyz[i*3+2])
            .setUv(i==1||i==2?1:0,i>=2?1:0).setColor(red,green,blue,alpha);
    }
    @Override public void close() {
        reset();
        if(mask!=null) { mask.destroyBuffers(); mask=null; }
        if(plasmaUniforms!=null) { plasmaUniforms.close(); plasmaUniforms=null; }
        if(crystalUniforms!=null) { crystalUniforms.close(); crystalUniforms=null; }
        if(storage!=null) { storage.close(); storage=null; }
    }
}
