package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.pipeline.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.render.coliseimvisual.SharedVertexBuffer;
import morfeov.coliseum.render.coliseimvisual.TransientGpuBuffers;
import morfeov.coliseum.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;
import java.util.*;

@ModuleInfo(id="block_esp",name="BlockESP",category=Category.RENDER,description="Подсвечивает блоки из списка .blockesp add/remove/clear/list")
public final class BlockESP extends Module {
    private final BlockListSetting selected=add(new BlockListSetting("Блоки"));
    private final NumberSetting range=add(new NumberSetting("Дальность",32,8,64,8));
    private final NumberSetting limit=add(new NumberSetting("Лимит отображения",2048,128,8192,128));
    private final ColorSetting color=add(new ColorSetting("Цвет",0xff45dfed));
    private final Map<Long,LongArrayList> sections=new HashMap<>();
    private final Set<Block> blocks=new HashSet<>();
    private final List<SectionPos> pending=new ArrayList<>();
    private String cached="";
    private Object level;
    private int centerX=Integer.MIN_VALUE,centerY,centerZ,lastRange,cursor;
    private static RenderPipeline pipeline;

    public List<String> blockIds() { return selected.get().isBlank()?List.of():List.of(selected.get().split(",")); }
    public static Identifier blockId(String input) {
        var id=Identifier.tryParse(input.toLowerCase(Locale.ROOT));
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.getValue(id).defaultBlockState().isAir()) return null;
        return id;
    }
    public boolean addBlock(String input) {
        var id=blockId(input);
        if(id==null) throw new IllegalArgumentException("Неизвестный блок: "+input);
        var ids=new LinkedHashSet<>(blockIds());
        if(!ids.add(id.toString()))return false;
        selected.set(String.join(",",ids));reset();return true;
    }
    public boolean removeBlock(String input) {
        var id=Identifier.tryParse(input.toLowerCase(Locale.ROOT));
        var ids=new LinkedHashSet<>(blockIds());
        if(id==null || !ids.remove(id.toString()))return false;
        selected.set(String.join(",",ids));reset();return true;
    }
    public void clearBlocks() { selected.set("");reset(); }
    private void syncBlocks() {
        if(cached.equals(selected.get()))return;
        blocks.clear();
        for(String name:blockIds()) { var id=blockId(name);if(id!=null)blocks.add(BuiltInRegistries.BLOCK.getValue(id)); }
        cached=selected.get();reset();
    }
    private void reset() { sections.clear();pending.clear();cursor=0;centerX=Integer.MIN_VALUE;level=null; }
    @Override protected void onDisable() { reset(); }
    @Override public void tick(Minecraft mc) {
        syncBlocks();
        if(mc.level==null || mc.player==null || blocks.isEmpty()) { reset();return; }
        if(level!=mc.level) { reset();level=mc.level; }
        var origin=mc.player.blockPosition();
        int cx=origin.getX()>>4,cy=origin.getY()>>4,cz=origin.getZ()>>4,r=range.intValue();
        if(cx!=centerX || cy!=centerY || cz!=centerZ || r!=lastRange) {
            centerX=cx;centerY=cy;centerZ=cz;lastRange=r;pending.clear();cursor=0;
            int minY=Math.max(mc.level.getMinY()>>4,(origin.getY()-r)>>4),maxY=Math.min((mc.level.getMaxY()-1)>>4,(origin.getY()+r)>>4);
            for(int x=(origin.getX()-r)>>4;x<=(origin.getX()+r)>>4;x++)
                for(int z=(origin.getZ()-r)>>4;z<=(origin.getZ()+r)>>4;z++)
                    for(int y=minY;y<=maxY;y++) pending.add(SectionPos.of(x,y,z));
            pending.sort(Comparator.comparingDouble(p->p.center().distSqr(origin)));
            var retained=new HashSet<Long>();for(var section:pending)retained.add(section.asLong());
            sections.keySet().retainAll(retained);
        }
        long deadline=System.nanoTime()+2_000_000L;
        for(int count=0;count<16 && !pending.isEmpty();count++) {
            if(cursor>=pending.size())cursor=0;
            scan(mc,pending.get(cursor++));
            if(System.nanoTime()>=deadline)break;
        }
    }
    private void scan(Minecraft mc,SectionPos position) {
        long key=position.asLong();
        var chunk=mc.level.getChunkSource().getChunk(position.x(),position.z(),ChunkStatus.FULL,false);
        if(chunk==null) { sections.remove(key);return; }
        var section=chunk.getSections()[position.y()-(mc.level.getMinY()>>4)];
        if(section.hasOnlyAir() || !section.maybeHas(state->blocks.contains(state.getBlock()))) { sections.remove(key);return; }
        var found=new LongArrayList();
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
            if(blocks.contains(section.getBlockState(x,y,z).getBlock())) found.add(BlockPos.asLong(position.minBlockX()+x,position.minBlockY()+y,position.minBlockZ()+z));
        if(found.isEmpty()) sections.remove(key);else sections.put(key,found);
    }
    public static RenderPipeline pipeline() {
        if(pipeline==null) pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("coliseum","pipeline/block_esp"))
            .withVertexShader(Identifier.fromNamespaceAndPath("coliseum","core/coliseimvisual/block_overlay_mask"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("coliseum","core/block_esp"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION).withVertexBinding(0,DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty()).withCull(false).build();
        return pipeline;
    }
    public void render(Matrix4f view) {
        var mc=Minecraft.getInstance();
        if(!enabled() || mc.level==null || mc.player==null || mc.level!=level || sections.isEmpty())return;
        var camera=mc.gameRenderer.mainCamera().position();
        var matrix=new Matrix4f(view);
        var vertices=SharedVertexBuffer.shared().beginVertices(PrimitiveTopology.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn=0,r=range.intValue();
        outer:for(var section:pending) {
            var values=sections.get(section.asLong());
            if(values==null)continue;
            for(int i=0;i<values.size();i++) {
            var pos=BlockPos.of(values.getLong(i));
            if(mc.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>r*r || !mc.level.hasChunk(pos.getX()>>4,pos.getZ()>>4) || !blocks.contains(mc.level.getBlockState(pos).getBlock()))continue;
            box(vertices,matrix,(float)(pos.getX()-camera.x),(float)(pos.getY()-camera.y),(float)(pos.getZ()-camera.z),color.get());
            if(++drawn>=limit.intValue())break outer;
            }
        }
        try(var mesh=vertices.build()) {
            if(mesh==null)return;
            var compiled=RenderSystem.getCompiledPipeline(pipeline());
            var data=TransientGpuBuffers.uploadVertices(mesh.vertexBuffer(),DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
            var indices=RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            var indexBuffer=indices.getBuffer(mesh.drawState().indexCount());
            var target=mc.gameRenderer.mainRenderTarget();
            try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Coliseum BlockESP",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(compiled);RenderSystem.bindDefaultUniforms(pass);pass.setVertexBuffer(0,data);pass.setIndexBuffer(indexBuffer,indices.type());
                pass.drawIndexed(mesh.drawState().indexCount(),1,0,0,0);
            }
        }
    }
    private static void box(BufferBuilder b,Matrix4f m,float x,float y,float z,int color) {
        x-=.002f;y-=.002f;z-=.002f;float s=1.004f;
        face(b,m,color,x,y,z,x+s,y,z,x+s,y+s,z,x,y+s,z);
        face(b,m,color,x,y,z+s,x,y+s,z+s,x+s,y+s,z+s,x+s,y,z+s);
        face(b,m,color,x,y,z,x,y+s,z,x,y+s,z+s,x,y,z+s);
        face(b,m,color,x+s,y,z,x+s,y,z+s,x+s,y+s,z+s,x+s,y+s,z);
        face(b,m,color,x,y,z,x,y,z+s,x+s,y,z+s,x+s,y,z);
        face(b,m,color,x,y+s,z,x+s,y+s,z,x+s,y+s,z+s,x,y+s,z+s);
    }
    private static void face(BufferBuilder b,Matrix4f m,int color,float... xyz) {
        for(int i=0;i<4;i++) b.addVertex(m,xyz[i*3],xyz[i*3+1],xyz[i*3+2]).setUv(i==1||i==2?1:0,i>=2?1:0).setColor(color);
    }
}
