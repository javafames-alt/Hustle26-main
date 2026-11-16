package morfeov.coliseum.module.impl.combat.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AuraAimPoint {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Random RANDOM = new Random();

    private AuraAimPoint() {
    }

    public static void reset() {
    }

    public static Vec3 getSnapMultiPoint(
            LivingEntity target,
            float attackRange,
            boolean ignoreWalls,
            int samples
    ) {
        LocalPlayer player = MC.player;
        if (player == null || target == null) return null;

        Vec3 eye = player.getEyePosition();
        AABB originalBox = target.getBoundingBox();
        double distanceToTarget = CombatPredicates.strictDistanceTo(player, target);

        if (distanceToTarget < 0.35 || originalBox.contains(eye)) {
            return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ());
        }

        double inset = Math.min(
                0.12,
                Math.min(originalBox.getXsize(), originalBox.getZsize()) * 0.22
        );
        AABB box = originalBox.deflate(inset);
        double bx = box.maxX - box.minX;
        double by = box.maxY - box.minY;
        double bz = box.maxZ - box.minZ;

        List<Vec3> valid = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            Vec3 point = new Vec3(
                    box.minX + RANDOM.nextDouble() * bx,
                    box.minY + RANDOM.nextDouble() * by,
                    box.minZ + RANDOM.nextDouble() * bz
            );
            double distance = point.distanceTo(eye);
            if (distance > attackRange) continue;
            if (!ignoreWalls && !hasLineOfSight(player, eye, point)) continue;
            valid.add(point);
        }

        return valid.isEmpty() ? new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ()) : valid.get(RANDOM.nextInt(valid.size()));
    }

    private static boolean hasLineOfSight(
            LocalPlayer player,
            Vec3 eye,
            Vec3 point
    ) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye,
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }
}
