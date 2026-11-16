package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsRendererMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void hustle263$captureItemPose(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        var module = Hustle26.MODULES.get(ItemPhysics.class);
        float tilt = Float.NaN;
        if (module != null && module.enabled()) {
            tilt = entity.onGround() ? (float)(Math.PI / 2)
                    : (float)(Math.PI / 4 * (1 + Math.sin(state.ageInTicks * .27 + state.bobOffset)));
        }
        ((ItemPhysics.RenderPose) state).hustle263$setItemTilt(tilt);
    }

    @WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void hustle263$groundOffset(PoseStack pose, float x, float y, float z, Operation<Void> original,
                                        @Local(argsOnly = true) ItemEntityRenderState state) {
        float tilt = ((ItemPhysics.RenderPose) state).hustle263$itemTilt();
        if (Float.isFinite(tilt)) {
            var bounds = state.item.getModelBoundingBox();
            double bottom = bounds.minY * Math.max(0, Math.cos(tilt)) - bounds.maxZ * Math.sin(tilt);
            y = (float)(.025 - bottom);
        }
        original.call(pose, x, y, z);
    }

    @WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
    private void hustle263$rotateItem(PoseStack pose, Axis axis, float angle, Operation<Void> original,
                                      @Local(argsOnly = true) ItemEntityRenderState state) {
        float tilt = ((ItemPhysics.RenderPose) state).hustle263$itemTilt();
        if (!Float.isFinite(tilt)) {
            original.call(pose, axis, angle);
            return;
        }
        original.call(pose, Axis.YP, state.bobOffset);
        pose.rotate(Axis.XP, tilt);
    }
}
