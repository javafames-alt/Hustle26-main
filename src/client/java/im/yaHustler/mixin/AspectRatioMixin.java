package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.render.AspectRatio;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class AspectRatioMixin {
    @ModifyArgs(method="setupPerspective", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void hustle263$aspect(Args args) {
        AspectRatio module = Hustle26.MODULES.get(AspectRatio.class);
        if (module != null && module.enabled()) args.set(3, (float) args.get(4) * module.ratio());
    }
}
