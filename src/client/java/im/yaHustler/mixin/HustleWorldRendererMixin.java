package im.yaHustler.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import im.yaHustler.render.coliseimvisual.HustleVisuals;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class HustleWorldRendererMixin {
    @Inject(method="shouldRenderBlockOutline", at=@At("HEAD"), cancellable=true)
    private void hustle263$blockOverlayOutline(CallbackInfoReturnable<Boolean> cir) {
        var overlay=im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.render.BlockOverlay.class);
        if(overlay!=null && overlay.replacesVanillaOutline()) cir.setReturnValue(false);
    }
    @Inject(method="renderLevel", at=@At("HEAD"))
    private void hustle263$beginVisuals(CallbackInfo ci) { HustleVisuals.begin(); }
    @Inject(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"))
    private void hustle263$worldMatrices(CallbackInfo ci, @Local(ordinal=0) Matrix4f projection) { HustleVisuals.matrices(projection); }
    @Inject(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    private void hustle263$drawVisuals(CallbackInfo ci) { HustleVisuals.render(); }
    @Inject(method="renderLevel", at=@At("RETURN"))
    private void hustle263$saturation(CallbackInfo ci) {
        var saturation=im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.render.Saturation.class);
        if(saturation!=null && saturation.enabled())saturation.render();
    }
}
