package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import im.yaHustler.module.impl.render.Chams;
import im.yaHustler.render.chams.ChamsModels;

/** Chams «Обводка»: the cape is part of the outlined silhouette. */
@Mixin(CapeLayer.class)
public abstract class ChamsCapeMixin {
    @WrapOperation(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"
        ),
        require = 0
    )
    private void hustle263$outlineCape(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, RenderType type,
                                      int light, int overlay, int outlineColor, Operation<Void> original) {
        original.call(collector, model, state, pose, type, light, overlay, outlineColor);
        if (state instanceof AvatarRenderState avatar && avatar.skin.cape() != null && Chams.outlineApplies(avatar)) {
            ChamsModels.capture(model, state, pose, avatar.skin.cape().texturePath(), true);
        }
    }
}
