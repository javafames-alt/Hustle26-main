package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.AspectRatio;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class AspectRatioMixin {
    @ModifyArgs(method="setupPerspective", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void coliseum$aspect(Args args) {
        AspectRatio module = Coliseum.MODULES.get(AspectRatio.class);
        if (module != null && module.enabled()) args.set(3, (float) args.get(4) * module.ratio());
    }
}
