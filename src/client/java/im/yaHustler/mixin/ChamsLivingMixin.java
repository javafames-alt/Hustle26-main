package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;

import im.yaHustler.module.impl.render.Chams;
import im.yaHustler.render.chams.ChamsModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class ChamsLivingMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="getRenderType",at=@At("RETURN"),cancellable=true)
    private void hustle263$hiddenModel(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state,boolean visible,boolean translucent,boolean glowing,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<RenderType> cir){
        if(cir.getReturnValue()==null&&state instanceof AvatarRenderState avatar&&Chams.applies(avatar)){
            // «Обводка»: an invisible player shows only its outline - the model is captured but never drawn.
            if(Chams.instance.mode.is(Chams.OUTLINE))Chams.hiddenOutline=avatar;
            cir.setReturnValue(Chams.renderType(avatar));
        }
    }
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private void hustle263$chams(SubmitNodeCollector collector,Model model,Object state,PoseStack pose,RenderType type,int light,int overlay,int color,UvMapping sprite,int outline,Operation<Void> original){
        if(state instanceof AvatarRenderState avatar&&Chams.applies(avatar)){
            if(Chams.instance.mode.is("Flat")){type=Chams.renderType(avatar);color=Chams.tint(avatar);light=15728880;overlay=Chams.overlay();}
            else if(Chams.instance.mode.is("Блюр")&&im.yaHustler.render.chams.ChamsBlurPass.ready()){ChamsModels.capture(model,state,pose,avatar.skin.body().texturePath(),false);return;}
            else if(Chams.glowApplies(avatar)){ChamsModels.capture(model,state,pose,avatar.skin.body().texturePath(),false);type=Chams.renderType(avatar);color=Chams.tint(avatar);light=15728880;overlay=Chams.overlay();}
            else if(Chams.outlineApplies(avatar))ChamsModels.capture(model,state,pose,avatar.skin.body().texturePath(),false);
        }
        if(state!=null&&state==Chams.hiddenOutline){Chams.hiddenOutline=null;return;}
        original.call(collector,model,state,pose,type,light,overlay,color,sprite,outline);
    }
}
