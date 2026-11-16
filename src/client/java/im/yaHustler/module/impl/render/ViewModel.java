package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@ModuleInfo(id = "viewmodel", name = "ViewModel", category = Category.RENDER,
    description = "Положение и размер каждой руки")
public final class ViewModel extends Module {
    private static ViewModel instance;

    private final Hand right = new Hand("Right");
    private final Hand left = new Hand("Left");

    public ViewModel() { instance = this; }

    public static boolean active() { return instance != null && instance.enabled(); }

    public static void applyArm(InteractionHand hand, PoseStack poseStack) {
        applyArm(arm(hand), poseStack);
    }

    public static void applyArm(HumanoidArm arm, PoseStack poseStack) {
        if (active() && arm != null) instance.hand(arm).applyArm(poseStack, arm == HumanoidArm.RIGHT ? 1 : -1);
    }

    public static void applyView(HumanoidArm arm, PoseStack poseStack, Matrix4f root, Matrix4f rootInverse,
                                 Matrix3f rootNormal, Matrix3f rootNormalInverse) {
        if (active() && arm != null)
            instance.hand(arm).applyView(poseStack, arm == HumanoidArm.RIGHT ? 1 : -1, root, rootInverse, rootNormal, rootNormalInverse);
    }

    private static HumanoidArm arm(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        HumanoidArm main = player.getMainArm();
        return hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
    }

    private Hand hand(HumanoidArm arm) { return arm == HumanoidArm.RIGHT ? right : left; }

    private final class Hand {
        private final NumberSetting x, y, z, width, size;

        Hand(String side) {
            x = add(new NumberSetting(side + " X", 0.0, -1.0, 1.0, 0.01));
            y = add(new NumberSetting(side + " Y", 0.0, -1.0, 1.0, 0.01));
            z = add(new NumberSetting(side + " Z", 0.0, -1.0, 1.0, 0.01));
            width = add(new NumberSetting(side + " Width", 1.0, 0.1, 3.0, 0.05));
            size = add(new NumberSetting(side + " Size", 1.0, 0.1, 3.0, 0.05));
        }

        void applyArm(PoseStack poseStack, int side) {
            translate(poseStack, side);
            if (unscaled()) return;
            float anchorX = side * 0.56f, anchorY = -0.52f, anchorZ = -0.72f;
            poseStack.translate(anchorX, anchorY, anchorZ);
            scale(poseStack);
            poseStack.translate(-anchorX, -anchorY, -anchorZ);
        }

        void applyView(PoseStack poseStack, int side, Matrix4f root, Matrix4f rootInverse,
                       Matrix3f rootNormal, Matrix3f rootNormalInverse) {
            float dx = x.floatValue() * side, dy = y.floatValue(), dz = z.floatValue();
            boolean scaled = !unscaled();
            if (dx == 0 && dy == 0 && dz == 0 && !scaled) return;
            Matrix4f view = new Matrix4f().translation(dx, dy, dz);
            if (scaled) {
                float s = size.floatValue(), anchorX = side * 0.56f, anchorY = -0.52f, anchorZ = -0.72f;
                view.translate(anchorX, anchorY, anchorZ).scale(s * width.floatValue(), s, s).translate(-anchorX, -anchorY, -anchorZ);
            }
            PoseStack.Pose last = poseStack.last();
            Matrix4f change = new Matrix4f(root).mul(view).mul(rootInverse);
            last.pose().set(change.mul(last.pose(), new Matrix4f()));
            if (scaled) {
                Matrix3f normal = new Matrix3f(view).invert().transpose();
                Matrix3f normalChange = new Matrix3f(rootNormal).mul(normal).mul(rootNormalInverse);
                last.normal().set(normalChange.mul(last.normal(), new Matrix3f()));
            }
        }

        void translate(PoseStack poseStack, int side) {
            float dx = x.floatValue() * side, dy = y.floatValue(), dz = z.floatValue();
            if (dx != 0 || dy != 0 || dz != 0) poseStack.translate(dx, dy, dz);
        }

        void scale(PoseStack poseStack) {
            if (unscaled()) return;
            float s = size.floatValue();
            poseStack.scale(s * width.floatValue(), s, s);
        }

        private boolean unscaled() {
            return Math.abs(size.floatValue() - 1) < 1.0e-4f && Math.abs(width.floatValue() - 1) < 1.0e-4f;
        }
    }
}
