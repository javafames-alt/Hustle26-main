package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

final class SwingTransforms {
    private static final float PI = (float) Math.PI;

    private static final Matrix4f ROTATION = new Matrix4f();

    private SwingTransforms() {}

    private static void rot(PoseStack m, Axis axis, float degrees) {
        m.mulPose(ROTATION.rotation(axis.rotationDegrees(degrees)));
    }

    static void apply(PoseStack m, HumanoidArm arm, float progress, String style,
                      float strength, float corner, float slant) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float sin1 = Mth.sin(progress * progress * PI);
        float sin2 = Mth.sin(Mth.sqrt(progress) * PI);
        float anim = Mth.sin(progress * PI);
        switch (style) {
            case "Down" -> {
                m.translate(i * 0.56f, -0.32f, -0.72f);
                rot(m, Axis.YP, 76 * i);
                rot(m, Axis.YP, sin2 * -5 * strength);
                rot(m, Axis.XN, sin2 * -100 * strength);
                rot(m, Axis.XP, sin2 * -155 * strength);
                rot(m, Axis.XP, -100);
            }
            case "Poke" -> {
                m.translate(i * 0.56f, -0.52f, -0.72f);
                m.translate(0, 0, strength / 3f * -anim);
                rot(m, Axis.YP, 75 * i);
                rot(m, Axis.ZP, (-75 * (strength / 4f) * anim - 60) * i);
                rot(m, Axis.XP, -75);
            }
            case "Static" -> {
                m.translate(i * 0.56f, -0.42f, -0.72f);
                rot(m, Axis.XP, sin2 * -60 * strength);
                m.translate(0, -0.1, 0);
            }
            case "Feast" -> {
                m.translate(i * 0.56f, -0.32f, -0.72f);
                rot(m, Axis.YP, 30 * i);
                rot(m, Axis.YP, sin2 * 75 * i * strength);
                rot(m, Axis.XP, sin2 * -65 * strength);
                rot(m, Axis.YP, 30 * i);
                rot(m, Axis.XP, -80);
                rot(m, Axis.YP, 35 * i);
            }
            case "Akrien" -> {
                m.translate(i * 0.65f, -0.32f, -0.72f);
                rot(m, Axis.YP, 76 * i);
                rot(m, Axis.YP, sin2 * -5 * strength);
                rot(m, Axis.XN, sin2 * -100 * strength);
                rot(m, Axis.XP, sin2 * -155 * strength);
                rot(m, Axis.XP, -100);
                rot(m, Axis.YP, sin2 * 25 * strength);
                rot(m, Axis.XN, sin2 * -25 * strength);
                rot(m, Axis.XN, sin1 * 15 * strength);
                m.translate(sin2 * 0.18f * strength, sin2 * 0.59f * strength, 0);
            }
            case "Block" -> {
                m.translate(0.56f * i, -0.5f, -0.7f);
                rot(m, Axis.YP, 45 * i);
                rot(m, Axis.XP, sin2 * -85 * strength);
                m.translate(-0.1f * i, 0.28f, 0.2f);
                rot(m, Axis.XP, -85);
            }
            case "ToBack" -> {
                m.translate(0.65f * i, -0.45f, -0.9f);
                rot(m, Axis.XP, 50);
                rot(m, Axis.YP, (-30 * (1 - sin2 * strength) - 30) * i);
                rot(m, Axis.ZP, 110 * i);
            }
            case "SelfBack" -> {
                m.translate(0.65f * i, -0.3f, -0.8f);
                rot(m, Axis.YP, 90 * i);
                rot(m, Axis.ZP, -70 * i);
                rot(m, Axis.XP, -100 - 60 * strength * anim);
            }
            case "Break" -> {
                m.translate(0.66f * i, -0.3f, -0.38f);
                rot(m, Axis.YP, 270 * i);
                rot(m, Axis.XP, sin2 * 10 * strength);
                m.scale(0.5f, 0.5f, 0.5f);
                m.translate(-0.1f * i, 0.2f, 0);
                rot(m, Axis.YP, -10 * i);
                rot(m, Axis.XP, 90);
                rot(m, Axis.YP, -105 * i);
            }
            case "DropDown" -> {
                m.translate(i * 0.56f, -0.52f, -0.72f);
                rot(m, Axis.YP, 80);
                rot(m, Axis.XP, corner);
                rot(m, Axis.XP, -slant * anim * strength);
            }
            case "Pander" -> {
                m.translate(i * 0.56f, -0.52f, -0.72f);
                m.translate((0.3f - anim * 0.15f) * i, 0.2f, -0.15f - anim * 0.13f);
                rot(m, Axis.YP, (76 - 10 * anim) * i);
                rot(m, Axis.ZP, (-16 - 8 * anim) * i);
                rot(m, Axis.XP, -83 - 26 * anim);
            }
            case "Slant" -> {
                float rotate = 35 * strength;
                m.translate(i * 0.56f, -0.52f, -0.72f);
                m.translate(0, 0, -0.3f * anim * strength);
                rot(m, Axis.XP, anim * -rotate);
                rot(m, Axis.ZP, anim * rotate);
            }
            default -> {
                m.translate(0.56f * i, -0.52f, -0.72f);
                rot(m, Axis.YP, i * (45 + sin1 * -20 * strength));
                rot(m, Axis.ZP, i * sin2 * -20 * strength);
                rot(m, Axis.XP, sin2 * -80 * strength);
                rot(m, Axis.YP, i * -45);
            }
        }
    }
}
