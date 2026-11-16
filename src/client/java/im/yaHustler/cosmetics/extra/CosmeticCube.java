package im.yaHustler.cosmetics.extra;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class CosmeticCube {
    public float[] origin = new float[3];
    public float[] size = new float[3];
    public float[] rotation = new float[]{0.0F, 0.0F, 0.0F};
    public float[] pivot = new float[]{0.0F, 0.0F, 0.0F};
    public float inflate = 0.0F;
    public boolean mirror = false;
    public final List<CosmeticQuad> quads = new ArrayList<>();

    public void render(Pose parentEntry, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        Pose entry = parentEntry.copy();
        if (this.rotation[0] != 0.0F || this.rotation[1] != 0.0F || this.rotation[2] != 0.0F) {
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
        }

        Matrix4f posMatrix = entry.pose();

        for (CosmeticQuad quad : this.quads) {
            for (CosmeticVertex v : quad.vertices) {
                consumer.addVertex(posMatrix, v.pos.x, v.pos.y, v.pos.z)
                    .setColor(red, green, blue, alpha)
                    .setUv(v.u, v.v)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(entry, quad.normal.x, quad.normal.y, quad.normal.z);
            }
        }
    }



    void collectBounds(Pose parentEntry, float[] bounds) {
        Pose entry = parentEntry.copy();
        if (this.rotation[0] != 0.0F || this.rotation[1] != 0.0F || this.rotation[2] != 0.0F) {
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
        }

        Matrix4f matrix = entry.pose();
        Vector3f transformed = new Vector3f();

        for (CosmeticQuad quad : this.quads) {
            for (CosmeticVertex vertex : quad.vertices) {
                matrix.transformPosition(vertex.pos, transformed);
                bounds[0] = Math.min(bounds[0], transformed.x);
                bounds[1] = Math.min(bounds[1], transformed.y);
                bounds[2] = Math.min(bounds[2], transformed.z);
                bounds[3] = Math.max(bounds[3], transformed.x);
                bounds[4] = Math.max(bounds[4], transformed.y);
                bounds[5] = Math.max(bounds[5], transformed.z);
            }
        }
    }
}
