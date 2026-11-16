package im.yaHustler.mixin;
import im.yaHustler.module.impl.render.NoRender;
import com.mojang.renderpearl.api.buffers.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(FogRenderer.class) public abstract class NoRenderFogMixin {
 @Shadow @Final private GpuBuffer emptyBuffer;
 @Inject(method="getBuffer",at=@At("HEAD"),cancellable=true)
 private void hustle263$fog(FogRenderer.FogMode mode,CallbackInfoReturnable<GpuBufferSlice> cir){if(NoRender.noFog())cir.setReturnValue(emptyBuffer.slice(0,FogRenderer.FOG_UBO_SIZE));}
 @Inject(method="getFogType",at=@At("HEAD"),cancellable=true)
 private void hustle263$lava(Camera camera,CallbackInfoReturnable<FogType> cir){if(NoRender.noLava()&&camera.getFluidInCamera()==FogType.LAVA)cir.setReturnValue(FogType.NONE);}
}
