package morfeov.coliseum.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class SkinLayer3D extends RenderLayer<AvatarRenderState, PlayerModel> {
    public SkinLayer3D(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        SkinLayerState layers = (SkinLayerState) state;
        SkinEntry entry = layers.coliseum$skinLayers();
        int mask = layers.coliseum$skinLayerMask();
        if (entry == null || mask == 0 || state.isInvisible) return;
        SkinVoxels voxels = entry.voxels(getParentModel());
        RenderType type = RenderTypes.entityTranslucent(entry.texture());
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        for (int i = 0; i < SkinVoxels.COUNT; i++) {
            ModelPart part = part(voxels, mask, i);
            if (part == null) continue;
            poseStack.pushPose();
            for (ModelPart node : voxels.path(i)) node.translateAndRotate(poseStack);
            collector.submitModelPart(part, poseStack, type, light, overlay, null);
            poseStack.popPose();
        }
        // Chams «Обводка»: the 3D skin layers are part of the outlined silhouette.
        morfeov.coliseum.render.chams.ChamsModels.captureGeometry(state, entry.texture(), vertices -> {
            for (int i = 0; i < SkinVoxels.COUNT; i++) {
                ModelPart part = part(voxels, mask, i);
                if (part == null) continue;
                poseStack.pushPose();
                for (ModelPart node : voxels.path(i)) node.translateAndRotate(poseStack);
                part.render(poseStack, vertices, light, overlay);
                poseStack.popPose();
            }
        });
    }

    private static ModelPart part(SkinVoxels voxels, int mask, int index) {
        if ((mask & 1 << index) == 0) return null;
        ModelPart part = voxels.part(index);
        ModelPart[] path = voxels.path(index);
        return part == null || path == null || !visible(path) ? null : part;
    }

    private static boolean visible(ModelPart[] path) {
        for (int i = 0; i < path.length - 1; i++) if (!path[i].visible || path[i].skipDraw) return false;
        return true;
    }
}
