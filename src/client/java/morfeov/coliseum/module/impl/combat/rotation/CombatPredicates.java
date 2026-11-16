package morfeov.coliseum.module.impl.combat.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.Optional;

public class CombatPredicates {
    private CombatPredicates() {
    }

    public static double strictDistanceTo(LocalPlayer player, LivingEntity target) {
        return strictDistanceTo(player.getEyePosition(), target);
    }

    public static double strictDistanceTo(Vec3 eye, LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 closest = new Vec3(
                Math.clamp(eye.x, box.minX, box.maxX),
                Math.clamp(eye.y, box.minY, box.maxY),
                Math.clamp(eye.z, box.minZ, box.maxZ)
        );
        return closest.subtract(eye).length();
    }

    public static boolean isPlayerInCriticalState(LocalPlayer player) {
        return morfeov.coliseum.module.impl.combat.aura.CriticalUtils.isPlayerInCriticalState(player);
    }

    public static float attackCooldownThreshold(LocalPlayer player, boolean onlyCrits) {
        if (player != null && player.isFallFlying()) {
            return 0.85f;
        }
        return onlyCrits ? 0.93f : 0.90f;
    }

    public static boolean canPerformCriticalHit(LocalPlayer player) {
        return morfeov.coliseum.module.impl.combat.aura.CriticalUtils.canPerformCriticalHit(player);
    }

    public static boolean isAimingAtTarget(LocalPlayer player, LivingEntity target, float range, boolean ignoreWalls) {
        if (player == null || target == null || !target.isAlive()) return false;

        Vec3 start = player.getEyePosition();
        float margin = target.getPickRadius();
        double horizontalSpeed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        double speedBonus = Math.min(0.35, horizontalSpeed * 0.4);
        AABB box = target.getBoundingBox().inflate((margin > 0.0f ? margin : 0.1) + speedBonus);

        if (box.contains(start) || isMeleeContact(player, target) || strictDistanceTo(player, target) <= 0.5) {
            return true;
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();

        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        Vec3 lookVec = new Vec3((double) (i * j), (double) (-k), (double) (h * j));

        double castRange = Math.max(range + 0.5, 4.0);
        Vec3 end = start.add(lookVec.scale(castRange));

        Optional<Vec3> hit = box.clip(start, end);
        if (hit.isPresent()) {
            if (ignoreWalls) return true;
            HitResult wallHit = player.level().clip(new ClipContext(
                    start, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));
            return wallHit.getType() != HitResult.Type.BLOCK;
        }

        if (speedBonus > 0.05) {
            Vec3 offset = player.getDeltaMovement().scale(0.5);
            Vec3 movedStart = start.add(offset);
            Vec3 movedEnd = end.add(offset);
            Optional<Vec3> movedHit = box.clip(movedStart, movedEnd);
            if (movedHit.isPresent()) {
                if (ignoreWalls) return true;
                HitResult wallHit = player.level().clip(new ClipContext(
                        movedStart, movedHit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
                ));
                return wallHit.getType() != HitResult.Type.BLOCK;
            }
        }

        return false;
    }

    public static boolean isMeleeContact(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null || !target.isAlive()) return false;
        return player.getBoundingBox().inflate(0.12, 0.04, 0.12).intersects(target.getBoundingBox());
    }

    public static boolean isExactAimAtTarget(LocalPlayer player, LivingEntity target, float range, boolean ignoreWalls) {
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        if (box.contains(eye)) return true;
        var hit = box.clip(eye, eye.add(player.getViewVector(1).scale(range)));
        return hit.isPresent() && (ignoreWalls || player.level().clip(new ClipContext(
                eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.BLOCK);
    }
}
