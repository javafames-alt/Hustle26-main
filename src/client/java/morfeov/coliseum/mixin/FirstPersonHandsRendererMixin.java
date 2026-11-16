package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.module.impl.render.SwingAnimations;
import morfeov.coliseum.module.impl.render.ViewModel;
import morfeov.coliseum.animation.Swing3DRenderer;
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
    private boolean coliseum$styled;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light,
                                            float equip, float swing, HumanoidArm arm, PlayerRenderState player);

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void coliseum$basePose(FirstPersonHandsAndItemsRenderer self, PoseStack poseStack, HumanoidArm arm,
                                  float equip, Operation<Void> original,
                                  @Local(argsOnly = true) InteractionHand hand,
                                  @Local(argsOnly = true, ordinal = 2) float attack) {
        if (coliseum$styled || !SwingAnimations.shouldSwing(hand)) {
            original.call(self, poseStack, arm, equip);
            return;
        }
        coliseum$styled = true;
        poseStack.translate(0.0f, -0.6f * SwingAnimations.equipOffset(equip), 0.0f);
        SwingAnimations.applySwing(poseStack, hand, attack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"),
        require = 0)
    private void coliseum$attackPose(FirstPersonHandsAndItemsRenderer self, PoseStack poseStack, HumanoidArm arm,
                                    float attack, Operation<Void> original) {
        if (!coliseum$styled) original.call(self, poseStack, arm, attack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void coliseum$swingStyle(FirstPersonHandsAndItemsRenderer self, float attack, PoseStack poseStack,
                                    int invert, HumanoidArm arm, Operation<Void> original,
                                    @Local(argsOnly = true) InteractionHand hand,
                                    @Local(argsOnly = true, ordinal = 3) float equip) {
        if (coliseum$styled) return;
        if (!SwingAnimations.shouldSwing(hand)) {
            original.call(self, attack, poseStack, invert, arm);
            return;
        }
        coliseum$styled = true;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(-side * 0.56f, 0.52f + 0.6f * equip - 0.6f * SwingAnimations.equipOffset(equip), 0.72f);
        SwingAnimations.applySwing(poseStack, hand, attack);
    }

    @WrapMethod(method = "submitArmWithItem")
    private void coliseum$renderHand(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                       float partialTicks, float pitch, InteractionHand hand, float swing,
                                       ItemStack stack, float equip, PoseStack poseStack,
                                       SubmitNodeCollector collector, int light, Operation<Void> original) {
        coliseum$styled = false;
        poseStack.pushPose();
        try {
            ViewModel.applyArm(hand, poseStack);
            if (!Swing3DRenderer.render(player, state, partialTicks, hand, swing, stack, equip, poseStack, collector, light,
                    (pose, armEquip, armSwing, arm) -> renderPlayerArm(pose, collector, light, armEquip, armSwing, arm, player))) {
                original.call(player, state, partialTicks, pitch, hand, swing, stack, equip, poseStack, collector, light);
            }
        } finally {
            coliseum$styled = false;
            poseStack.popPose();
        }
    }
}
