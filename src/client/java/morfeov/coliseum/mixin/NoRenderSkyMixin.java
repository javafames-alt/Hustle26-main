package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class) public abstract class NoRenderSkyMixin {
 @Inject(method="lambda$addSkyPass$0",at=@At("HEAD"),cancellable=true)
 private static void coliseum$sky(CallbackInfo ci){if(NoRender.noSkyBackground())ci.cancel();}
}
