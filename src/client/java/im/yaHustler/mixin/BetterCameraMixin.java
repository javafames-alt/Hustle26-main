package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.player.GodMode;
import im.yaHustler.module.impl.render.BetterMinecraft;
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
 @Unique private float hustle263$yaw,hustle263$pitch;
 @Unique private boolean hustle263$rotating;
 @ModifyVariable(method="getMaxZoom",at=@At("HEAD"),argsOnly=true)
 private float hustle263$motion(float distance){var m=Hustle26.MODULES.get(BetterMinecraft.class);var player=Minecraft.getInstance().player;return m!=null&&m.enabled()&&m.smooth.enabled()&&m.motion.enabled()&&player!=null?distance+(float)Math.min(1,player.getDeltaMovement().horizontalDistance()*4)*m.motionDistance.floatValue():distance;}
 @Shadow protected abstract void setPosition(Vec3 pos);
 @Unique private Vec3 hustle263$previous;
 @Unique private long hustle263$time;
 @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
 private void hustle263$zoom(float partial,CallbackInfoReturnable<Float> ci){var m=Hustle26.MODULES.get(BetterMinecraft.class);if(m!=null&&m.enabled())ci.setReturnValue(m.fov(ci.getReturnValue()));}
 @Inject(method="getMaxZoom",at=@At("HEAD"),cancellable=true)
 private void hustle263$clip(float distance,CallbackInfoReturnable<Float> ci){
     var m=Hustle26.MODULES.get(BetterMinecraft.class);
     if(m!=null&&m.enabled()&&m.clip.enabled()){ci.setReturnValue(distance);return;}
     var gm=Hustle26.MODULES.get(GodMode.class);
     if(gm!=null&&gm.enabled())ci.setReturnValue(distance);
 }
 @Inject(method="update",at=@At("TAIL"))
 private void hustle263$smooth(net.minecraft.client.DeltaTracker delta,CallbackInfo ci){var m=Hustle26.MODULES.get(BetterMinecraft.class);var mc=Minecraft.getInstance();long now=System.nanoTime();float dt=hustle263$time==0?0:Math.min(.1f,(now-hustle263$time)/1e9f);hustle263$time=now;
 if(m!=null&&m.enabled()&&m.smooth.enabled()&&!mc.options.getCameraType().isFirstPerson()&&hustle263$previous!=null&&hustle263$previous.distanceToSqr(position)<256){setPosition(hustle263$previous.lerp(position,1-Math.exp(-m.smoothSpeed.floatValue()*dt)));}
 if(m!=null&&m.enabled()&&m.smooth.enabled()&&!mc.options.getCameraType().isFirstPerson()){if(hustle263$rotating){float a=1-(float)Math.exp(-m.smoothSpeed.floatValue()*dt);setRotation(hustle263$yaw+net.minecraft.util.Mth.wrapDegrees(yRot-hustle263$yaw)*a,hustle263$pitch+(xRot-hustle263$pitch)*a);}hustle263$yaw=yRot;hustle263$pitch=xRot;hustle263$rotating=true;}else hustle263$rotating=false;
 hustle263$previous=position;
 }
}
