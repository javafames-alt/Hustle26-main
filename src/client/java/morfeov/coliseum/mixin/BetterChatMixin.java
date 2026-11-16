package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.BetterMinecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
public abstract class BetterChatMixin {
 @Unique private long coliseum$message;
 @Unique private boolean coliseum$animated;
 @Inject(method="addMessage",at=@At("TAIL"))private void coliseum$message(CallbackInfo ci){coliseum$message=System.nanoTime();}
 @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("HEAD"))
 private void coliseum$begin(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){var m=Coliseum.MODULES.get(BetterMinecraft.class);coliseum$animated=m!=null&&m.enabled()&&m.chatAnimation.enabled();if(coliseum$animated){float t=Math.clamp((System.nanoTime()-coliseum$message)/350_000_000f,0,1);graphics.pose().pushMatrix();graphics.pose().translate((float)Math.pow(1-t,3)*-12,0);}}
 @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("RETURN"))
 private void coliseum$end(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){if(coliseum$animated)graphics.pose().popMatrix();}
}
