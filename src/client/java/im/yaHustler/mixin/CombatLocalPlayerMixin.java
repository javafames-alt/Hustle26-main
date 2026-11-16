package im.yaHustler.mixin;

import im.yaHustler.port.CombatHooks;
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
    private void hustle263$criticalMotion(CallbackInfo ci) { var criticals = im.yaHustler.module.impl.combat.Criticals.getEnabled(); if (criticals != null) criticals.beforeMotion(); }
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="move", at=@At("HEAD"), argsOnly=true)
    private Vec3 hustle263$keyFinderMove(Vec3 movement) {
        var finder = im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.player.AutoKeyFinder.class);
        return finder != null && finder.enabled() ? finder.overrideMovement(movement) : movement;
    }
    @Inject(method="sendPosition",at=@At("RETURN"))
    private void hustle263$postCombatMotion(CallbackInfo ci) { CombatHooks.postMotion(); }
    @Inject(method="move",at=@At("RETURN"))
    private void hustle263$postCombatMove(MoverType type,Vec3 movement,CallbackInfo ci) { CombatHooks.postMove(); }
}
