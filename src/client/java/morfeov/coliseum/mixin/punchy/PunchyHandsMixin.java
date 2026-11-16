package morfeov.coliseum.mixin.punchy;

import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.punchy.PunchyBridge;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyHandsMixin {
    @Inject(method = "renderFirstPerson", at = @At("HEAD"), remap = false, require = 0)
    private static void coliseum$captureRoot(FirstPersonHandsAndItemsRenderer renderer, LocalPlayer player, float partialTicks,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
        PunchyBridge.captureRoot(poseStack);
    }

    @Inject(method = "renderFirstPerson", at = @At("RETURN"), remap = false, require = 0)
    private static void coliseum$releaseRoot(FirstPersonHandsAndItemsRenderer renderer, LocalPlayer player, float partialTicks,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
        PunchyBridge.releaseRoot();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), remap = false)
    private static void coliseum$itemTransformPush(FirstPersonHandsAndItemsRenderer renderer,
                                                  PlayerModel model, AvatarRenderState state,
                                                  LocalPlayer player, HumanoidArm arm,
                                                  PoseStack poseStack, SubmitNodeCollector collector,
                                                  int light, Matrix4f armMatrix, ModelPart itemPart,
                                                  ModelPart armPart, ModelPart sourceArm,
                                                  float frameInterp,
                                                  FirstPersonHandsAndItemsRenderState handsState,
                                                  CallbackInfo ci) {
        PunchyBridge.itemFramePush(player, arm, poseStack);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"), remap = false)
    private static void coliseum$itemTransformPop(FirstPersonHandsAndItemsRenderer renderer,
                                                 PlayerModel model, AvatarRenderState state,
                                                 LocalPlayer player, HumanoidArm arm,
                                                 PoseStack poseStack, SubmitNodeCollector collector,
                                                 int light, Matrix4f armMatrix, ModelPart itemPart,
                                                 ModelPart armPart, ModelPart sourceArm,
                                                 float frameInterp,
                                                 FirstPersonHandsAndItemsRenderState handsState,
                                                 CallbackInfo ci) {
        PunchyBridge.handFramePop(player, arm, poseStack);
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/player/PlayerModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        shift = At.Shift.AFTER), remap = false)
    private static void coliseum$itemSize(FirstPersonHandsAndItemsRenderer renderer,
                                         PlayerModel model, AvatarRenderState state,
                                         LocalPlayer player, HumanoidArm arm,
                                         PoseStack poseStack, SubmitNodeCollector collector,
                                         int light, Matrix4f armMatrix, ModelPart itemPart,
                                         ModelPart armPart, ModelPart sourceArm,
                                         float frameInterp,
                                         FirstPersonHandsAndItemsRenderState handsState,
                                         CallbackInfo ci) {
        PunchyBridge.applyItemScale(player, arm, poseStack);
    }

    @Inject(method = "renderArm", at = @At("HEAD"), remap = false, require = 0)
    private static void coliseum$armPush(PlayerModel model, AbstractClientPlayer player, HumanoidArm arm,
                                        PoseStack poseStack, SubmitNodeCollector collector, int light,
                                        Identifier skin, boolean slim, float partialTicks, CallbackInfo ci) {
        PunchyBridge.armFramePush(arm, poseStack);
    }

    @Inject(method = "renderArm", at = @At("RETURN"), remap = false, require = 0)
    private static void coliseum$armPop(PlayerModel model, AbstractClientPlayer player, HumanoidArm arm,
                                       PoseStack poseStack, SubmitNodeCollector collector, int light,
                                       Identifier skin, boolean slim, float partialTicks, CallbackInfo ci) {
        PunchyBridge.armFramePop(arm, poseStack);
    }

    @Inject(method = "renderArmArmor", at = @At("HEAD"), remap = false, require = 0)
    private static void coliseum$armorPush(AvatarRenderer renderer, AvatarRenderState state, PlayerModel model,
                                          AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack,
                                          SubmitNodeCollector collector, int light, CallbackInfo ci) {
        PunchyBridge.armFramePush(arm, poseStack);
    }

    @Inject(method = "renderArmArmor", at = @At("RETURN"), remap = false, require = 0)
    private static void coliseum$armorPop(AvatarRenderer renderer, AvatarRenderState state, PlayerModel model,
                                         AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack,
                                         SubmitNodeCollector collector, int light, CallbackInfo ci) {
        PunchyBridge.armFramePop(arm, poseStack);
    }
}
