package im.yaHustler.wyvern.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.wyvern.event.EventBus;
import im.yaHustler.wyvern.event.RenderHandEvent;
import im.yaHustler.wyvern.module.impl.render.SwingAnimation;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void wyvern$swing(float animation, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (SwingAnimation.apply(poseStack, animation, invert)) ci.cancel();
	}

	@Inject(method = "applyItemArmTransform", at = @At("RETURN"))
	private void wyvern$viewModelItem(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
		EventBus.INSTANCE.post(new RenderHandEvent(poseStack, arm));
		SwingAnimation.applyRest(poseStack, arm);
	}

	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void wyvern$viewModelArm(PoseStack poseStack, SubmitNodeCollector collector, int light, float equipProgress,
			float swingProgress, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
		EventBus.INSTANCE.post(new RenderHandEvent(poseStack, arm));
	}
}
