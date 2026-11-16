package im.yaHustler.cosmetics.extra;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import org.joml.Vector3f;

public class CosmeticBone {
    public String name;
    public String parent;
    public float[] pivot = new float[]{0.0F, 0.0F, 0.0F};
    public float[] rotation = new float[]{0.0F, 0.0F, 0.0F};
    public final List<CosmeticCube> cubes = new ArrayList<>();
    public final List<CosmeticBone> children = new ArrayList<>();

    public void render(
        Pose parentEntry,
        VertexConsumer consumer,
        int light,
        int overlay,
        float red,
        float green,
        float blue,
        float alpha,
        BedrockAnimation animation,
        float animTime
    ) {
        Pose entry = parentEntry.copy();
        entry.translate(this.pivot[0] / 16.0F, this.pivot[1] / 16.0F, this.pivot[2] / 16.0F);
        Vector3f animRot = null;
        Vector3f animPos = null;
        if (animation != null && this.name != null) {
            animRot = new Vector3f();
            animPos = new Vector3f();
            animation.apply(this.name, animTime, animRot, animPos);
        }

        if (animPos != null && (animPos.x != 0.0F || animPos.y != 0.0F || animPos.z != 0.0F)) {
            entry.translate(animPos.x / 16.0F, animPos.y / 16.0F, animPos.z / 16.0F);
        }

        float rotZ = this.rotation[2] + (animRot != null ? animRot.z : 0.0F);
        float rotY = this.rotation[1] + (animRot != null ? animRot.y : 0.0F);
        float rotX = this.rotation[0] + (animRot != null ? animRot.x : 0.0F);
        if (rotZ != 0.0F) {
            entry.rotate(Axis.ZP.rotationDegrees(rotZ));
        }

        if (rotY != 0.0F) {
            entry.rotate(Axis.YP.rotationDegrees(rotY));
        }

        if (rotX != 0.0F) {
            entry.rotate(Axis.XP.rotationDegrees(rotX));
        }

        entry.translate(-this.pivot[0] / 16.0F, -this.pivot[1] / 16.0F, -this.pivot[2] / 16.0F);

        for (CosmeticCube cube : this.cubes) {
            cube.render(entry, consumer, light, overlay, red, green, blue, alpha);
        }

        for (CosmeticBone child : this.children) {
            child.render(entry, consumer, light, overlay, red, green, blue, alpha, animation, animTime);
        }
    }



    void collectBounds(Pose parentEntry, float[] bounds) {
        Pose entry = parentEntry.copy();
        entry.translate(this.pivot[0] / 16.0F, this.pivot[1] / 16.0F, this.pivot[2] / 16.0F);
        if (this.rotation[2] != 0.0F) {
            entry.rotate(Axis.ZP.rotationDegrees(this.rotation[2]));
        }

        if (this.rotation[1] != 0.0F) {
            entry.rotate(Axis.YP.rotationDegrees(this.rotation[1]));
        }

        if (this.rotation[0] != 0.0F) {
            entry.rotate(Axis.XP.rotationDegrees(this.rotation[0]));
        }

        entry.translate(-this.pivot[0] / 16.0F, -this.pivot[1] / 16.0F, -this.pivot[2] / 16.0F);

        for (CosmeticCube cube : this.cubes) {
            cube.collectBounds(entry, bounds);
        }

        for (CosmeticBone child : this.children) {
            child.collectBounds(entry, bounds);
        }
    }
}
