package morfeov.coliseum.cosmetics.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

public final class ExtraCosmeticsCheck {
    public static void main(String[] args)throws Exception {
        var entries=ExtraCosmetics.entries();assertEquals(78,entries.size());
        var ids=new HashSet<Integer>();var categories=new HashMap<String,Integer>();
        int animated=0;long vertices=0;
        for(var entry:entries) {
            assertTrue(ids.add(entry.index));assertTrue(entry.index>=30000);
            categories.merge(entry.type,1,Integer::sum);
            var item=ExtraCosmetics.model(entry.index);
            assertNotNull(item);assertNotNull(item.getModel());
            for(String file:List.of(item.getTexturePath(),item.getPreviewPath()))try(var input=ExtraCosmeticsCheck.class.getResourceAsStream("/assets/coliseum/"+file)) {
                assertNotNull(input,file);var image=ImageIO.read(input);assertNotNull(image,file);assertTrue(image.getWidth()>0&&image.getHeight()>0);
            }
            if(item.getAnimationPath()!=null){assertNotNull(item.getAnimation(),item.getSlug());animated++;}
            for(float time:new float[]{0,.5f,3}) {
                var pose=new PoseStack();CosmeticPlacement.applyPlacement(pose,item,item.getCategory());
                assertTrue(pose.last().pose().isFinite(),item.getSlug());
                var capture=new Capture();
                item.getModel().render(pose,capture,15728880,0,1,1,1,1,item.getAnimation(),time);
                assertTrue(capture.count>0,item.getSlug());assertEquals(0,capture.count%4,item.getSlug());vertices+=capture.count;
            }
        }
        assertEquals(Map.of("wings",18,"hat",13,"bodywear",24,"pet",23),categories);
        assertFalse(ExtraCosmetics.contains(62));
        assertNotNull(ExtraCosmeticsCheck.class.getResourceAsStream("/morfeov/coliseum/mixin/ColiseumWorldRendererMixin.class"));
        assertNotNull(ExtraCosmeticsCheck.class.getResourceAsStream("/assets/coliseum/textures/font/coliseum_tags.png"));
        System.out.println("Extra cosmetics verified: "+entries.size()+" unique IDs, textures, previews, "+animated+" animations, placement and "+vertices+" finite vertices across 234 rendered samples.");
    }
    private static final class Capture implements VertexConsumer {
        private int count;
        private void finite(float... values) { for(float value:values)assertTrue(Float.isFinite(value)); }
        @Override public VertexConsumer addVertex(float x,float y,float z){finite(x,y,z);count++;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer setColor(int color){return this;}
        @Override public VertexConsumer setUv(float u,float v){finite(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){return this;}
        @Override public VertexConsumer setUv3(float u,float v){finite(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){finite(x,y,z);return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
}
