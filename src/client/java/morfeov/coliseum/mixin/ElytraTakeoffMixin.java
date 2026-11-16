package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.player.ElytraHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ElytraTakeoffMixin {
    @WrapOperation(method="canGlide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean coliseum$levitation(LivingEntity player,Holder<MobEffect> effect,Operation<Boolean> original){
        if(player==Minecraft.getInstance().player&&effect==MobEffects.LEVITATION){
            var module=Coliseum.MODULES.get(ElytraHelper.class);
            if(module!=null&&module.enabled()&&module.autoTakeoff.enabled()&&module.levitation.enabled())return false;
        }
        return original.call(player,effect);
    }
}
