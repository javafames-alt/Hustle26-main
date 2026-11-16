package im.yaHustler.mixin;
import im.yaHustler.module.impl.render.NoRender;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class) public abstract class NoRenderHandsMixin {
 @Inject(method="submitArmWithItem",at=@At("HEAD"),cancellable=true)
 private void hustle263$hand(PlayerRenderState p,FirstPersonHandsAndItemsRenderState state,float partial,float pitch,InteractionHand hand,float swing,ItemStack item,float equip,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci){if(NoRender.noHand(hand))ci.cancel();}
}
