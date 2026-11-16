package im.yaHustler.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class ActionMouseMixin {
    @Inject(method="onButton",at=@At("HEAD"))
    private void hustle263$mouse(long window,MouseButtonInfo button,int action,CallbackInfo ci){
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().handle())
            im.yaHustler.util.ActionMouseButtons.update(button.button(),action==1);
    }
}
