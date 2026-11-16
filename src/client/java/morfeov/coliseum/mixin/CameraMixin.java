package morfeov.coliseum.mixin;

import morfeov.coliseum.port.SilentComponent;
import morfeov.coliseum.rotation.Look;
import morfeov.coliseum.module.impl.combat.aura.rotation.neuro.MotionTrace;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private float coliseum$motionPitch;

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float coliseum$cameraYaw(Entity entity, float partialTicks) {
        if (entity instanceof LocalPlayer) {
            SilentComponent.applyRenderInterpolation();
        }
        float yaw = entity.getViewYRot(partialTicks);
        if (entity instanceof LocalPlayer) {
            if (Look.active()) yaw = Look.yaw();
            else Look.track(yaw, entity.getViewXRot(partialTicks));
            MotionTrace.Point offset = MotionTrace.cameraOffset();
            coliseum$motionPitch = offset.pitch();
            return yaw + offset.yaw();
        }
        return yaw;
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float coliseum$cameraPitch(Entity entity, float partialTicks) {
        if (entity instanceof LocalPlayer) {
            float pitch = Look.active() ? Look.pitch() : entity.getViewXRot(partialTicks);
            return Mth.clamp(pitch + coliseum$motionPitch, -90, 90);
        }
        return entity.getViewXRot(partialTicks);
    }
}
