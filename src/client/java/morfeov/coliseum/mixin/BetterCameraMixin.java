package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.player.GodMode;
import morfeov.coliseum.module.impl.render.BetterMinecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Camera.class)
public abstract class BetterCameraMixin {
 @Shadow private Vec3 position;
 @Shadow private float xRot,yRot;
 @Shadow protected abstract void setRotation(float yaw,float pitch);
 @Unique private float coliseum$yaw,coliseum$pitch;
 @Unique private boolean coliseum$rotating;
 @ModifyVariable(method="getMaxZoom",at=@At("HEAD"),argsOnly=true)
 private float coliseum$motion(float distance){var m=Coliseum.MODULES.get(BetterMinecraft.class);var player=Minecraft.getInstance().player;return m!=null&&m.enabled()&&m.smooth.enabled()&&m.motion.enabled()&&player!=null?distance+(float)Math.min(1,player.getDeltaMovement().horizontalDistance()*4)*m.motionDistance.floatValue():distance;}
 @Shadow protected abstract void setPosition(Vec3 pos);
 @Unique private Vec3 coliseum$previous;
 @Unique private long coliseum$time;
 @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
 private void coliseum$zoom(float partial,CallbackInfoReturnable<Float> ci){var m=Coliseum.MODULES.get(BetterMinecraft.class);if(m!=null&&m.enabled())ci.setReturnValue(m.fov(ci.getReturnValue()));}
 @Inject(method="getMaxZoom",at=@At("HEAD"),cancellable=true)
 private void coliseum$clip(float distance,CallbackInfoReturnable<Float> ci){
     var m=Coliseum.MODULES.get(BetterMinecraft.class);
     if(m!=null&&m.enabled()&&m.clip.enabled()){ci.setReturnValue(distance);return;}
     var gm=Coliseum.MODULES.get(GodMode.class);
     if(gm!=null&&gm.enabled())ci.setReturnValue(distance);
 }
 @Inject(method="update",at=@At("TAIL"))
 private void coliseum$smooth(net.minecraft.client.DeltaTracker delta,CallbackInfo ci){var m=Coliseum.MODULES.get(BetterMinecraft.class);var mc=Minecraft.getInstance();long now=System.nanoTime();float dt=coliseum$time==0?0:Math.min(.1f,(now-coliseum$time)/1e9f);coliseum$time=now;
 if(m!=null&&m.enabled()&&m.smooth.enabled()&&!mc.options.getCameraType().isFirstPerson()&&coliseum$previous!=null&&coliseum$previous.distanceToSqr(position)<256){setPosition(coliseum$previous.lerp(position,1-Math.exp(-m.smoothSpeed.floatValue()*dt)));}
 if(m!=null&&m.enabled()&&m.smooth.enabled()&&!mc.options.getCameraType().isFirstPerson()){if(coliseum$rotating){float a=1-(float)Math.exp(-m.smoothSpeed.floatValue()*dt);setRotation(coliseum$yaw+net.minecraft.util.Mth.wrapDegrees(yRot-coliseum$yaw)*a,coliseum$pitch+(xRot-coliseum$pitch)*a);}coliseum$yaw=yRot;coliseum$pitch=xRot;coliseum$rotating=true;}else coliseum$rotating=false;
 coliseum$previous=position;
 }
}
