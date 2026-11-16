package im.yaHustler.port;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.movement.ElytraTarget;
import im.yaHustler.module.impl.themes.Theme;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collector-based 26.3 rendering for crystal actions and the predicted ghost. */
public final class CombatVisuals implements IMinecraft {
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private static PlayerModel regular,slim;
    private static Object modelSet;
    private static Vec3 smoothed;
    private static int targetId=-1;
    public static void submit(PoseStack pose,SubmitNodeCollector collector) {
        if(mc.level==null||mc.player==null)return;
        var crystal=Hustle26.MODULES.get(im.yaHustler.module.impl.combat.CrystalAura.class);
        if(crystal!=null && crystal.enabled()) for(var visual:crystal.visibleBlocks()) {
            float fade=Math.clamp(1-(mc.player.tickCount-visual.tick())/10f,0,1);
            box(pose,collector,new AABB(visual.pos()).inflate(.002),((int)(fade*90)<<24)|(visual.color()&0xffffff));
        }
        var flight=Hustle26.MODULES.get(ElytraTarget.class);
        Vec3 predicted=flight!=null&&flight.enabled()?flight.predictedPosition():null;
        var target=flight!=null?flight.trackedTarget():null;
        if(predicted==null||target==null) { smoothed=null;targetId=-1;return; }
        smoothed=smoothed==null||targetId!=target.getId()||smoothed.distanceToSqr(predicted)>64?predicted:smoothed.lerp(predicted,.4);
        targetId=target.getId();
        int accent=Theme.accent()&0x00ffffff;
        if(flight.predictionRenderMode().equals("Моделька") && ghost(pose,collector,target,smoothed,accent))return;
        AABB bounds=flight.predictionRenderMode().equals("Квадратик")?AABB.ofSize(smoothed,.6,.6,.6)
            :target.getBoundingBox().move(smoothed.subtract(target.getBoundingBox().getCenter()));
        box(pose,collector,bounds,0x55000000|accent);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static boolean ghost(PoseStack pose,SubmitNodeCollector collector,net.minecraft.world.entity.LivingEntity target,Vec3 point,int color) {
        var renderer=mc.getEntityRenderDispatcher().getRenderer(target);
        if(!(renderer instanceof AvatarRenderer avatar))return false;
        AvatarRenderState state=avatar.createRenderState();
        avatar.extractRenderState(target,state,mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if(state.skin==null)return false;
        if(modelSet!=mc.getEntityModels()) { modelSet=mc.getEntityModels();regular=null;slim=null; }
        boolean narrow=state.skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
        PlayerModel model=narrow?slim:regular;
        if(model==null) {
            model=new PlayerModel(mc.getEntityModels().bakeLayer(narrow?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),narrow);
            if(narrow)slim=model;else regular=model;
        }
        Vec3 cam=mc.gameRenderer.mainCamera().position();
        pose.pushPose();
        pose.translate(point.x-cam.x,point.y-target.getBbHeight()*.5-cam.y,point.z-cam.z);
        pose.scale(state.scale,state.scale,state.scale);
        pose.mulPose(new org.joml.Matrix4f().rotation(Axis.YP.rotationDegrees(180-state.bodyRot)));
        if(state.fallFlyingTimeInTicks>0) {
            if(!state.isAutoSpinAttack)pose.mulPose(new org.joml.Matrix4f().rotation(Axis.XP.rotationDegrees(state.fallFlyingScale()*(-90-state.xRot))));
            if(state.shouldApplyFlyingYRot)pose.mulPose(new org.joml.Matrix4f().rotation(Axis.YP.rotation(state.flyingYRot)));
        }
        pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.501,0);
        float[] scales={1,1.05f,1.11f};int[] alpha={128,64,32};
        for(int i=0;i<scales.length;i++) {
            pose.pushPose();pose.translate(0,.5,0);pose.scale(scales[i],scales[i],scales[i]);pose.translate(0,-.5,0);
            collector.submitModel(model,state,pose,RenderTypes.entityTranslucent(state.skin.body().texturePath()),15728880,OverlayTexture.NO_OVERLAY,(alpha[i]<<24)|color);
            pose.popPose();
        }
        pose.popPose();return true;
    }
    private static void box(PoseStack pose,SubmitNodeCollector collector,AABB box,int color) {
        Vec3 cam=mc.gameRenderer.mainCamera().position();
        float[][] p=new float[8][3];
        for(int i=0;i<8;i++)p[i]=new float[]{(float)(((i&1)==0?box.minX:box.maxX)-cam.x),(float)(((i&2)==0?box.minY:box.maxY)-cam.y),(float)(((i&4)==0?box.minZ:box.maxZ)-cam.z)};
        collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,vertices)->{
            for(int[] face:FACES)for(int index:face)vertices.addVertex(matrix,p[index][0],p[index][1],p[index][2]).setColor(color);
        });
    }
}
