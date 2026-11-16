package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.CustomCrystal;
import morfeov.coliseum.render.coliseimvisual.ColiseumVisuals;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalRenderer.class)
public abstract class CustomCrystalRendererMixin {
    @WrapOperation(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
    private void coliseum$customCrystal(SubmitNodeCollector collector, Model<?> model, Object state,
                                        PoseStack pose, Identifier texture, int light, int overlay, int outline,
                                        Operation<Void> original) {
        CustomCrystal module = Coliseum.MODULES.get(CustomCrystal.class);
        if (ColiseumVisuals.worldFrame() && module != null && module.active()
                && model instanceof EndCrystalModel crystal && state instanceof EndCrystalRenderState renderState) {
            module.queue(crystal, renderState, pose, light, overlay);
        } else original.call(collector, model, state, pose, texture, light, overlay, outline);
    }
}
