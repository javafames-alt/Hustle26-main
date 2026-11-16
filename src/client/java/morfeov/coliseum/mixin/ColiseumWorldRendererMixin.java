package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import morfeov.coliseum.render.coliseimvisual.ColiseumVisuals;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ColiseumWorldRendererMixin {
    @Inject(method="shouldRenderBlockOutline", at=@At("HEAD"), cancellable=true)
    private void coliseum$blockOverlayOutline(CallbackInfoReturnable<Boolean> cir) {
        var overlay=morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.BlockOverlay.class);
        if(overlay!=null && overlay.replacesVanillaOutline()) cir.setReturnValue(false);
    }
    @Inject(method="renderLevel", at=@At("HEAD"))
    private void coliseum$beginVisuals(CallbackInfo ci) { ColiseumVisuals.begin(); }
    @Inject(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"))
    private void coliseum$worldMatrices(CallbackInfo ci, @Local(ordinal=0) Matrix4f projection) { ColiseumVisuals.matrices(projection); }
    @Inject(method="renderLevel", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    private void coliseum$drawVisuals(CallbackInfo ci) { ColiseumVisuals.render(); }
    @Inject(method="renderLevel", at=@At("RETURN"))
    private void coliseum$saturation(CallbackInfo ci) {
        var saturation=morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Saturation.class);
        if(saturation!=null && saturation.enabled())saturation.render();
    }
}
