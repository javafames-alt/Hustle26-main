package morfeov.coliseum.mixin;
import morfeov.coliseum.module.impl.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Camera.class) public abstract class NoRenderCameraMixin {
 @Inject(method="getMaxZoom",at=@At("HEAD"),cancellable=true)
 private void coliseum$zoom(float distance,CallbackInfoReturnable<Float> cir){if(NoRender.noCameraOverlays()&&!Minecraft.getInstance().options.getCameraType().isFirstPerson())cir.setReturnValue(distance);}
}
