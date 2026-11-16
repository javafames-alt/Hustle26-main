package im.yaHustler.mixin;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.combat.aura.AttackAura;
import im.yaHustler.module.impl.movement.NoPush;
import im.yaHustler.movement.WebContact;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MovementEntityMixin implements WebContact {
    @Shadow
    protected static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        throw new AssertionError();
    }

    @Unique private boolean hustle263$inWeb;
    @Unique private Vec3 hustle263$velocityBeforeFluid;

    @Override public boolean hustle263$isInWeb() { return hustle263$inWeb; }
    @Override public void hustle263$setInWeb(boolean value) { hustle263$inWeb = value; }

    @Inject(method = "move", at = @At("HEAD"))
    private void hustle263$resetWeb(MoverType type, Vec3 movement, CallbackInfo ci) { hustle263$inWeb = false; }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void hustle263$snowStep(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if ((Object) this != player || player == null) return;
        var weather = Hustle26.MODULES.get(im.yaHustler.module.impl.render.Weather.class);
        if (weather == null || !weather.snowStep(pos)) return;
        player.playSound(net.minecraft.sounds.SoundEvents.SNOW_STEP, .28f, 1);
        ci.cancel();
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void hustle263$entityPush(Entity other, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && NoPush.blocksEntityPush() && ((Object) this == player || other == player)) ci.cancel();
    }

    @Inject(method = "updateFluidInteraction", at = @At("HEAD"))
    private void hustle263$beforeCurrent(CallbackInfoReturnable<Boolean> cir) {
        hustle263$velocityBeforeFluid = (Object) this == Minecraft.getInstance().player && NoPush.blocksFluidPush()
            ? ((Entity) (Object) this).getDeltaMovement() : null;
    }

    @Inject(method = "updateFluidInteraction", at = @At("RETURN"))
    private void hustle263$afterCurrent(CallbackInfoReturnable<Boolean> cir) {
        if (hustle263$velocityBeforeFluid != null) {
            ((Entity) (Object) this).setDeltaMovement(hustle263$velocityBeforeFluid);
            hustle263$velocityBeforeFluid = null;
        }
    }

    @Redirect(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 hustle263$moveFix(Vec3 movementInput, float speed, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player) {
            var aura = Hustle26.MODULES.get(AttackAura.class);
            if (aura != null && aura.isMovementCorrectionActive()) {
                return getInputVector(movementInput, speed, mc.player.getYRot());
            }
        }
        return getInputVector(movementInput, speed, yaw);
    }
}
