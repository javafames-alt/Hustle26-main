package im.yaHustler.module.impl.player;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

@ModuleInfo(id = "auto_armor", name = "AutoArmor", category = Category.PLAYER, description = "Надевает лучшую броню с учётом защиты, чар и прочности")
public final class AutoArmor extends Module {
    private final NumberSetting delay = add(new NumberSetting("Задержка, мс", 150, 0, 1000, 50));
    private final BooleanSetting preserveElytra = add(new BooleanSetting("Сохранять элитры", true));
    private final BooleanSetting inventoryOnly = add(new BooleanSetting("Только в инвентаре", false));
    private long nextAction;

    @Override public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || !player.isAlive()) { nextAction = 0; return; }
        if (client.gui.screen() != null && !(client.gui.screen() instanceof InventoryScreen)) return;
        if (inventoryOnly.enabled() && !(client.gui.screen() instanceof InventoryScreen)) return;
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty() || player.isUsingItem()) return;
        if (System.nanoTime() < nextAction || InvActionUtil.get().busy()) return;
        for (int armorIndex = 5; armorIndex <= 8; armorIndex++) {
            var destination = player.inventoryMenu.getSlot(armorIndex);
            var current = destination.getItem();
            if (!destination.mayPickup(player) || preserveElytra.enabled() && current.has(DataComponents.GLIDER)) continue;
            var equipment = switch (armorIndex) { case 5 -> EquipmentSlot.HEAD; case 6 -> EquipmentSlot.CHEST; case 7 -> EquipmentSlot.LEGS; default -> EquipmentSlot.FEET; };
            int best = -1;
            ItemStack chosen = current;
            for (int i = 0; i < 36; i++) {
                var source = player.inventoryMenu.getSlot(InventoryActions.menuSlot(i));
                var candidate = source.getItem();
                if (!source.mayPickup(player) || !destination.mayPlace(candidate) || candidate.getCount() != 1) continue;
                if (EnchantmentHelper.has(candidate, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) continue;
                if (better(candidate, chosen, equipment, player.registryAccess())) { best = i; chosen = candidate; }
            }
            if (best < 0 || !InvActionUtil.get().tryAcquire(id(), 10, Math.max(50, delay.intValue()))) continue;
            int menu = player.inventoryMenu.containerId;
            if (best < 9) client.gameMode.handleContainerInput(menu, armorIndex, best, ContainerInput.SWAP, player);
            else {
                client.gameMode.handleContainerInput(menu, best, 0, ContainerInput.PICKUP, player);
                client.gameMode.handleContainerInput(menu, armorIndex, 0, ContainerInput.PICKUP, player);
                if (!player.inventoryMenu.getCarried().isEmpty()) client.gameMode.handleContainerInput(menu, best, 0, ContainerInput.PICKUP, player);
            }
            nextAction = System.nanoTime() + delay.intValue() * 1_000_000L;
            return;
        }
    }
    public static boolean better(ItemStack candidate, ItemStack current, EquipmentSlot slot, HolderLookup.Provider registries) {
        double score = score(candidate, slot, registries);
        if (score <= 0) return false;
        double equipped = score(current, slot, registries);
        if (Math.abs(score - equipped) > .0001) return score > equipped;
        return durability(candidate) > durability(current) + .0001;
    }
    private static double score(ItemStack stack, EquipmentSlot slot, HolderLookup.Provider registries) {
        if (stack.isEmpty() || stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage()) return 0;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) return 0;
        var attributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = attributes.compute(Attributes.ARMOR, 0, slot);
        if (armor <= 0) return 0;
        double toughness = attributes.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        var protection = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        return armor * 4 + toughness + EnchantmentHelper.getItemEnchantmentLevel(protection, stack) * 3;
    }
    private static double durability(ItemStack stack) { return stack.isDamageableItem() ? 1 - (double) stack.getDamageValue() / stack.getMaxDamage() : 1; }
    @Override protected void onDisable() { nextAction = 0; InvActionUtil.get().release(id()); }
}
