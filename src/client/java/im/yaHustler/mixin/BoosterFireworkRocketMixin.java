package im.yaHustler.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import im.yaHustler.module.impl.movement.ElytraBooster;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkRocketEntity.class)
public abstract class BoosterFireworkRocketMixin {
    @Shadow private LivingEntity attachedToEntity;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private Vec3 hustle263$fireworkBoost(Vec3 velocity, double x, double y, double z, Operation<Vec3> original) {
        Vec3 result;
        if (!ElytraBooster.appliesTo(attachedToEntity)) result=original.call(velocity,x,y,z);
        else {
            ElytraBooster booster=ElytraBooster.getInstance();
            result=ElytraBooster.accelerate(velocity,attachedToEntity.getLookAngle(),booster.factors(attachedToEntity.getYRot(),attachedToEntity.getXRot()));
        }
        return result;
    }
}
