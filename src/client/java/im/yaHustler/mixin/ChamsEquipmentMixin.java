package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import im.yaHustler.module.impl.render.Chams;
import im.yaHustler.render.chams.ChamsModels;


@Mixin(EquipmentLayerRenderer.class)
public class ChamsEquipmentMixin {
   @WrapOperation(
      method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
      at = @At(
         value = "INVOKE",
         ordinal = 0,
         target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
      )
   )
   private <S> void hustle263$captureArmor(
      OrderedSubmitNodeCollector collector,
      Model<? super S> model,
      S state,
      PoseStack pose,
      RenderType type,
      int light,
      int overlay,
      int color,
      UvMapping sprite,
      int outline,
      Operation<Void> original,
      @Local(name = "layerTexture") Identifier texture
   ) {
      original.call(new Object[]{collector, model, state, pose, type, light, overlay, color, sprite, outline});
      if (state instanceof AvatarRenderState avatar && Chams.capturesArmor(avatar)) {
         ChamsModels.capture(model, state, pose, texture, true);
      }
   }
}
