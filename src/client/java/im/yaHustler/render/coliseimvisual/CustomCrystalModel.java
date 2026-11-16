package im.yaHustler.render.coliseimvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Applies the crystal appearance only while producing its mask, then restores every shared model part. */
public final class CustomCrystalModel {
    private CustomCrystalModel() {}

    public static Matrix4f scaledPose(Matrix4fc submittedPose, float size) {
        // Undo the renderer's local half-block offset before scaling around the entity origin.
        return new Matrix4f(submittedPose).translate(0, .5f, 0).scale(size).translate(0, -.5f, 0);
    }

    public static void renderMask(EndCrystalModel model, EndCrystalRenderState state, PoseStack pose,
                                  VertexConsumer vertices, int light, int overlay) {
        var parts = model.allParts();
        PartPose[] previousPoses = new PartPose[parts.size()];
        boolean[] previousVisibility = new boolean[parts.size()];
        float[][] previousScales = new float[parts.size()][3];
        for (int i = 0; i < parts.size(); i++) {
            previousPoses[i] = parts.get(i).storePose();
            previousVisibility[i] = parts.get(i).visible;
            previousScales[i][0] = parts.get(i).xScale;
            previousScales[i][1] = parts.get(i).yScale;
            previousScales[i][2] = parts.get(i).zScale;
        }
        try {
            model.setupAnim(state);
            model.cube.visible = false;
            model.base.visible = false;
            model.renderToBuffer(pose, vertices, light, overlay, -1);
        } finally {
            for (int i = 0; i < parts.size(); i++) {
                parts.get(i).loadPose(previousPoses[i]);
                parts.get(i).visible = previousVisibility[i];
                // ModelPart.storePose() omits runtime scale; loadPose() resets it to one.
                parts.get(i).xScale = previousScales[i][0];
                parts.get(i).yScale = previousScales[i][1];
                parts.get(i).zScale = previousScales[i][2];
            }
        }
    }
}
