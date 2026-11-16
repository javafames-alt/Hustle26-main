package im.yaHustler.cosmetics.extra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.resources.Identifier;

public class BedrockModel {
    public final float textureWidth;
    public final float textureHeight;
    public float minY = 0.0F;
    public float maxY = 24.0F;
    public float minX = 0.0F;
    public float maxX = 0.0F;
    public float minZ = 0.0F;
    public float maxZ = 0.0F;
    public final List<CosmeticBone> bones = new ArrayList<>();

    public BedrockModel(float textureWidth, float textureHeight) {
        this.textureWidth = textureWidth > 0.0F ? textureWidth : 64.0F;
        this.textureHeight = textureHeight > 0.0F ? textureHeight : 64.0F;
    }

    public void render(PoseStack matrices, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        this.render(matrices, consumer, light, overlay, red, green, blue, alpha, null, 0.0F);
    }

    public void render(
        PoseStack matrices,
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
        this.render(matrices.last(), consumer, light, overlay, red, green, blue, alpha, animation, animTime);
    }

    public void render(
        Pose entry,
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
        for (CosmeticBone bone : this.bones) {
            if (bone.parent == null || bone.parent.isEmpty()) {
                bone.render(entry, consumer, light, overlay, red, green, blue, alpha, animation, animTime);
            }
        }
    }



    public void recalculateBounds() {
        float[] bounds = new float[]{
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        };
        Pose identity = new Pose();

        for (CosmeticBone bone : this.bones) {
            if (bone.parent == null || bone.parent.isEmpty()) {
                bone.collectBounds(identity, bounds);
            }
        }

        if (Float.isFinite(bounds[0])) {
            this.minX = bounds[0];
            this.minY = bounds[1];
            this.minZ = bounds[2];
            this.maxX = bounds[3];
            this.maxY = bounds[4];
            this.maxZ = bounds[5];
        }
    }
}
