package im.yaHustler.module.impl.combat.crystal;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.Difficulty;
import im.yaHustler.port.IMinecraft;
import im.yaHustler.util.player.CrystalDamageUtil;

import java.util.List;
import java.util.Set;

/** Local explosion damage and prediction; hypothetical excavation never changes the world. */
public final class CrystalDamageCalculator implements IMinecraft {
    private CrystalDamageCalculator() { }

    public static AABB predictBox(Player player, int ticks) {
        AABB box = player.getBoundingBox();
        Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
        // Do not extrapolate teleports or move a prediction through collision shapes / webs.
        if (motion.lengthSqr() > 4.0) return box;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) return box;
        }
        for (int tick = 0; tick < ticks; tick++) {
            AABB next = box.move(motion);
            if (!mc.level.noCollision(player, next)) break;
            box = next;
        }
        return box;
    }

    public static float damage(Vec3 explosion, LivingEntity player, AABB box,
                               BlockPos addedObsidian, BlockPos removedBlock, boolean emulateNormal) {
        return damageAfterMining(explosion, player, box, addedObsidian,
                removedBlock == null ? Set.of() : Set.of(removedBlock), emulateNormal);
    }

    public static float damageAfterMining(Vec3 explosion, LivingEntity player, AABB box,
                                         BlockPos addedObsidian, Set<BlockPos> removedBlocks, boolean emulateNormal) {
        return calculate(explosion, player, box, addedObsidian, removedBlocks, emulateNormal, false);
    }

    public static float maximumDamage(Vec3 explosion, LivingEntity player, AABB box, boolean emulateNormal) {
        return calculate(explosion, player, box, null, Set.of(), emulateNormal, true);
    }

    private static float calculate(Vec3 explosion, LivingEntity player, AABB box,
                                   BlockPos addedObsidian, Set<BlockPos> removedBlocks,
                                   boolean emulateNormal, boolean maximum) {
        if (player.isInvulnerable() || player.isSpectator() || player instanceof Player user && user.isCreative()) return 0;
        Difficulty difficulty = emulateNormal ? Difficulty.NORMAL : mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL) return 0;
        Vec3 feet = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        double distance = feet.distanceTo(explosion) / 12.0;
        if (distance > 1.0) return 0;
        double impact = (1.0 - distance) * (maximum ? 1 : exposure(explosion, box, addedObsidian, removedBlocks));
        float damage = (float) Math.floor((impact * impact + impact) * 42.0 + 1.0);
        if (difficulty == Difficulty.EASY) damage = Math.min(damage / 2 + 1, damage);
        if (difficulty == Difficulty.HARD) damage *= 1.5f;
        damage = CombatRules.getDamageAfterAbsorb(player, damage, mc.level.damageSources().explosion(null, null),
                player.getArmorValue(), (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) damage *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2f);
        int protection = CrystalDamageUtil.getProtectionAmount(List.of(
                player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST),
                player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.FEET)));
        return Math.max(0, CombatRules.getDamageAfterMagicAbsorb(damage, protection));
    }

    private static double exposure(Vec3 explosion, AABB box, BlockPos added, Set<BlockPos> removed) {
        double stepX = 1.0 / ((box.maxX - box.minX) * 2 + 1);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2 + 1);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2 + 1);
        double offsetX = (1 - Math.floor(1 / stepX) * stepX) / 2;
        double offsetZ = (1 - Math.floor(1 / stepZ) * stepZ) / 2;
        int clear = 0;
        int total = 0;
        for (double x = 0; x <= 1; x += stepX) {
            for (double y = 0; y <= 1; y += stepY) {
                for (double z = 0; z <= 1; z += stepZ) {
                    Vec3 sample = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offsetX,
                            Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
                    HitResult.Type hit = BlockGetter.traverseBlocks(sample, explosion, null, (context, pos) -> {
                        BlockState state = pos.equals(added) ? Blocks.OBSIDIAN.defaultBlockState()
                                : removed.contains(pos) ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
                        var result = state.getCollisionShape(mc.level, pos).clip(sample, explosion, pos);
                        return result == null ? null : result.getType();
                    }, context -> HitResult.Type.MISS);
                    if (hit == HitResult.Type.MISS) clear++;
                    total++;
                }
            }
        }
        return (double) clear / total;
    }
}
