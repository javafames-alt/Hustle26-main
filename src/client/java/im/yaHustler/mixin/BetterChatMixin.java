package im.yaHustler.mixin;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.BetterMinecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
public abstract class BetterChatMixin {
 @Unique private long hustle263$message;
 @Unique private boolean hustle263$animated;
 @Inject(method="addMessage",at=@At("TAIL"))private void hustle263$message(CallbackInfo ci){hustle263$message=System.nanoTime();}
 @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("HEAD"))
 private void hustle263$begin(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){var m=Hustle26.MODULES.get(BetterMinecraft.class);hustle263$animated=m!=null&&m.enabled()&&m.chatAnimation.enabled();if(hustle263$animated){float t=Math.clamp((System.nanoTime()-hustle263$message)/350_000_000f,0,1);graphics.pose().pushMatrix();graphics.pose().translate((float)Math.pow(1-t,3)*-12,0);}}
 @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("RETURN"))
 private void hustle263$end(GuiGraphicsExtractor graphics,Font font,int tick,int x,int y,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){if(hustle263$animated)graphics.pose().popMatrix();}
}
