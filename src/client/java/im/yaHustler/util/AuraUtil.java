package im.yaHustler.util;

import im.yaHustler.rotation.Rotations;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class AuraUtil {
    private AuraUtil() {}

    public static double distanceSq(Vec3 eye, Entity entity) {
        AABB box = entity.getBoundingBox();
        double x = Mth.clamp(eye.x, box.minX, box.maxX) - eye.x;
        double y = Mth.clamp(eye.y, box.minY, box.maxY) - eye.y;
        double z = Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
        return x * x + y * y + z * z;
    }

    public static double distanceSq(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? Double.POSITIVE_INFINITY : distanceSq(player.getEyePosition(), entity);
    }

    public static boolean inReach(Entity entity, double maxReach) {
        return distanceSq(entity) <= maxReach * maxReach;
    }

    public static boolean lookingAt(float yaw, float pitch, double distance, Entity entity, boolean throughWalls) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        Vec3 eye = client.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(new Vec2(pitch, yaw)).scale(distance);
        Optional<Vec3> hit = entity.getBoundingBox().contains(eye)
            ? Optional.of(eye)
            : entity.getBoundingBox().clip(eye, eye.add(direction));
        if (hit.isEmpty()) return false;
        return throughWalls || client.level.clip(new ClipContext(eye, hit.get(),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player)).getType() == HitResult.Type.MISS;
    }

    private static final double[] STEPS = {0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0};

    public static boolean visible(Vec3 from, LivingEntity entity, double reach) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        AABB box = entity.getBoundingBox();
        double reachSq = reach * reach;
        if (clear(client, from, box.getCenter(), reachSq)
            || clear(client, from, new Vec3(box.getCenter().x, box.maxY, box.getCenter().z), reachSq)) return true;
        int last = STEPS.length - 1;
        for (int a = 0; a <= last; a++) {
            for (int b = 0; b <= last; b++) {
                for (int c = 0; c <= last; c++) {
                    if (a > 0 && a < last && b > 0 && b < last && c > 0 && c < last) continue;
                    Vec3 point = new Vec3(
                        Mth.lerp(STEPS[a], box.minX, box.maxX),
                        Mth.lerp(STEPS[b], box.minY, box.maxY),
                        Mth.lerp(STEPS[c], box.minZ, box.maxZ));
                    if (clear(client, from, point, reachSq)) return true;
                }
            }
        }
        return false;
    }

    private static boolean clear(Minecraft client, Vec3 from, Vec3 point, double reachSq) {
        double distSq = from.distanceToSqr(point);
        if (distSq > reachSq || distSq < 1.0e-8) return false;
        Vec3 end = point.add(from.subtract(point).scale(0.05 / Math.sqrt(distSq)));
        return client.level.clip(new ClipContext(from, end, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, client.player)).getType() == HitResult.Type.MISS;
    }

    public static boolean lookingAtBox(LivingEntity entity, double distance) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        Vec3 eye = player.getEyePosition();
        AABB box = entity.getBoundingBox();
        Vec3 d = new Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX) - eye.x,
            Mth.clamp(eye.y, box.minY, box.maxY) - eye.y,
            Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z);
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        return lookingAt(yaw, pitch, distance, entity, true);
    }

    public static boolean bonusReady(int ticks, LivingEntity target, boolean blocked) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (blocked || ticks < 7 || player == null) return false;
        return lookingAtBox(target, 3.0) && player.getAttackStrengthScale(0.5f) > 0.7f && fallingFreely();
    }

    public static boolean fallingFreely() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        LocalPlayer player = client.player;
        double dy = (player.getDeltaMovement().y - 0.08000000049877275) * 0.9799995837206814;
        if (dy >= 0.0) return false;
        AABB moved = player.getBoundingBox().move(0.0, dy, 0.0);
        AABB feet = new AABB(moved.minX, moved.minY - 0.010000001417203743, moved.minZ, moved.maxX, moved.minY, moved.maxZ);
        return !client.level.getBlockCollisions(player, feet).iterator().hasNext();
    }

    public static boolean canCrit() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return false;
        FluidState fluid = player.level().getFluidState(BlockPos.containing(player.getEyePosition()));
        return !player.hasEffect(MobEffects.LEVITATION) && !player.hasEffect(MobEffects.BLINDNESS)
            && !fluid.is(FluidTags.WATER) && !fluid.is(FluidTags.LAVA)
            && !player.getAbilities().flying && !player.isFallFlying()
            && !player.onClimbable() && !player.isPassenger();
    }

    public static boolean isCrit() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && canCrit() && player.fallDistance > 0.0f && !player.onGround();
    }

    public static float lerpAngle(float start, float end, float amount) {
        float t = Mth.clamp(amount, 0.0f, 1.0f);
        float delta = Mth.wrapDegrees(end - start);
        if (Math.abs(delta) < 0.5f) return end;
        float stepped = Mth.wrapDegrees(start + delta * t);
        float snapped = Rotations.snapSensitivity(start, stepped);
        float remaining = Mth.wrapDegrees(end - snapped);
        return Math.abs(remaining) < 0.5f ? end : snapped;
    }
}
