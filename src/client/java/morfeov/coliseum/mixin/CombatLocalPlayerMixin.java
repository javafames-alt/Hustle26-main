package morfeov.coliseum.mixin;

import morfeov.coliseum.port.CombatHooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class CombatLocalPlayerMixin {
    @Inject(method="sendPosition",at=@At("HEAD"))
    private void coliseum$criticalMotion(CallbackInfo ci) { var criticals = morfeov.coliseum.module.impl.combat.Criticals.getEnabled(); if (criticals != null) criticals.beforeMotion(); }
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="move", at=@At("HEAD"), argsOnly=true)
    private Vec3 coliseum$keyFinderMove(Vec3 movement) {
        var finder = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.player.AutoKeyFinder.class);
        return finder != null && finder.enabled() ? finder.overrideMovement(movement) : movement;
    }
    @Inject(method="sendPosition",at=@At("RETURN"))
    private void coliseum$postCombatMotion(CallbackInfo ci) { CombatHooks.postMotion(); }
    @Inject(method="move",at=@At("RETURN"))
    private void coliseum$postCombatMove(MoverType type,Vec3 movement,CallbackInfo ci) { CombatHooks.postMove(); }
}
