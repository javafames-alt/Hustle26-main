package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.module.impl.render.SwingAnimations;
import im.yaHustler.module.impl.render.ViewModel;
import im.yaHustler.animation.Swing3DRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsRendererMixin {
    @Unique
    private boolean hustle263$styled;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light,
                                            float equip, float swing, HumanoidArm arm, PlayerRenderState player);

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void hustle263$basePose(FirstPersonHandsAndItemsRenderer self, PoseStack poseStack, HumanoidArm arm,
                                  float equip, Operation<Void> original,
                                  @Local(argsOnly = true) InteractionHand hand,
                                  @Local(argsOnly = true, ordinal = 2) float attack) {
        if (hustle263$styled || !SwingAnimations.shouldSwing(hand)) {
            original.call(self, poseStack, arm, equip);
            return;
        }
        hustle263$styled = true;
        poseStack.translate(0.0f, -0.6f * SwingAnimations.equipOffset(equip), 0.0f);
        SwingAnimations.applySwing(poseStack, hand, attack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"),
        require = 0)
    private void hustle263$attackPose(FirstPersonHandsAndItemsRenderer self, PoseStack poseStack, HumanoidArm arm,
                                    float attack, Operation<Void> original) {
        if (!hustle263$styled) original.call(self, poseStack, arm, attack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void hustle263$swingStyle(FirstPersonHandsAndItemsRenderer self, float attack, PoseStack poseStack,
                                    int invert, HumanoidArm arm, Operation<Void> original,
                                    @Local(argsOnly = true) InteractionHand hand,
                                    @Local(argsOnly = true, ordinal = 3) float equip) {
        if (hustle263$styled) return;
        if (!SwingAnimations.shouldSwing(hand)) {
            original.call(self, attack, poseStack, invert, arm);
            return;
        }
        hustle263$styled = true;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(-side * 0.56f, 0.52f + 0.6f * equip - 0.6f * SwingAnimations.equipOffset(equip), 0.72f);
        SwingAnimations.applySwing(poseStack, hand, attack);
    }

    @WrapMethod(method = "submitArmWithItem")
    private void hustle263$renderHand(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                       float partialTicks, float pitch, InteractionHand hand, float swing,
                                       ItemStack stack, float equip, PoseStack poseStack,
                                       SubmitNodeCollector collector, int light, Operation<Void> original) {
        hustle263$styled = false;
        poseStack.pushPose();
        try {
            ViewModel.applyArm(hand, poseStack);
            if (!Swing3DRenderer.render(player, state, partialTicks, hand, swing, stack, equip, poseStack, collector, light,
                    (pose, armEquip, armSwing, arm) -> renderPlayerArm(pose, collector, light, armEquip, armSwing, arm, player))) {
                original.call(player, state, partialTicks, pitch, hand, swing, stack, equip, poseStack, collector, light);
            }
        } finally {
            hustle263$styled = false;
            poseStack.popPose();
        }
    }
}
