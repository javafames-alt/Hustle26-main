package im.yaHustler.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Fit the visible artwork rather than the transparent padding in donor texture sheets. */
public final class CosmeticArtwork {
    record Region(int u,int v,int w,int h,int textureWidth,int textureHeight) {}
    private static final Map<Identifier,Region> REGIONS = new ConcurrentHashMap<>();
    private CosmeticArtwork() {}
    static Region region(Identifier texture) {
        return REGIONS.computeIfAbsent(texture,id->{
            try(var in=CosmeticArtwork.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath())) {
                if(in!=null) {
                    var img=ImageIO.read(in);
                    if(img!=null) {
                        int left=img.getWidth(),top=img.getHeight(),right=-1,bottom=-1;
                        for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++)if((img.getRGB(x,y)>>>24)>=8) {
                            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
                        }
                        return right<left ? new Region(0,0,img.getWidth(),img.getHeight(),img.getWidth(),img.getHeight())
                            : new Region(left,top,right-left+1,bottom-top+1,img.getWidth(),img.getHeight());
                    }
                }
            } catch(java.io.IOException ignored) {}
            return new Region(0,0,64,64,64,64);
        });
    }
    public static void draw(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, int color) {
        Region region=region(texture);
        float scale=Math.min(w/region.w,h/region.h);
        g.pose().pushMatrix();
        try {
            g.pose().translate(x+(w-region.w*scale)/2,y+(h-region.h*scale)/2);
            g.pose().scale(scale,scale);
            im.yaHustler.ui.GuiTextures.blit(g, RenderPipelines.GUI_TEXTURED,texture,0,0,region.u,region.v,region.w,region.h,
                region.w,region.h,region.textureWidth,region.textureHeight,GuiOpacity.color(color));
        } finally {g.pose().popMatrix();}
    }
}
