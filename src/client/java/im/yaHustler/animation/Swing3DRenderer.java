package im.yaHustler.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import im.yaHustler.module.impl.render.SwingAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import static im.yaHustler.animation.Swing3DAnimation.*;

/** Submits the original 3D mode using 26.3's already extracted item models. */
public final class Swing3DRenderer {
    private Swing3DRenderer() {}

    @FunctionalInterface
    public interface ArmRenderer {
        void render(PoseStack pose, float equip, float swing, HumanoidArm arm);
    }

    public static boolean render(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                 float partialTicks, InteractionHand hand, float swing, ItemStack stack,
                                 float equip, PoseStack pose, SubmitNodeCollector collector, int light,
                                 ArmRenderer arms) {
        if (!SwingAnimations.is3DActive() || state.isScoping || player.avatarRenderState == null
                || player.avatarRenderState.isSpectator) return false;
        var avatar = player.avatarRenderState;
        var module = SwingAnimations.getInstance();
        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? avatar.mainArm : avatar.mainArm.getOpposite();
        if (!mainHand && (!module.leftArm.enabled() || avatar.isInvisible || stack.isEmpty())) return false;
        if (stack.isEmpty()) {
            if (avatar.isInvisible) return false;
            renderEmptyHand(arm, swing, equip, pose, arms);
            return true;
        }
        if (usesVanillaPose(stack)) return false;

        boolean using = avatar.isUsingItem && state.useItemRemainingTicks > 0 && avatar.useItemHand == hand;
        ItemUseAnimation action = stack.getUseAnimation();
        // Keep the native charge/brush/horn animations and their gameplay cues.
        if (using && mainHand && action != ItemUseAnimation.EAT && action != ItemUseAnimation.DRINK
                && action != ItemUseAnimation.BLOCK) return false;
        if (using && (action == ItemUseAnimation.EAT || action == ItemUseAnimation.DRINK)) {
            applyEatDrinkPose(pose, arm, Math.max(0, avatar.ticksUsingItem + partialTicks));
            arms.render(pose, 0, swing, arm);
        } else if (using && action == ItemUseAnimation.BLOCK) {
            applyBlockPose(pose, arm, Math.max(0, avatar.ticksUsingItem + partialTicks));
            arms.render(pose, 0, swing, arm);
        } else {
            if (mainHand) module.animation3D().applySwing(pose, arm == HumanoidArm.LEFT, swing, stack);
            applyHandBase(pose, equip, arm);
            arms.render(pose, 0, 0, arm);
        }
        applyItemAnchor(pose, arm, stack);
        applyItemPose(pose, arm, stack);
        var item = mainHand ? state.mainHandRenderState : state.offHandRenderState;
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        return true;
    }

    private static void renderEmptyHand(HumanoidArm arm, float progress, float equip,
                                         PoseStack pose, ArmRenderer arms) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        var player = Minecraft.getInstance().player;
        boolean movement = player != null && (player.isSwimming() || !player.onGround() && player.onClimbable());
        float rotation = swingRotation(progress), swing = swingCurve(progress);
        if (arm == HumanoidArm.LEFT) {
            if (movement) {
                applyHandBase(pose, equip, arm);
                pose.translate(.05, -.08, -.1);
                pose.rotateDegrees(Axis.YP, 12);
            } else {
                pose.translate(-.1, -.08 - equip * .05, -.12);
                pose.translate(-.03 * swing, .04 * rotation, -.05 * swing);
                pose.rotateDegrees(Axis.YP, 18);
                pose.rotateDegrees(Axis.ZP, -10);
                pose.rotateDegrees(Axis.XP, -12);
                pose.rotateDegrees(Axis.YP, 8 * swing);
                pose.rotateDegrees(Axis.XP, -6 * rotation);
            }
            arms.render(pose, equip * .35f, 0, arm);
            return;
        }
        if (movement) {
            applyHandBase(pose, equip, arm);
        } else {
            pose.translate(0, .2 * rotation, .15 * rotation);
            pose.translate(.1 * direction * swing, .15 * swing, -.45 * swing);
            pose.rotateDegrees(Axis.YP, 35 * swing * direction);
            pose.rotateDegrees(Axis.XP, -30 * swing);
            pose.rotateDegrees(Axis.YP, -10 * rotation * direction);
            pose.rotateDegrees(Axis.XP, 10 * rotation);
        }
        arms.render(pose, 0, 0, arm);
    }
}
