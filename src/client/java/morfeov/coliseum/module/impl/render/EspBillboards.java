package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.render.coliseimvisual.EspEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class EspBillboards {
    boolean glow=true;
    private Vec3 camera;
    private final Vector3f right=new Vector3f(),up=new Vector3f();
    void begin(PoseStack pose,SubmitNodeCollector collector){
        var cam=Minecraft.getInstance().gameRenderer.mainCamera();camera=cam.position();
        right.set(1,0,0).rotate(cam.rotation());up.set(0,1,0).rotate(cam.rotation());
    }
    void drawBillboard(Identifier texture,double x,double y,double z,float width,float height,int color,float angle){
        if(!glow&&texture.getPath().endsWith("bloom.png"))return;
        float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
        Vector3f r=new Vector3f(right).mul(cos).fma(sin,up).mul(width*.5f);
        Vector3f u=new Vector3f(up).mul(cos).fma(-sin,right).mul(height*.5f);
        float cx=(float)(x-camera.x),cy=(float)(y-camera.y),cz=(float)(z-camera.z);
        float[] xyz={cx-r.x+u.x,cy-r.y+u.y,cz-r.z+u.z,
                     cx-r.x-u.x,cy-r.y-u.y,cz-r.z-u.z,
                     cx+r.x-u.x,cy+r.y-u.y,cz+r.z-u.z,
                     cx+r.x+u.x,cy+r.y+u.y,cz+r.z+u.z};
        EspEffects.quad(texture,xyz,new float[]{0,0,0,1,1,1,1,0},new int[]{color,color,color,color});
    }
    void drawWorldQuad(Identifier texture,double x0,double y0,double z0,double x1,double y1,double z1,double x2,double y2,double z2,double x3,double y3,double z3,int c0,int c1,int c2,int c3,float u0,float v0,float u1,float v1,float u2,float v2,float u3,float v3){
        worldQuad(texture,new double[]{x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3},new float[]{u0,v0,u1,v1,u2,v2,u3,v3},new int[]{c0,c1,c2,c3});
    }
    void worldQuad(Identifier texture,double[] xyz,float[] uv,int[] colors){
        float[] positions=new float[12];for(int i=0;i<4;i++){positions[i*3]=(float)(xyz[i*3]-camera.x);positions[i*3+1]=(float)(xyz[i*3+1]-camera.y);positions[i*3+2]=(float)(xyz[i*3+2]-camera.z);}
        EspEffects.quad(texture,positions,uv,colors);
    }
}
