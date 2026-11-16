package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import morfeov.coliseum.module.impl.movement.GrimGlide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class GrimGlideVelocityMixin {
    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 coliseum$grimGlideVelocity(Vec3 velocity) {
        if (!((Object)this instanceof LocalPlayer player)) return velocity;
        Vec3 result=GrimGlide.modifyGlidingVelocity(player,velocity);
        var motion=morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.movement.ElytraMotion.class);
        if (motion!=null && motion.stopping()) return Vec3.ZERO;
        return result;
    }
}
