package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import morfeov.coliseum.module.impl.render.HandShader;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class HandShaderMixin {
    @WrapOperation(method = "render3dHud", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void coliseum$handShader(GameRenderer self, CameraRenderState camera, PlayerRenderState player,
                                    GpuTextureView depth, Operation<Void> original) {
        original.call(self, camera, player, depth);
        HandShader.afterHands(depth);
    }
}
