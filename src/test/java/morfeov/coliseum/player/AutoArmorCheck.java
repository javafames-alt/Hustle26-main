package morfeov.coliseum.player;

import morfeov.coliseum.module.impl.player.AutoArmor;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import static org.junit.jupiter.api.Assertions.*;

public final class AutoArmorCheck {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(DataComponentInitializers.PendingComponents::apply);
        var iron = new ItemStack(Items.IRON_CHESTPLATE);
        var diamond = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var netherite = new ItemStack(Items.NETHERITE_CHESTPLATE);
        assertTrue(AutoArmor.better(iron, ItemStack.EMPTY, EquipmentSlot.CHEST, registries));
        assertTrue(AutoArmor.better(diamond, iron, EquipmentSlot.CHEST, registries));
        assertTrue(AutoArmor.better(netherite, diamond, EquipmentSlot.CHEST, registries));
        assertFalse(AutoArmor.better(iron, diamond, EquipmentSlot.CHEST, registries));
        assertFalse(AutoArmor.better(diamond.copy(), diamond, EquipmentSlot.CHEST, registries));
        diamond.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        assertTrue(AutoArmor.better(diamond, netherite, EquipmentSlot.CHEST, registries));
        var damaged = diamond.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        assertTrue(AutoArmor.better(diamond, damaged, EquipmentSlot.CHEST, registries));
        assertFalse(AutoArmor.better(damaged, diamond, EquipmentSlot.CHEST, registries));
        assertFalse(AutoArmor.better(diamond, ItemStack.EMPTY, EquipmentSlot.HEAD, registries));
        assertFalse(AutoArmor.better(new ItemStack(Items.ELYTRA), iron, EquipmentSlot.CHEST, registries));
        assertFalse(AutoArmor.better(new ItemStack(Items.CARVED_PUMPKIN), ItemStack.EMPTY, EquipmentSlot.HEAD, registries));
        damaged.setDamageValue(damaged.getMaxDamage());
        assertFalse(AutoArmor.better(damaged, ItemStack.EMPTY, EquipmentSlot.CHEST, registries));
        System.out.println("AutoArmor material, protection, durability, equipment slot and non-armor filtering checks passed.");
    }
}
