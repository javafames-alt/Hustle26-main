package morfeov.coliseum.module.impl.movement;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id = "grim_glide", name = "GrimGlide", category = Category.MOVEMENT,
    description = "Ускоряет полёт на элитрах без фейерверков")
public final class GrimGlide extends Module {
    /** Prevent a zero reset setting from permanently freezing the native game timer. */
    public static final float MIN_TIMER_MULTIPLIER = 0.05f;
    private static GrimGlide instance;
    public final NumberSetting timerBoost = add(new NumberSetting("Timer Boost", 1.5, 1.0, 5.0, 0.1));
    public final NumberSetting timerReset = add(new NumberSetting("Сброс таймера", 0.5, 0.0, 0.9, 0.01));
    private final GrimGlideFlightState flight = new GrimGlideFlightState();
    private LocalPlayer trackedPlayer;

    public GrimGlide() { instance = this; }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }

    @Override public void tick(Minecraft client) {
        // The module manager also ticks without a world, when LocalPlayer.tick cannot restore the timer.
        if (client.player != trackedPlayer) {
            reset();
            trackedPlayer = client.player;
        }
    }

    private void reset() {
        flight.reset();
        trackedPlayer = null;
    }

    private static GrimGlide active() {
        GrimGlide module = instance;
        return module != null && module.enabled() ? module : null;
    }

    /** Called at the same pre-super tick point as the donor's player update event. */
    public static void beforePlayerTick(LocalPlayer player) {
        GrimGlide module = active();
        if (module == null) return;
        if (module.trackedPlayer != player) {
            module.flight.reset();
            module.trackedPlayer = player;
        }
        module.flight.beforeTick(player.isFallFlying(), module.timerBoost.floatValue(), module.timerReset.floatValue());
    }

    public static Vec3 modifyGlidingVelocity(LocalPlayer player, Vec3 vanillaVelocity) {
        GrimGlide module = active();
        if (module == null) return vanillaVelocity;
        return GrimGlidePhysics.velocity(player.getDeltaMovement(), player.getLookAngle(),
            player.getXRot(), player.getYRot(), player.getGravity(), player.hasEffect(MobEffects.SLOW_FALLING),
            module.flight.recoveringFromCorrection());
    }

    public static void positionCorrected() {
        GrimGlide module = active();
        if (module != null) module.flight.positionCorrected();
    }

    public static boolean replaceMovementPacket(LocalPlayer player) {
        GrimGlide module = active();
        return module != null && module.flight.replaceMovementPacket(player.isFallFlying());
    }

    public static float timerMultiplier() {
        GrimGlide module = active();
        return module == null ? 1.0f : module.flight.timerMultiplier();
    }
}
