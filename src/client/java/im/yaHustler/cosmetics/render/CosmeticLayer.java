package im.yaHustler.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import im.yaHustler.cosmetics.*;
import im.yaHustler.cosmetics.hustle263.*;
import im.yaHustler.cosmetics.geckolib.GeckolibCosmeticRenderer;
import im.yaHustler.cosmetics.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Cosmetic geometry is captured after the native avatar's model pose is applied. */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    public CosmeticLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent) { super(parent); }
    @Override public void submit(PoseStack pose, SubmitNodeCollector queue, int light, AvatarRenderState state, float yaw, float pitch) {
        var player=Minecraft.getInstance().player;
        if(player==null || state.id!=player.getId() || state.isSpectator || state.isInvisible) return;
        var companion=im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.render.Companion.class);
        if(companion!=null)companion.submit(pose,queue,state,getParentModel());
        for(int index:LocalCosmetics.selectedIndices()) {
            String type=LocalCosmetics.type(index);
            if(type.equals("cape") || WeaponCosmetics.isWeaponType(type)) continue;
            if(im.yaHustler.cosmetics.extra.ExtraCosmetics.submit(index,pose,queue,light,state,getParentModel()))continue;
            CosmeticModel cosmetic=LocalCosmetics.modelFor(index);
            if(cosmetic==null || cosmetic.getTextureId()==null) continue;
            if(LocalCosmetics.isHustle263(index) && !type.equals("pet")) {
                HustleFeatureRenderer.submit(index,pose,queue,light,state,getParentModel());continue;
            }
            pose.pushPose();
            try {
                if(LocalCosmetics.isHustle263(index)) {
                    // Pet models keep their own root; don't attach them to the walking torso.
                    pose.translate(cosmetic.getX(), cosmetic.getY(), cosmetic.getZ());
                    switch(LocalCosmetics.hustle263Id(index)) {
                        case 45,46,90 -> pose.translate(0,3,0);
                        case 132 -> pose.translate(0,2,0);
                        case 133 -> pose.translate(-1,.5,0);
                        case 134,135 -> pose.translate(-1,0,0);
                    }
                    pose.rotate(Axis.ZP.rotationDegrees(180));
                    pose.scale(cosmetic.getScale(),cosmetic.getScale(),cosmetic.getScale());
                    HustleModelRenderer.getInstance().applyPetOrientation(cosmetic,pose);
                } else {
                    var model=getParentModel();
                    float offset=switch(cosmetic.getPosition()) {
                        case HEAD -> {model.head.translateAndRotate(pose);yield .5f;}
                        case ABOVE_HEAD -> {model.head.translateAndRotate(pose);yield .75f;}
                        case BODY -> {model.body.translateAndRotate(pose);yield -.3f;}
                        case RIGHT_ARM -> {model.rightArm.translateAndRotate(pose);yield -.25f;}
                        case LEFT_ARM -> {model.leftArm.translateAndRotate(pose);yield -.25f;}
                        case RIGHT_LEG -> {model.rightLeg.translateAndRotate(pose);yield -.35f;}
                        case LEFT_LEG -> {model.leftLeg.translateAndRotate(pose);yield -.35f;}
                        case FREE -> 0f;
                    };
                    pose.rotate(Axis.ZP.rotationDegrees(180));
                    pose.translate(cosmetic.getX(),cosmetic.getY()+offset,cosmetic.getZ());
                    pose.rotate(Axis.YP.rotationDegrees(cosmetic.getYaw()));
                    pose.rotate(Axis.XP.rotationDegrees(cosmetic.getPitch()));
                    pose.rotate(Axis.ZP.rotationDegrees(cosmetic.getRoll()));
                    pose.scale(cosmetic.getScale(),cosmetic.getScale(),cosmetic.getScale());
                }
                boolean hustle263=LocalCosmetics.isHustle263(index);
                boolean flying=state.isFallFlying, swimming=state.isVisuallySwimming, sneaking=state.isCrouching, moving=state.walkAnimationSpeed>.03;
                SubmitNodeCollector.CustomGeometryRenderer geometry=(entry,vertices)->{
                    PoseStack local=new PoseStack();local.last().pose().set(entry.pose());local.last().normal().set(entry.normal());
                    if(hustle263) {
                        var renderer=HustleModelRenderer.getInstance();renderer.setPlayerState(flying,swimming,sneaking,moving);
                        renderer.renderCosmetic(cosmetic,local,vertices,light);
                    } else GeckolibCosmeticRenderer.getInstance().renderCosmetic(cosmetic,local,vertices,light);
                };
                queue.submitCustomGeometry(pose,RenderTypes.entityCutout(cosmetic.getTextureId()),geometry);
                // Chams «Обводка»: cosmetics are part of the outlined silhouette.
                im.yaHustler.render.chams.ChamsModels.captureGeometry(state,cosmetic.getTextureId(),pose.last(),geometry);
            } finally {pose.popPose();}
        }
    }
}
