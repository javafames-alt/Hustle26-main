package im.yaHustler.module.impl.combat.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

public final class CriticalUtils {
    private static final List<VoxelShape> EMPTY_SHAPES = Collections.emptyList();

    private static Vec3 cachedLandingPos;
    private static int landingPosCachedAge = -1;
    private static double cachedMaxFallDistance;
    private static int maxFallDistanceCachedAge = -1;
    private static int maceDelayTicks;
    private static int maceCooldownTimer;

    private CriticalUtils() {}
    public static boolean isInCobweb(LocalPlayer player) {
        if (player == null) return false;
        if ((Object) player instanceof im.yaHustler.movement.WebContact contact && contact.hustle263$isInWeb()) return true;
        var box = player.getBoundingBox().deflate(1e-5);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if (player.level().getBlockState(pos).is(Blocks.COBWEB)) return true;
        return false;
    }

    private static Vec3 entityPos(Entity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    public static void reset() {
        maceDelayTicks = 0;
        maceCooldownTimer = 0;
        cachedLandingPos = null;
        landingPosCachedAge = -1;
        cachedMaxFallDistance = 0.0;
        maxFallDistanceCachedAge = -1;
    }

    public static void tickMaceCooldown() {
        if (maceCooldownTimer > 0) {
            maceCooldownTimer--;
        }
    }

    public static void onMaceAttack(LocalPlayer player) {
        if (player != null && player.getMainHandItem().is(Items.MACE)) {
            maceCooldownTimer = 20;
        }
    }

    public static boolean isMaceOnCooldown() {
        return maceCooldownTimer > 0;
    }

    public static boolean isMaceActive(LocalPlayer player) {
        if (player == null) return false;
        if (player.getMainHandItem().is(Items.MACE)) return true;
        if (player.fallDistance < 1.0) return false;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.MACE)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canPerformCriticalHit(LocalPlayer player) {
        if (player == null) return false;
        return !player.onClimbable()
                && !player.isInWater()
                && !player.isUnderWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.hasEffect(MobEffects.SLOW_FALLING)
                && !player.isFallFlying()
                && !player.isPassenger()
                && !player.getAbilities().flying
                && !player.hasEffect(MobEffects.LEVITATION)
                && !player.isInLava();
    }

    public static boolean isPlayerInCriticalState(LocalPlayer player) {
        if (player == null) return false;
        if (player.isFallFlying()) {
            return player.getDeltaMovement().y < -0.01 || player.getXRot() > 1.0f || player.fallDistance > 0.0;
        }
        return !player.onGround()
                && player.fallDistance > 0.0f
                && player.getDeltaMovement().y < -0.0784D
                && canPerformCriticalHit(player);
    }

    public static Vec3 getPredictedLandingPos(LocalPlayer player) {
        if (player == null || player.level() == null || player.onGround()) return null;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.onClimbable()) return null;
        if (player.hasEffect(MobEffects.LEVITATION) || player.isInWater() || player.isInLava()) return null;

        if (player.tickCount == landingPosCachedAge) {
            return cachedLandingPos;
        }
        landingPosCachedAge = player.tickCount;
        cachedLandingPos = calculateLandingPos(player);
        return cachedLandingPos;
    }

    private static Vec3 calculateLandingPos(LocalPlayer player) {
        double gravity = player.getGravity();
        double offsetX = 0.0;
        double offsetY = 0.0;
        double offsetZ = 0.0;

        AABB box = player.getBoundingBox();
        Vec3 vel = player.getDeltaMovement();

        for (int i = 0; i < 400; i++) {
            double nextVx = vel.x * 0.9799995613681012;
            double nextVy = (vel.y - gravity) * 0.9799995613681012;
            double nextVz = vel.z * 0.9799995613681012;

            Vec3 targetMove = new Vec3(nextVx, nextVy, nextVz);
            Vec3 adjustedMove = Entity.collideBoundingBox(
                    player,
                    targetMove,
                    box.move(offsetX, offsetY, offsetZ),
                    player.level(),
                    EMPTY_SHAPES
            );

            if (targetMove.y < -9.999995680744685E-5 && adjustedMove.y > targetMove.y + 9.999995999686725E-5) {
                return entityPos(player).add(offsetX + adjustedMove.x, offsetY + adjustedMove.y, offsetZ + adjustedMove.z);
            }

            offsetX += adjustedMove.x;
            offsetY += adjustedMove.y;
            offsetZ += adjustedMove.z;

            if (containsFluid(player, box.move(offsetX, offsetY, offsetZ))) {
                return entityPos(player).add(offsetX, offsetY, offsetZ);
            }

            if (Math.abs(adjustedMove.x - targetMove.x) > 9.999995999686725E-5) {
                nextVx = 0.0;
            }
            if (Math.abs(adjustedMove.y - targetMove.y) > 9.999995999686725E-5) {
                nextVy = 0.0;
            }
            if (Math.abs(adjustedMove.z - targetMove.z) > 9.999995999686725E-5) {
                nextVz = 0.0;
            }

            vel = new Vec3(nextVx, nextVy, nextVz);
            if (vel.lengthSqr() < 1.0E-12 && targetMove.y >= -9.999995680744685E-5) {
                break;
            }
        }
        return null;
    }

    public static double getFallHeightToLanding(LocalPlayer player) {
        Vec3 landingPos = getPredictedLandingPos(player);
        return (landingPos != null && player != null) ? player.getY() - landingPos.y : -1.0;
    }

    public static boolean willLandWithinTicks(LocalPlayer player, int ticks) {
        if (player == null || player.level() == null || player.onGround()) return false;
        double gravity = player.getGravity();
        double offsetY = 0.0;
        Vec3 vel = player.getDeltaMovement();

        for (int i = 0; i < ticks; i++) {
            double nextVy = (vel.y - gravity) * 0.9799995613681012;
            Vec3 adjusted = Entity.collideBoundingBox(
                    player,
                    new Vec3(0.0, nextVy, 0.0),
                    player.getBoundingBox().move(0.0, offsetY, 0.0),
                    player.level(),
                    EMPTY_SHAPES
            );
            if (nextVy < 0.0 && (i == 0 ? adjusted.y != nextVy : adjusted.y > nextVy * 0.5)) {
                return true;
            }
            offsetY += adjusted.y;
            vel = new Vec3(vel.x, adjusted.y, vel.z);
        }
        return false;
    }

    public static boolean willEnterObstacleWithinTicks(LocalPlayer player, int ticks) {
        if (player == null || player.level() == null) return false;
        double gravity = player.getGravity();
        double offsetY = 0.0;
        Vec3 vel = player.getDeltaMovement();

        for (int i = 0; i < ticks; i++) {
            double nextVy = (vel.y - gravity) * 0.9799995613681012;
            AABB box = player.getBoundingBox().move(0.0, offsetY, 0.0);
            Vec3 adjusted = Entity.collideBoundingBox(
                    player,
                    new Vec3(0.0, nextVy, 0.0),
                    box,
                    player.level(),
                    EMPTY_SHAPES
            );
            if (nextVy < 0.0 && containsObstacleOrFluid(player, box.move(adjusted.x, adjusted.y, adjusted.z))) {
                return true;
            }
            offsetY += adjusted.y;
            vel = new Vec3(vel.x, adjusted.y, vel.z);
        }
        return false;
    }

    public static boolean canCritTarget(LocalPlayer player, LivingEntity target, boolean checkLanding) {
        if (checkLanding && !willLandWithinTicks(player, 2) && !willEnterObstacleWithinTicks(player, 2)) {
            Vec3 landing = getPredictedLandingPos(player);
            return landing == null || target == null || landing.distanceTo(entityPos(target)) > 2.0;
        }
        return true;
    }

    public static double getMaxPredictedFallDistance(LocalPlayer player) {
        if (player == null) return 0.0;
        if (player.tickCount == maxFallDistanceCachedAge) {
            return cachedMaxFallDistance;
        }
        maxFallDistanceCachedAge = player.tickCount;
        cachedMaxFallDistance = simulateMaxFallDistance(player);
        return cachedMaxFallDistance;
    }

    private static double simulateMaxFallDistance(LocalPlayer player) {
        if (player == null || player.level() == null || player.onGround()) return 0.0;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.onClimbable()) return 0.0;
        if (player.hasEffect(MobEffects.LEVITATION) || player.isInWater() || player.isInLava()) return 0.0;

        double gravity = player.getGravity();
        double simulatedFall = player.fallDistance;
        double maxFall = simulatedFall;

        double offsetY = 0.0;
        Vec3 vel = player.getDeltaMovement();
        AABB box = player.getBoundingBox();

        for (int i = 0; i < 60; i++) {
            double nextVy = (vel.y - gravity) * 0.9799995613681012;
            Vec3 adjusted = Entity.collideBoundingBox(
                    player,
                    new Vec3(0.0, nextVy, 0.0),
                    box.move(0.0, offsetY, 0.0),
                    player.level(),
                    EMPTY_SHAPES
            );

            if (nextVy < 0.0) {
                simulatedFall += Math.abs(nextVy);
                maxFall = Math.max(maxFall, simulatedFall);
            }
            if (nextVy < -1e-4 && adjusted.y > nextVy + 1e-4) {
                break;
            }
            offsetY += adjusted.y;
            if (containsObstacleOrFluid(player, box.move(0.0, offsetY, 0.0))) {
                break;
            }
            vel = new Vec3(vel.x, adjusted.y, vel.z);
        }
        return maxFall;
    }

    public static float getPredictedFallDistance(LocalPlayer player, int ticks) {
        if (player == null) return 0.0f;
        double gravity = player.getGravity();
        double simulatedFall = player.fallDistance;
        Vec3 vel = player.getDeltaMovement();

        for (int i = 0; i < ticks; i++) {
            double nextVy = (vel.y - gravity) * 0.9799995613681012;
            if (nextVy < 0.0) {
                simulatedFall += Math.abs(nextVy);
            }
            vel = new Vec3(vel.x, nextVy, vel.z);
        }
        return (float) simulatedFall;
    }

    public static boolean isLandingNextTickWithMace(LocalPlayer player) {
        if (player == null || player.fallDistance <= 1.0) return false;
        return willLandWithinTicks(player, 1);
    }

    public static boolean shouldDelayMaceAttack(LocalPlayer player, LivingEntity target) {
        if (isInCobweb(player)) return false;
        if (!isMaceActive(player) || target == null) return false;
        if (isMaceOnCooldown()) return true;

        if (player.fallDistance < 1.5 && player.getDeltaMovement().y > 0.0) {
            return true;
        }

        double maxFallDistance = getMaxPredictedFallDistance(player);
        if (maxFallDistance > 3.0) {
            boolean willLandSoon = willLandWithinTicks(player, 2);
            boolean nearLandingHeight = getFallHeightToLanding(player) < 2.5 && getFallHeightToLanding(player) >= 0.0;
            if (!willLandSoon && !nearLandingHeight) {
                if (maceDelayTicks++ < 12) {
                    return true;
                }
            } else {
                maceDelayTicks = 0;
            }
        }
        return false;
    }

    public static boolean checkCritical(LocalPlayer player, LivingEntity target, boolean onlyCrits, boolean smartCrits) {
        if (player == null) return true;
        if (player.isFallFlying()) return true;
        if (isInCobweb(player)) return true;

        if (isMaceActive(player) && !player.onGround()) {
            if (shouldDelayMaceAttack(player, target)) {
                return false;
            }
            if (MaceItem.canSmashAttack(player) && player.getDeltaMovement().y < 0.0) {
                return true;
            }
        }

        if (!onlyCrits) return true;
        if (player.isInWater() || player.isUnderWater()) return true;
        if (im.yaHustler.module.impl.combat.Criticals.isActive()) return im.yaHustler.module.impl.combat.Criticals.allowsAuraAttack(player);
        if (!canPerformCriticalHit(player)) return false;

        boolean isFalling = !player.onGround()
                && player.fallDistance > 0.0f
                && player.getDeltaMovement().y < -0.0784D;

        if (isFalling) {
            return canCritTarget(player, target, true);
        }

        if (smartCrits && player.onGround() && !player.input.keyPresses.jump()) {
            return true;
        }
        return false;
    }

    public static boolean isCritReadyForSpooky(LocalPlayer player, LivingEntity target) {
        if (player == null) return false;
        if (player.isFallFlying()) return true;

        if (isMaceActive(player) && MaceItem.canSmashAttack(player) && player.getDeltaMovement().y < 0.0) {
            return !shouldDelayMaceAttack(player, target);
        }

        if (!player.onGround() && player.getDeltaMovement().y < -0.05 && canPerformCriticalHit(player)) {
            return true;
        }

        return willLandWithinTicks(player, 1) && canPerformCriticalHit(player);
    }

    private static boolean containsFluid(LocalPlayer player, AABB box) {
        if (player.level() == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)
        )) {
            var fluid = player.level().getBlockState(pos).getFluidState();
            if (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsObstacleOrFluid(LocalPlayer player, AABB box) {
        if (player.level() == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)
        )) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH)) {
                return true;
            }
            if (state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }
}
