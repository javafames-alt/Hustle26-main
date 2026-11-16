package morfeov.coliseum.module.impl.combat.crystal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import morfeov.coliseum.port.IMinecraft;
import morfeov.coliseum.util.player.CrystalDamageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zenith Class35 exposure, variable explosion power and reductions, with a per-plan cache. */
public final class CrystalDamage implements IMinecraft {
    private record Exposure(Vec3 blast, AABB box, BlockPos added, Set<BlockPos> removed) {}
    private final Map<Exposure, Float> cache = new HashMap<>();
    public void reset() { cache.clear(); }

    public float damage(Vec3 blast, float power, Player target, AABB box, BlockPos added, Set<BlockPos> removed) {
        return calculate(blast, power, target, box, added, removed, false);
    }

    public float maximum(Vec3 blast, float power, Player target, AABB box) {
        return calculate(blast, power, target, box, null, Set.of(), true);
    }

    private float calculate(Vec3 blast, float power, Player target, AABB box, BlockPos added, Set<BlockPos> removed, boolean maximum) {
        if (mc.level == null || !target.isAlive() || target.isCreative() || target.isSpectator() || target.isInvulnerable()) return 0;
        double diameter = power * 2;
        Vec3 feet = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        double distance = feet.distanceTo(blast) / diameter;
        if (distance > 1) return 0;
        double impact = (1 - distance) * (maximum ? 1 : exposure(blast, box, added, removed));
        float value = (float) ((impact * impact + impact) * .5 * 7 * diameter + 1);
        value = switch (mc.level.getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(value / 2 + 1, value);
            case NORMAL -> value;
            case HARD -> value * 1.5f;
        };
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        value *= 1 - Mth.clamp(armor - value / (2 + toughness / 4), armor * .2f, 20) / 25;
        var resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) value *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * .2f);
        int protection = CrystalDamageUtil.getProtectionAmount(List.of(target.getItemBySlot(EquipmentSlot.HEAD),
                target.getItemBySlot(EquipmentSlot.CHEST), target.getItemBySlot(EquipmentSlot.LEGS), target.getItemBySlot(EquipmentSlot.FEET)));
        return Math.max(0, value * (1 - Mth.clamp(protection, 0, 20) / 25f));
    }

    private float exposure(Vec3 blast, AABB box, BlockPos added, Set<BlockPos> removed) {
        return cache.computeIfAbsent(new Exposure(blast, box, added, removed), key -> {
            double sx = 1 / ((box.maxX - box.minX) * 2 + 1), sy = 1 / ((box.maxY - box.minY) * 2 + 1), sz = 1 / ((box.maxZ - box.minZ) * 2 + 1);
            double ox = (1 - Math.floor(1 / sx) * sx) / 2, oz = (1 - Math.floor(1 / sz) * sz) / 2;
            int clear = 0, total = 0;
            for (double x = 0; x <= 1; x += sx) for (double y = 0; y <= 1; y += sy) for (double z = 0; z <= 1; z += sz) {
                Vec3 sample = new Vec3(Mth.lerp(x, box.minX, box.maxX) + ox, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + oz);
                boolean visible = BlockGetter.traverseBlocks(sample, blast, Boolean.TRUE, (context, pos) -> {
                    if (removed.contains(pos)) return null;
                    var state = pos.equals(added) ? Blocks.OBSIDIAN.defaultBlockState() : mc.level.getBlockState(pos);
                    return state.getCollisionShape(mc.level, pos).clip(sample, blast, pos) == null ? null : Boolean.FALSE;
                }, context -> true);
                total++;
                if (visible) clear++;
            }
            return total == 0 ? 0 : (float) clear / total;
        });
    }

    /** Donor method5906: gravity and axis-wise collision clipping, capped at twelve ticks. */
    public static AABB predict(Player player, int ticks) {
        AABB box = player.getBoundingBox();
        Vec3 velocity = player.position().subtract(player.xo, player.yo, player.zo);
        if (velocity.lengthSqr() > 4 || mc.level == null) return box;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) return box;
        }
        if (player.isFallFlying()) return box.move(velocity.scale(Math.min(ticks, 12)));
        return simulate(player, box, velocity, player.onGround(), ticks);
    }

    public static AABB simulate(Player player, AABB box, Vec3 velocity, boolean onGround, int ticks) {
        double vx = velocity.x, vy = onGround && velocity.y <= 0 ? -.0784 : velocity.y, vz = velocity.z;
        if (Math.hypot(vx, vz) < .03) { vx = 0; vz = 0; }
        for (int i = 0; i < Math.min(ticks, 12); i++) {
            Vec3 step = Entity.collideBoundingBox(player, new Vec3(vx, vy, vz), box, mc.level, List.of());
            boolean grounded = vy < 0 && step.y > vy + .0001;
            boolean ceiling = vy > 0 && step.y < vy - .0001;
            if (Math.abs(step.x - vx) > .0001) vx = 0;
            if (Math.abs(step.z - vz) > .0001) vz = 0;
            box = box.move(step);
            vy = grounded ? -.0784 : ((ceiling ? 0 : vy) - .08) * .98;
        }
        return box;
    }
}
