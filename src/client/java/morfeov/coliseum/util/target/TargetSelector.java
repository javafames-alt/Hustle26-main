package morfeov.coliseum.util.target;



import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.Comparator;
import java.util.List;


public class TargetSelector {
    private static final double[] MULTIPOINT_HEIGHTS = {0.12, 0.28, 0.42, 0.56, 0.70, 0.84, 0.96};
    private static final double[] VISIBILITY_HEIGHTS = {0.2, 0.5, 0.8};
    private static final double[][] MULTIPOINT_OFFSETS = {
            {0.0, 0.0},
            {-0.30, 0.0},
            {0.30, 0.0},
            {0.0, -0.30},
            {0.0, 0.30},
            {-0.18, -0.18},
            {-0.18, 0.18},
            {0.18, -0.18},
            {0.18, 0.18}
    };

    
    public void setConfig(TargetSelectorConfig value) { config = value; }
    private TargetSelectorConfig config;
    
    private Integer focusedTargetId = null;

    public TargetSelector(TargetSelectorConfig config) {
        this.config = config;
    }

    public LivingEntity select(Player player, double searchRange) {
        return select(player, searchRange, 360.0f, player.getYRot());
    }

    public LivingEntity select(Player player, double searchRange, float horizontalFov) {
        return select(player, searchRange, horizontalFov, player.getYRot());
    }

    public LivingEntity select(Player player, double searchRange,
                               float horizontalFov, float referenceYaw) {
        var world = player.level();

        if (config == null) return null;
        TargetRuleSet rules = ruleSet();
        Vec3 playerEye = player.getEyePosition();
        double searchRangeSquared = searchRange * searchRange;
        float clampedFov = Mth.clamp(horizontalFov, 1.0f, 360.0f);

        if (config.focusTarget() && focusedTargetId != null) {
            var entity = world.getEntity(focusedTargetId);
            if (entity instanceof LivingEntity living
                    && shouldKeepFocused(
                    player, living, playerEye, searchRangeSquared, rules, clampedFov, referenceYaw
            )) {
                return living;
            }
        }

        AABB searchBox = player.getBoundingBox().inflate(searchRange);
        List<LivingEntity> candidates = world.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> isValid(
                        player, e, playerEye, searchRangeSquared, rules, clampedFov, referenceYaw
                )
        );

        Comparator<LivingEntity> comparator = comparatorFor(player);
        LivingEntity selected = null;
        for (LivingEntity candidate : candidates) {
            if (selected == null || comparator.compare(candidate, selected) < 0) {
                selected = candidate;
            }
        }

        focusedTargetId = config.focusTarget() && selected != null ? selected.getId() : null;
        return selected;
    }

    public void clearFocus() {
        focusedTargetId = null;
    }

    private boolean shouldKeepFocused(Player player, LivingEntity candidate, Vec3 playerEye,
                                      double searchRangeSquared, TargetRuleSet rules,
                                      float horizontalFov, float referenceYaw) {
        return isValid(
                player, candidate, playerEye, searchRangeSquared, rules, horizontalFov, referenceYaw
        );
    }

    private boolean isValid(Player player, LivingEntity candidate, Vec3 playerEye,
                            double searchRangeSquared, TargetRuleSet rules,
                            float horizontalFov, float referenceYaw) {
        if (candidate == player || !candidate.isAlive() || candidate.isRemoved()) return false;
        if (closestPointSquared(playerEye, candidate.getBoundingBox()) > searchRangeSquared) return false;
        if (!isWithinHorizontalFov(player, candidate, horizontalFov, referenceYaw)) return false;
        if (!config.ignoreWalls() && !canSeeTarget(player, playerEye, candidate.getBoundingBox())) return false;
        return TargetRules.isValidLiving(player, candidate, rules);
    }

    private boolean canSeeTarget(Player player, Vec3 eye, AABB box) {
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double height = box.maxY - box.minY;
        for (double fraction : VISIBILITY_HEIGHTS) {
            if (canSeePoint(player, eye, new Vec3(centerX, box.minY + height * fraction, centerZ))) return true;
        }
        return false;
    }

    private static boolean isWithinHorizontalFov(
            Player player,
            LivingEntity candidate,
            float horizontalFov,
            float referenceYaw
    ) {
        if (horizontalFov >= 359.999f) {
            return true;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 target = candidate.getBoundingBox().getCenter();
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float targetYaw = Mth.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f
        );
        float yawDifference = Math.abs(
                Mth.wrapDegrees(targetYaw - referenceYaw)
        );
        return yawDifference <= horizontalFov * 0.5f;
    }

    private static double closestPointSquared(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private Comparator<LivingEntity> comparatorFor(Player player) {
        return switch (config.priority()) {
            case Distance -> Comparator.comparingDouble(player::distanceToSqr);
            case Health -> Comparator.comparingDouble(LivingEntity::getHealth)
                    .thenComparingDouble(player::distanceToSqr);
            case Armor -> Comparator.comparingInt(LivingEntity::getArmorValue)
                    .thenComparingDouble(player::distanceToSqr);
            case FieldOfView -> Comparator.<LivingEntity, Float>comparing(e -> fovScore(player, e))
                    .thenComparingDouble(player::distanceToSqr);
            case AllAtOnce -> Comparator.<LivingEntity, Float>comparing(e -> fovScore(player, e))
                    .thenComparingDouble(player::distanceToSqr)
                    .thenComparingDouble(LivingEntity::getHealth)
                    .thenComparingInt(LivingEntity::getArmorValue);
        };
    }

    public float[] computeAngles(Player player, LivingEntity target) {
        return computeAngles(player, computeAimPoint(player, target));
    }

    public Vec3 computeAimPoint(Player player, LivingEntity target) {
        Vec3 playerEye = player.getEyePosition();
        return bestAimPoint(player, target, playerEye);
    }

    public float[] computeAngles(Player player, Vec3 targetPoint) {
        Vec3 playerEye = player.getEyePosition();
        double dx = targetPoint.x - playerEye.x;
        double dy = targetPoint.y - playerEye.y;
        double dz = targetPoint.z - playerEye.z;
        double flatDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float pitch = Mth.clamp((float) (-Math.toDegrees(Math.atan2(dy, flatDistance))), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    private Vec3 bestAimPoint(Player player, LivingEntity target, Vec3 playerEye) {
        AABB box = target.getBoundingBox();
        Vec3 bestPoint = target.getEyePosition();
        double bestScore = Double.MAX_VALUE;

        for (double height : MULTIPOINT_HEIGHTS) {
            double y = box.minY + (box.maxY - box.minY) * height;
            for (double[] offset : MULTIPOINT_OFFSETS) {
                Vec3 point = pointInBox(box, offset[0], y, offset[1]);
                double score = aimPointScore(player, playerEye, point);
                if (config != null && !config.ignoreWalls() && !canSeePoint(player, playerEye, point)) score += 180.0;
                if (score < bestScore) {
                    bestScore = score;
                    bestPoint = point;
                }
            }
        }

        return bestPoint;
    }

    private Vec3 pointInBox(AABB box, double xFactor, double y, double zFactor) {
        double halfX = (box.maxX - box.minX) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double x = Mth.clamp(centerX + halfX * xFactor, box.minX, box.maxX);
        double z = Mth.clamp(centerZ + halfZ * zFactor, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private double aimPointScore(Player player, Vec3 playerEye, Vec3 point) {
        double dx = point.x - playerEye.x;
        double dy = point.y - playerEye.y;
        double dz = point.z - playerEye.z;
        double flatDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float pitch = Mth.clamp((float) (-Math.toDegrees(Math.atan2(dy, flatDistance))), -90f, 90f);
        return Math.abs(Mth.wrapDegrees(yaw - player.getYRot()))
                + Math.abs(pitch - player.getXRot())
                + point.distanceToSqr(playerEye) * 0.015;
    }

    private boolean canSeePoint(Player player, Vec3 start, Vec3 point) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                start, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.04;
    }

    private float fovScore(Player player, LivingEntity target) {
        float[] angles = computeAngles(player, target);
        return Math.abs(Mth.wrapDegrees(angles[0] - player.getYRot()))
                + Math.abs(angles[1] - player.getXRot());
    }

    private TargetRuleSet ruleSet() {
        return new TargetRuleSet(
                config.targetPlayers(),
                config.targetMobs(),
                config.targetAnimals(),
                config.targetInvisibles(),
                config.targetNaked(),
                config.targetVillagers()
        );
    }
}
