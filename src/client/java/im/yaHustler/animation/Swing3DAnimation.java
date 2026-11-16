package im.yaHustler.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

/** The 3D hand and item poses from Hustle 26.3 1.21.11, adapted to the 26.3 renderer. */
public final class Swing3DAnimation {
    private boolean slashReversed, swingInProgress;
    private float previousProgress;

    public void reset() { slashReversed = swingInProgress = false; previousProgress = 0; }

    public static boolean isSpear(ItemStack stack) {
        return stack.getUseAnimation() == ItemUseAnimation.TRIDENT
            || stack.getUseAnimation() == ItemUseAnimation.SPEAR;
    }

    public static float easeInOutBack(float x) {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        if (x < 0.5f) {
            return (float) (Math.pow(2.0f * x, 2.0) * ((c2 + 1.0f) * 2.0f * x - c2) / 2.0);
        }
        return (float) ((Math.pow(2.0f * x - 2.0f, 2.0) * ((c2 + 1.0f) * (x * 2.0f - 2.0f) + c2) + 2.0) / 2.0);
    }

    private void updateSwingState(float swingProgress) {
        boolean swinging = swingProgress > 0.01f;
        if (swinging && (!swingInProgress || swingProgress < previousProgress)) {
            slashReversed = !slashReversed;
        }
        swingInProgress = swinging;
        previousProgress = swingProgress;
    }

    public void applySwing(PoseStack m, boolean isLeft, float swingProgress, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        updateSwingState(swingProgress);

        float direction = isLeft ? -1.0f : 1.0f;
        float swingRot = swingProgress < 0.6f
                ? Mth.sin(Mth.clamp(swingProgress, 0.0f, 0.12506f) * 12.56f)
                : Mth.sin(Mth.clamp(swingProgress, 0.62532f, 0.75038f) * 12.56f);
        float swing = easeInOutBack(Mth.sin(swingProgress * (float) Math.PI));

        boolean sword = stack.is(ItemTags.SWORDS);
        boolean axe = stack.is(ItemTags.AXES);
        boolean shovel = stack.is(ItemTags.SHOVELS);
        boolean tool = stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.HOES);
        boolean spear = isSpear(stack);
        boolean block = stack.getUseAnimation() == ItemUseAnimation.BLOCK;
        boolean primarySlash = slashReversed || axe || spear || block;

        if (primarySlash && !shovel) {
            if (sword || axe) {
                m.translate(0.8f * direction * swingRot, 0.3f * swingRot, -0.5f * swing);
                m.rotateDegrees(Axis.YP, 15.0f * swingRot * direction);
                m.rotateDegrees(Axis.XN, -20.0f * swingRot);
                m.rotateDegrees(Axis.ZP, -70.0f * swingRot * direction);
                m.rotateDegrees(Axis.XN, (sword ? 40.0f : 30.0f) * swing);
                return;
            }
            if (spear) {
                m.translate(0.0f, 0.0f, 0.45f * swingRot);
                m.translate(-0.25f * direction * swing, -0.35f * swingRot, -0.6f * swing);
                m.translate(0.0f, 0.1f * swing, 0.0f);
                m.rotateDegrees(Axis.YP, 15.0f * swingRot * direction);
                m.rotateDegrees(Axis.ZP, 30.0f * swingRot * direction);
                return;
            }
            if (tool && !block) {
                m.translate(0.1f * direction * swingRot, 0.1f * swingRot, -0.5f * swing);
                m.rotateDegrees(Axis.XN, -30.0f * swingRot);
                m.rotateDegrees(Axis.ZP, -20.0f * swingRot * direction);
                m.rotateDegrees(Axis.XN, 40.0f * swing);
                return;
            }
            if (!block) {
                m.translate(0.1f * direction * swingRot, 0.1f * swingRot, -0.1f * swing);
                m.rotateDegrees(Axis.XN, -30.0f * swingRot);
                m.rotateDegrees(Axis.ZP, -10.0f * swingRot * direction);
                m.rotateDegrees(Axis.XN, 40.0f * swing);
                m.rotateDegrees(Axis.YP, 10.0f * swing * direction);
                return;
            }
            m.translate(0.1f * direction * swingRot, 0.1f * swingRot, -0.2f * swing);
            m.rotateDegrees(Axis.XN, -10.0f * swingRot);
            m.rotateDegrees(Axis.ZP, -10.0f * swingRot * direction);
            m.rotateDegrees(Axis.XN, 20.0f * swing);
            return;
        }
        if (shovel) {
            m.translate(0.0f, 0.15f * swingRot, -0.25f * swingRot);
            m.translate(0.0f, 0.0f, -0.2f * swing);
            m.rotateDegrees(Axis.YP, 15.0f * swingRot);
            m.rotateDegrees(Axis.XP, -35.0f * swingRot);
            m.rotateDegrees(Axis.XP, 30.0f * swing);
            return;
        }
        if (sword) {
            m.translate(-0.55f * direction * swingRot, -0.8f * swingRot, -0.77f * swing);
            m.rotateDegrees(Axis.YP, 5.0f * swingRot * direction);
            m.rotateDegrees(Axis.XN, -30.0f * swingRot);
            m.rotateDegrees(Axis.ZP, 70.0f * swingRot * direction);
            m.rotateDegrees(Axis.XN, 50.0f * swing);
            return;
        }
        if (tool) {
            m.translate(0.1f * direction * swingRot, 0.1f * swingRot, -0.5f * swing);
            m.rotateDegrees(Axis.XN, -30.0f * swingRot);
            m.rotateDegrees(Axis.ZP, -20.0f * swingRot * direction);
            m.rotateDegrees(Axis.XN, 40.0f * swing);
            return;
        }
        m.translate(0.1f * direction * swingRot, 0.1f * swingRot, -0.1f * swing);
        m.rotateDegrees(Axis.XN, -30.0f * swingRot);
        m.rotateDegrees(Axis.ZP, -10.0f * swingRot * direction);
        m.rotateDegrees(Axis.XN, 40.0f * swing);
        m.rotateDegrees(Axis.YP, 10.0f * swing * direction);
    }
    public static void applyHandBase(PoseStack matrices, float equipProgress, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(direction, -equipProgress * 0.3, 0.3);
        matrices.rotateDegrees(Axis.YP, 45.0f * direction);
        matrices.rotateDegrees(Axis.ZP, -40.0f * direction);
        matrices.rotateDegrees(Axis.XP, 30.0f);
        matrices.scale(0.9f, 0.9f, 0.9f);
    }

    public static void applyEatDrinkPose(PoseStack matrices, HumanoidArm arm, float useTicks) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float progress = Mth.clamp(useTicks / 5.0f, 0.0f, 1.0f);
        float wobble = Mth.sin(useTicks / 2.0f * (float) Math.PI) / 10.0f;

        matrices.translate(direction, 0.1, 0.3);
        matrices.translate(0.2 * direction * progress, -0.7 * progress, -0.2 * progress);
        matrices.translate(0.0, -0.2 * wobble, -0.2 * wobble);
        matrices.translate(0.0, 0.1 * easeInOutBack(Mth.sin(progress * (float) Math.PI)), 0.0);
        matrices.rotateDegrees(Axis.YP, 45.0f * direction);
        matrices.rotateDegrees(Axis.ZP, -40.0f * direction);
        matrices.rotateDegrees(Axis.XP, 30.0f);
        matrices.scale(0.9f, 0.9f, 0.9f);
        matrices.rotateDegrees(Axis.YP, 45.0f * progress * direction);
    }

    public static void applyBlockPose(PoseStack matrices, HumanoidArm arm, float useTicks) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float blockProgress = Mth.clamp(useTicks / 4.0f, 0.0f, 1.0f);
        float settleProgress = Mth.clamp(useTicks / 6.0f, 0.0f, 1.0f);

        matrices.translate(0.0, -0.2, 0.0);
        matrices.translate(direction, 0.0, 0.3);
        matrices.translate(0.7 * blockProgress * direction, 0.0, -1.3 * blockProgress);
        matrices.translate(-0.2 * direction * settleProgress, 0.0, 0.0);
        matrices.rotateDegrees(Axis.XP, 10.0f * Mth.sin(settleProgress * (float) Math.PI));
        matrices.rotateDegrees(Axis.YP, 70.0f * blockProgress * direction);
        matrices.rotateDegrees(Axis.YP, 45.0f * direction);
        matrices.rotateDegrees(Axis.ZP, -40.0f * direction);
        matrices.rotateDegrees(Axis.XP, 30.0f);
        matrices.rotateDegrees(Axis.YP, 5.0f * direction * blockProgress);
        matrices.rotateDegrees(Axis.XP, -10.0f * blockProgress);
        matrices.translate(0.0, 0.0, -0.2 * blockProgress);
        matrices.scale(0.9f, 0.9f, 0.9f);
    }

    public static void applyItemAnchor(PoseStack matrices, HumanoidArm arm, ItemStack stack) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (stack.is(Items.MACE)) {
            matrices.translate(-0.53 * direction, 0.66, -0.1);
        } else {
            matrices.translate(-0.3 * direction, 0.65, -0.1);
        }
        matrices.rotateDegrees(Axis.YP, -65.0f * direction);
        matrices.rotateDegrees(Axis.XP, 10.0f);
    }

    public static void applyItemPose(PoseStack matrices, HumanoidArm arm, ItemStack stack) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        if (stack.is(Items.MACE)) {

            matrices.rotateDegrees(Axis.YN, 75.0f * direction);
            matrices.rotateDegrees(Axis.XP, 70.0f);
            matrices.rotateDegrees(Axis.ZP, 45.0f * direction);
            matrices.translate(-0.16 * direction, -0.03, 0.08);
            matrices.scale(0.82f, 0.82f, 0.82f);
            return;
        }

        if (isTorch(stack)) {
            matrices.scale(1.5f, 1.5f, 1.5f);
            matrices.rotateDegrees(Axis.YN, 25.0f * direction);
            matrices.rotateDegrees(Axis.XP, 5.0f);
            matrices.rotateDegrees(Axis.ZP, 75.0f * direction);
            matrices.translate(0.2 * direction, 0.2, 0.05);
            return;
        }

        if (isSmallItem(stack)) {
            matrices.rotateDegrees(Axis.YN, 5.0f * direction);
            matrices.rotateDegrees(Axis.XP, 15.0f);
            matrices.rotateDegrees(Axis.ZP, 75.0f * direction);
            matrices.translate(0.0, -0.05, -0.1);
            matrices.scale(0.7f, 0.7f, 0.7f);
            return;
        }

        if (isThinItem(stack)) {
            matrices.translate(0.0, 0.0, -0.1);
            matrices.rotateDegrees(Axis.YN, 5.0f * direction);
            matrices.rotateDegrees(Axis.XP, 15.0f);
            matrices.rotateDegrees(Axis.ZP, 75.0f * direction);
            return;
        }

        if (stack.getItem() instanceof BlockItem) {
            matrices.rotateDegrees(Axis.YN, 25.0f * direction);
            matrices.rotateDegrees(Axis.XP, 5.0f);
            matrices.rotateDegrees(Axis.ZP, 75.0f * direction);
            matrices.translate(0.2 * direction, 0.2, 0.05);
            return;
        }

        if (stack.getUseAnimation() == ItemUseAnimation.BLOCK) {
            matrices.rotateDegrees(Axis.ZP, 160.0f * direction);
            matrices.rotateDegrees(Axis.YP, -60.0f * direction);
            matrices.rotateDegrees(Axis.XP, -70.0f);
            matrices.scale(0.75f, 0.75f, 0.75f);
            matrices.translate(0.15 * direction, arm == HumanoidArm.RIGHT ? 0.35 : 0.45, arm == HumanoidArm.RIGHT ? -0.15 : -0.1);
            matrices.translate(0.17 * direction, 0.0, 0.3);
            matrices.rotateDegrees(Axis.YP, -90.0f * direction);
            return;
        }

        if (isSpear(stack)) {
            matrices.rotateDegrees(Axis.YN, 75.0f * direction);
            matrices.rotateDegrees(Axis.XP, 90.0f);
            matrices.rotateDegrees(Axis.ZP, 45.0f * direction);
            matrices.translate(-0.3 * direction, 0.0, 0.0);
        } else {
            matrices.rotateDegrees(Axis.YN, 75.0f * direction);
            matrices.rotateDegrees(Axis.XP, 70.0f);
            matrices.rotateDegrees(Axis.ZP, 45.0f * direction);
        }

        matrices.scale(1.2f, 1.2f, 1.2f);

        if (stack.getUseAnimation() == ItemUseAnimation.BOW) {
            matrices.translate(-0.1 * direction, -0.2, 0.0);
        }
    }

    public static boolean usesVanillaPose(ItemStack stack) {
        return stack.getItem() instanceof MapItem
                || stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isWeaponLike(ItemStack stack) {
        ItemUseAnimation useAction = stack.getUseAnimation();
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.SHOVELS)
                || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof FishingRodItem
                || stack.is(Items.MACE)
                || useAction == ItemUseAnimation.BOW
                || useAction == ItemUseAnimation.BLOCK
                || isSpear(stack)
                || stack.is(Items.SHEARS)
                || stack.is(Items.CARROT_ON_A_STICK)
                || stack.is(Items.WARPED_FUNGUS_ON_A_STICK);
    }

    public static boolean isTorch(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.REDSTONE_TORCH);
    }

    public static boolean isThinItem(ItemStack stack) {
        Block block = Block.byItem(stack.getItem());
        return stack.is(Items.STRING)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.LEVER)
                || stack.is(Items.TRIPWIRE_HOOK)
                || block.defaultBlockState().is(BlockTags.RAILS)
                || block.defaultBlockState().is(BlockTags.CLIMBABLE)
                || block.defaultBlockState().is(BlockTags.DOORS)
                || block.defaultBlockState().is(BlockTags.BUTTONS)
                || block.defaultBlockState().is(BlockTags.PRESSURE_PLATES);
    }

    public static boolean isSmallItem(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.getItem() instanceof BucketItem
                || (!isWeaponLike(stack) && !(stack.getItem() instanceof BlockItem));
    }

    public static float swingRotation(float swingProgress) {
        return swingProgress < 0.6f
                ? Mth.sin(Mth.clamp(swingProgress, 0.0f, 0.12506f) * 12.56f)
                : Mth.sin(Mth.clamp(swingProgress, 0.62532f, 0.75038f) * 12.56f);
    }

    public static float swingCurve(float swingProgress) {
        return easeInOutBack(Mth.sin(swingProgress * (float) Math.PI));
    }

}
