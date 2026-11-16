package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.combat.CrystalAura;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class CrystalAuraLevelMixin {
    @Inject(method="addEntity", at=@At("RETURN"))
    private void coliseum$crystalAdded(Entity entity, CallbackInfo ci) {
        var aura = Coliseum.MODULES.get(CrystalAura.class);
        if (aura != null && aura.enabled()) aura.entityAdded(entity);
    }
}
