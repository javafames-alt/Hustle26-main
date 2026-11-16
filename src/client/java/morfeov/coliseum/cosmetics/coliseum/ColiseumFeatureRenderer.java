package morfeov.coliseum.cosmetics.coliseum;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import morfeov.coliseum.cosmetics.LocalCosmetics;
import morfeov.coliseum.cosmetics.model.CosmeticModel;

/** Placement rules for Coliseum hats, backpacks and wings, using named Minecraft APIs. */
public final class ColiseumFeatureRenderer {
    private static boolean wasMoving = false;

    private ColiseumFeatureRenderer() {}

    public static void submit(int index, PoseStack matrices, SubmitNodeCollector queue,
                              int light, AvatarRenderState state, PlayerModel playerModel) {
        if ("pet".equals(LocalCosmetics.type(index))) return;
        CosmeticModel cosmetic = LocalCosmetics.modelFor(index);
        if (cosmetic == null || cosmetic.getTextureId() == null) return;
        matrices.pushPose();
        try {
            switch (LocalCosmetics.type(index)) {
                case "hat" -> {
                    playerModel.head.translateAndRotate(matrices);
                    matrices.scale(1.08F, 1.08F, 1.08F);
                    switch (LocalCosmetics.coliseumId(index)) {
                        case 48, 49, 50, 53, 57 -> matrices.translate(0, -0.365, 0);
                        case 80 -> matrices.translate(0, 0.06, 0);
                        case 98 -> {}
                        case 88, 97 -> matrices.translate(0, 0.0625, 0);
                        case 92 -> matrices.translate(0, 0, -0.00625);
                        case 96 -> matrices.translate(0, 0, -0.06875);
                        default -> matrices.translate(0, -0.15, 0);
                    }
                }
                case "bodywear", "wings" -> {
                    playerModel.body.translateAndRotate(matrices);
                    matrices.translate(0, "wings".equals(LocalCosmetics.type(index)) ? 0.27 : 0.25, 0);
                }
            }
            applyModelTransform(cosmetic, matrices, true);
            boolean gliding = state.isFallFlying;
            boolean swimming = state.isVisuallySwimming;
            boolean sneaking = state.isCrouching;
            boolean moving = wasMoving
                    ? Math.abs(state.walkAnimationSpeed) > 0.025F
                    : Math.abs(state.walkAnimationSpeed) > 0.08F;
            wasMoving = moving;
            SubmitNodeCollector.CustomGeometryRenderer geometry = (entry, vertices) -> {
                PoseStack local = new PoseStack();
                local.last().pose().set(entry.pose());
                local.last().normal().set(entry.normal());
                ColiseumModelRenderer renderer = ColiseumModelRenderer.getInstance();
                renderer.setPlayerState(gliding, swimming, sneaking, moving);
                renderer.renderCosmetic(cosmetic, local, vertices, light);
            };
            queue.submitCustomGeometry(matrices, RenderTypes.entityCutout(cosmetic.getTextureId()), geometry);
            // Chams «Обводка»: wings, hats and backpacks are part of the outlined silhouette.
            morfeov.coliseum.render.chams.ChamsModels.captureGeometry(state, cosmetic.getTextureId(), matrices.last(), geometry);
        } finally {
            matrices.popPose();
        }
    }

    static void applyModelTransform(CosmeticModel cosmetic, PoseStack matrices, boolean translate) {
        matrices.rotate(Axis.ZP.rotationDegrees(180));
        if (translate) matrices.translate(cosmetic.getX(), cosmetic.getY(), cosmetic.getZ());
        matrices.rotate(Axis.YP.rotationDegrees(cosmetic.getYaw()));
        matrices.rotate(Axis.XP.rotationDegrees(cosmetic.getPitch()));
        matrices.rotate(Axis.ZP.rotationDegrees(cosmetic.getRoll()));
        matrices.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
    }
}
