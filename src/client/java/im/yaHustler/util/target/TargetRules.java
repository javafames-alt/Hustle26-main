package im.yaHustler.util.target;



import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

import java.util.Set;


public final class TargetRules {

    private static final Set<EntityType<?>> WATER_ANIMAL_TYPES = Set.of(
            net.minecraft.world.entity.EntityTypes.AXOLOTL,
            net.minecraft.world.entity.EntityTypes.COD,
            net.minecraft.world.entity.EntityTypes.DOLPHIN,
            net.minecraft.world.entity.EntityTypes.GLOW_SQUID,
            net.minecraft.world.entity.EntityTypes.PUFFERFISH,
            net.minecraft.world.entity.EntityTypes.SALMON,
            net.minecraft.world.entity.EntityTypes.SQUID,
            net.minecraft.world.entity.EntityTypes.TADPOLE,
            net.minecraft.world.entity.EntityTypes.TROPICAL_FISH
    );

    private TargetRules() {
    }

    public static boolean isValidEntity(Player viewer, Entity entity, TargetRuleSet rules) {
        if (entity instanceof ItemEntity item)
            return rules.targetItems() && !item.isRemoved();
        if (entity instanceof LivingEntity living)
            return isValidLiving(viewer, living, rules);
        return false;
    }

    public static boolean isValidLiving(Player viewer, LivingEntity candidate, TargetRuleSet rules) {
        if (candidate == viewer || !candidate.isAlive() || candidate.isRemoved())
            return false;
        if (!rules.targetInvisibles() && candidate.isInvisible())
            return false;

        if (candidate instanceof Player player) {
            if (im.yaHustler.module.impl.combat.AntiBot.rejects(player)) return false;
            if (im.yaHustler.friend.Friends.contains(player)) return false;
            if (player.isSpectator()) return false;
            if (!rules.targetPlayers()) return false;
            if (viewer.isAlliedTo(player)) return false;

            if (!rules.targetNaked() && isNaked(player)) return false;
            return true;
        }

        if (candidate instanceof Animal) return rules.targetAnimals();
        if (candidate instanceof Monster) return rules.targetMobs();

        if (candidate.getType() == net.minecraft.world.entity.EntityTypes.VILLAGER) return rules.targetVillagers();
        if (WATER_ANIMAL_TYPES.contains(candidate.getType())) return rules.targetAnimals();
        return false;
    }

    private static boolean isNaked(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }
}
