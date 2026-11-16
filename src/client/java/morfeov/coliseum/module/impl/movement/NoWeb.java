package morfeov.coliseum.module.impl.movement;

import java.util.HashSet;
import java.util.Set;
import morfeov.coliseum.mixin.MovementPredictionInvoker;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.movement.WebContact;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** The ReallyWorld, Break and Custom web modes adapted for Coliseum. */
@ModuleInfo(id = "no_web", name = "NoWeb", category = Category.MOVEMENT,
    description = "Свободное перемещение в паутине")
public final class NoWeb extends Module {
    private static NoWeb instance;
    public final ModeSetting mode = add(new ModeSetting("Мод", "ReallyWorld", "ReallyWorld", "Break", "Custom"));
    public final NumberSetting speed = add(new NumberSetting("Скорость", .21, .1, 1, .01))
        .visibleWhen(() -> mode.is("Custom"));
    public final NumberSetting verticalSpeed = add(new NumberSetting("Скорость по Y", .9, .1, 2, .01))
        .visibleWhen(() -> mode.is("Custom"));
    private LocalPlayer controlledPlayer;
    private int breakTick = Integer.MIN_VALUE;
    private final Set<BlockPos> brokenThisTick = new HashSet<>();

    public NoWeb() { instance = this; }

    @Override
    protected void onDisable() { controlledPlayer = null; brokenThisTick.clear(); breakTick = Integer.MIN_VALUE; }

    @Override
    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (controlledPlayer != player) { onDisable(); controlledPlayer = player; }
        if (player == null || client.level == null || mode.is("Break")
                || !((WebContact) player).coliseum$isInWeb()) return;
        Vec2 input = player.input.getMoveVector();
        double horizontal = mode.is("Custom") ? speed.get() : .21f;
        double vertical = mode.is("Custom") ? verticalSpeed.get() : .9f;
        player.setDeltaMovement(webVelocity(player.getDeltaMovement(), player.getYRot(), input.y, input.x,
            client.options.keyJump.isDown(), client.options.keyShift.isDown(), horizontal, vertical));
    }

    /** Keeps the donor's directional strafing and its sneak-over-jump priority. */
    public static Vec3 webVelocity(Vec3 previous, float yaw, float forward, float strafe,
                                   boolean jump, boolean sneak, double horizontal, double vertical) {
        double y = sneak ? -vertical : jump ? vertical : 0;
        if (forward == 0 && strafe == 0) return new Vec3(previous.x, y, previous.z);
        if (forward < 0) yaw += 180;
        float diagonal = forward < 0 ? -.5f : forward > 0 ? .5f : 1;
        if (strafe > 0) yaw -= 90 * diagonal;
        if (strafe < 0) yaw += 90 * diagonal;
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians) * horizontal, y, Math.cos(radians) * horizontal);
    }

    /** Return true to skip web drag after sending the original predicted STOP_DESTROY packet. */
    public static boolean onWebContact(LocalPlayer player, BlockPos position) {
        NoWeb module = instance;
        if (module == null || !module.enabled() || !module.mode.is("Break")) return false;
        Minecraft client = Minecraft.getInstance();
        if (player != client.player || client.level == null || client.gameMode == null) return false;
        if (module.controlledPlayer != player || module.breakTick != player.tickCount) {
            module.brokenThisTick.clear(); module.breakTick = player.tickCount; module.controlledPlayer = player;
        }
        if (module.brokenThisTick.add(position.immutable())) {
            ((MovementPredictionInvoker) client.gameMode).coliseum$predict(client.level,
                sequence -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    position.immutable(), Direction.UP, sequence));
        }
        return true;
    }
}
