package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import morfeov.coliseum.module.impl.themes.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Billboard geometry ported from NewCode's rhombus, celestial and spirit modes. */
final class NewCodeTargetSprites {
    private static final Identifier TARGET = Identifier.fromNamespaceAndPath("coliseum", "textures/targetesp/target.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("coliseum", "textures/targetesp/glow.png");
    private static final Identifier BLOOM = Identifier.fromNamespaceAndPath("coliseum", "textures/targetesp/bloom.png");

    static int color(LivingEntity target, boolean hitRed, float shade) {
        int base = Theme.accent();
        float hit = hitRed ? Math.clamp(target.hurtTime / 10f, 0, 1) : 0;
        int r = (int)Mth.lerp(hit, (base >> 16 & 255) * (1-shade), 255);
        int g = (int)Mth.lerp(hit, (base >> 8 & 255) * (1-shade), 0);
        int b = (int)Mth.lerp(hit, (base & 255) * (1-shade), 0);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    void render(Minecraft mc, PoseStack stack, SubmitNodeCollector collector, LivingEntity target,
                float fade, String mode, float size, boolean hitRed) {
        float tick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double x = Mth.lerp(tick, target.xOld, target.getX())-cam.x;
        double y = Mth.lerp(tick, target.yOld, target.getY())-cam.y;
        double z = Mth.lerp(tick, target.zOld, target.getZ())-cam.z;
        double time = (mc.player.tickCount + tick)/20.0;
        if (mode.equals("Ромб")) {
            quad(mc, stack, collector, TARGET, x, y+target.getBbHeight()/2, z, 1.5f*size/85f,
                (float)Math.toRadians((System.currentTimeMillis()%3600)/5f+1), color(target, hitRed, 0), fade);
        } else if (mode.equals("Призрак")) {
            for (int arm=0; arm<4; arm++) for (int segment=0; segment<=14; segment++) {
                double arc = segment*Math.toRadians(60)/14;
                double angle = arc+time*3+arm*Math.PI/2;
                float progress = segment/14f;
                quad(mc, stack, collector, GLOW, x+target.getBbWidth()*Math.cos(angle),
                    y+Math.sin(time+arm*15+arc+arm)*0.7+target.getBbHeight()/2+0.2,
                    z+target.getBbWidth()*Math.sin(angle), 0.4f*(0.5f+progress), 0,
                    color(target, hitRed, (float)(0.55*(0.5+0.5*Math.sin(time*1.5+segment*14*0.035)))), fade);
            }
        } else if (mode.equals("Духи")) {
            for (int spiral=0; spiral<4; spiral++) for (int i=0; i<18; i++) {
                float progress = i/17f;
                double angle = 0.15*(time*1000-i*13.86)/50+spiral*Math.PI/2;
                double radius = 0.56+0.02*i;
                float scale = (0.486f*(1-progress*progress)+0.162f*progress*progress)*fade;
                quad(mc, stack, collector, BLOOM, x+Math.cos(angle)*radius,
                    y+1+Math.sin(angle*2)*0.5, z+Math.sin(angle)*radius, scale, 0,
                    color(target, hitRed, progress*0.4f), fade*(1-progress*0.82f));
            }
        }
    }

    static void quad(Minecraft mc,PoseStack stack,SubmitNodeCollector collector,Identifier texture,
                     double x,double y,double z,float size,float rotation,int color,float alpha) {
        rectangle(mc,stack,collector,texture,x,y,z,size,size,rotation,color,alpha);
    }
    static void rectangle(Minecraft mc,PoseStack stack,SubmitNodeCollector collector,Identifier texture,
                          double x,double y,double z,float width,float height,float rotation,int color,float alpha) {
        var renderer=new EspBillboards();renderer.begin(stack,collector);
        var camera=mc.gameRenderer.mainCamera().position();
        renderer.drawBillboard(texture,x+camera.x,y+camera.y,z+camera.z,width,height,
            EspColor.withAlpha(color,(int)(Math.clamp(alpha,0,1)*255)),rotation);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x,float y,float u,float v,int color,float alpha) {
        vc.addVertex(pose,x,y,0).setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha)
            .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose,0,1,0);
    }
}
