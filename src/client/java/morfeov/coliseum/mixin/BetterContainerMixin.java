package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.BetterMinecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public abstract class BetterContainerMixin {
 @Unique private long coliseum$opened;
 @Unique private boolean coliseum$transformed;
 @Inject(method="init",at=@At("TAIL"))private void coliseum$open(CallbackInfo ci){coliseum$opened=System.nanoTime();}
 @Inject(method="extractRenderState",at=@At("HEAD"))private void coliseum$begin(GuiGraphicsExtractor graphics,int x,int y,float partial,CallbackInfo ci){var m=Coliseum.MODULES.get(BetterMinecraft.class);coliseum$transformed=m!=null&&m.enabled()&&m.containerAnimation.enabled();if(coliseum$transformed){float t=Math.clamp((System.nanoTime()-coliseum$opened)/220_000_000f,0,1);float ease=1-(float)Math.pow(1-t,3);graphics.pose().pushMatrix();graphics.pose().translate(0,(1-ease)*24);}}
 @Inject(method="extractRenderState",at=@At("RETURN"))private void coliseum$end(GuiGraphicsExtractor graphics,int x,int y,float partial,CallbackInfo ci){if(coliseum$transformed)graphics.pose().popMatrix();}
}
