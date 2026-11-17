package im.yaHustler.module.impl.combat;

import im.yaHustler.friend.Friends;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventAttackSubmitted;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id = "aim_assist", name = "AimAssist", category = Category.COMBAT,
    description = "Плавно доводит прицел к последней атакованной цели")
public final class AimAssist extends PortModule {
    private final NumberSetting fov = add(new NumberSetting("FOV", 180, 1, 180, 1));
    private final NumberSetting distance = add(new NumberSetting("Дистанция", 4, .1, 10, .1));
    private final NumberSetting speed = add(new NumberSetting("Скорость", 4, .1, 10, .1));
    private LivingEntity target;
    private long lastTurn;
    private double yawRemainder, pitchRemainder;

    @Override public void onAttackSubmitted(EventAttackSubmitted event) {
        if (event.target() instanceof LivingEntity living && valid(living)) {
            target = living;
            resetTurn();
        }
    }

    public void turn(Minecraft client, double yaw, double pitch) {
        var player = client.player;
        if (!valid(target) || client.level == null || client.level.getEntity(target.getId()) != target) clear();
        if (player == null || target == null || client.gui.screen() != null || !player.hasLineOfSight(target)) {
            resetTurn();
            if (player != null) player.turn(yaw, pitch);
            return;
        }
        var box = target.getBoundingBox();
        var eye = player.getEyePosition();
        double insetX = Math.min(.1, box.getXsize() * .2);
        double insetZ = Math.min(.1, box.getZsize() * .2);
        Vec3 offset = new Vec3(Mth.clamp(eye.x, box.minX + insetX, box.maxX - insetX),
            Mth.lerp(.55, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ + insetZ, box.maxZ - insetZ)).subtract(eye);
        double yawError = Mth.wrapDegrees(Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90 - player.getYRot());
        double pitchError = -Math.toDegrees(Math.atan2(offset.y, offset.horizontalDistance())) - player.getXRot();
        double error = Math.hypot(yawError, pitchError);
        if (offset.lengthSqr() > distance.get() * distance.get() || error < 1 || error > fov.get()
            || (yaw == 0 && pitch == 0 && error > 5)) {
            resetTurn();
            player.turn(yaw, pitch);
            return;
        }
        long now = System.nanoTime();
        double frames = lastTurn == 0 ? 1 : Math.clamp((now - lastTurn) * 120e-9, 0, 6);
        lastTurn = now;
        double step = speed.get() * Math.clamp(error * .1, .5, 1) * frames;
        yawRemainder += Math.clamp(yawError / .15, -step, step);
        pitchRemainder += Math.clamp(pitchError / .15, -step, step);
        long yawStep = Math.round(yawRemainder), pitchStep = Math.round(pitchRemainder);
        yawRemainder -= yawStep;
        pitchRemainder -= pitchStep;
        player.turn(yaw + yawStep, pitch + pitchStep);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand)
            && !Friends.contains(entity) && !AntiBot.rejects(entity);
    }

    private void resetTurn() {
        lastTurn = 0;
        yawRemainder = pitchRemainder = 0;
    }

    private void clear() {
        target = null;
        resetTurn();
    }

    @Override protected void onDisable() { clear(); }
    @Override public void onDisconnect(EventDisconnect event) { clear(); }
}
