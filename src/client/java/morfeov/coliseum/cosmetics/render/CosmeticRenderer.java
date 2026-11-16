package morfeov.coliseum.cosmetics.render;

import morfeov.coliseum.cosmetics.geckolib.GeckolibCosmeticRenderer;
import morfeov.coliseum.cosmetics.model.CosmeticModel;
import morfeov.coliseum.cosmetics.model.ModelPosition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;

public class CosmeticRenderer {
    private static CosmeticRenderer instance;
    private final GeckolibCosmeticRenderer geckolibRenderer = GeckolibCosmeticRenderer.getInstance();
    private final RenderStack stack = new RenderStack();

    public static CosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new CosmeticRenderer();
        }
        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayer player, PoseStack matrices, VertexConsumer vertexConsumer, int light, PlayerModel playerModel, float tickDelta) {
        if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
            this.stack.update(matrices);
            this.stack.push();
            float yOffset = this.transformToPosition(cosmetic, playerModel);
            this.stack.rotateZDegrees(180.0F);
            this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
            this.stack.rotateYDegrees(cosmetic.getYaw());
            this.stack.rotateXDegrees(cosmetic.getPitch());
            this.stack.rotateZDegrees(cosmetic.getRoll());
            this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
            this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
            this.stack.pop();
        }
    }

    private float transformToPosition(CosmeticModel cosmetic, PlayerModel playerModel) {
        float offset = 0.0F;
        ModelPosition position = cosmetic.getPosition();
        if (playerModel == null) {
            return offset;
        }

        switch (position) {
            case HEAD:
                this.transformToHeadModelPart(playerModel.head);
                offset = 0.5F;
                break;
            case ABOVE_HEAD:
                this.transformToHeadModelPart(playerModel.head);
                offset = 0.75F;
                break;
            case BODY:
                this.transformToModelPart(playerModel.body);
                offset = -0.3F;
                break;
            case RIGHT_ARM:
                this.transformToModelPart(playerModel.rightArm);
                offset = -0.25F;
                break;
            case LEFT_ARM:
                this.transformToModelPart(playerModel.leftArm);
                offset = -0.25F;
                break;
            case RIGHT_LEG:
                this.transformToModelPart(playerModel.rightLeg);
                offset = -0.35F;
                break;
            case LEFT_LEG:
                this.transformToModelPart(playerModel.leftLeg);
                offset = -0.35F;
                break;
            case FREE:
                break;
        }

        return offset;
    }

    private void transformToHeadModelPart(ModelPart head) {
        head.translateAndRotate(this.stack.get());
    }

    private void transformToModelPart(ModelPart part) {
        part.translateAndRotate(this.stack.get());
    }
}
