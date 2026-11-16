package im.yaHustler.util;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Client inventory transactions always use the active menu and restore the original hotbar slot. */
public final class InventoryActions {
    private InventoryActions() {}
    public static boolean ready(Minecraft mc) {
        return mc.player != null && mc.gameMode != null && mc.gui.screen() == null
            && mc.player.containerMenu == mc.player.inventoryMenu && mc.player.containerMenu.getCarried().isEmpty();
    }
    public static int find(Minecraft mc, Item item) { return find(mc, stack -> stack.is(item)); }
    public static int find(Minecraft mc, Predicate<ItemStack> test) {
        if (mc.player == null) return -1;
        int best = -1, score = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !test.test(stack)) continue;
            int value = (stack.isEnchanted() ? 100 : 0) + (i < 9 ? 10 : 0);
            if (value > score) { score = value; best = i; }
        }
        return best;
    }
    public static int menuSlot(int inventorySlot) { return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot; }
    public static void swapOffhand(Minecraft mc, int slot) {
        if (im.yaHustler.port.InvActionUtil.get().busy()) return;
        if (ready(mc) && slot >= 0) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
            menuSlot(slot), 40, ContainerInput.SWAP, mc.player);
    }
    public static void swapEquipment(Minecraft mc, int inventorySlot, int equipmentMenuSlot) {
        if (im.yaHustler.port.InvActionUtil.get().busy()) return;
        if (!ready(mc) || inventorySlot < 0) return;
        int id = mc.player.containerMenu.containerId, source = menuSlot(inventorySlot);
        mc.gameMode.handleContainerInput(id, source, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, equipmentMenuSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, source, 0, ContainerInput.PICKUP, mc.player);
    }
    public static void swapEquipmentFast(Minecraft mc,int inventorySlot,int equipmentMenuSlot) {
        if (im.yaHustler.port.InvActionUtil.get().busy()) return;
        if(!ready(mc)||inventorySlot<0)return;
        int id=mc.player.containerMenu.containerId;
        if(inventorySlot<9) {
            mc.gameMode.handleContainerInput(id,equipmentMenuSlot,inventorySlot,ContainerInput.SWAP,mc.player);
            return;
        }
        int hotbar=mc.player.getInventory().getSelectedSlot(),source=menuSlot(inventorySlot);
        mc.gameMode.handleContainerInput(id,source,hotbar,ContainerInput.SWAP,mc.player);
        mc.gameMode.handleContainerInput(id,equipmentMenuSlot,hotbar,ContainerInput.SWAP,mc.player);
        mc.gameMode.handleContainerInput(id,source,hotbar,ContainerInput.SWAP,mc.player);
    }
    public static int emptySlot(Minecraft mc) {
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }
    public static boolean use(Minecraft mc, Item item, boolean legit) {
        if (im.yaHustler.port.InvActionUtil.get().busy()) return false;
        if (!ready(mc)) return false;
        if (mc.player.getOffhandItem().is(item)) return useHand(mc, InteractionHand.OFF_HAND);
        int slot = find(mc, item);
        if (slot < 0 || mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(slot))) return false;
        if (legit) {
            swapOffhand(mc, slot);
            try { return useHand(mc, InteractionHand.OFF_HAND); }
            finally { swapOffhand(mc, slot); }
        }
        int previous = mc.player.getInventory().getSelectedSlot();
        boolean fromInventory = slot >= 9;
        if (fromInventory) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
            menuSlot(slot), previous, ContainerInput.SWAP, mc.player);
        else mc.player.getInventory().setSelectedSlot(slot);
        try { return useHand(mc, InteractionHand.MAIN_HAND); }
        finally {
            mc.player.getInventory().setSelectedSlot(previous);
            // useItem synchronizes selection before use; synchronize the restored slot as well.
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(previous));
            if (fromInventory) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
                menuSlot(slot), previous, ContainerInput.SWAP, mc.player);
        }
    }
    private static boolean useHand(Minecraft mc, InteractionHand hand) {
        if (mc.player.getCooldowns().isOnCooldown(mc.player.getItemInHand(hand))) return false;
        var result = mc.gameMode.useItem(mc.player, hand);
        if (result.consumesAction()) mc.player.swing(hand,mc.player.getItemInHand(hand).getInteractAnimation(),false);
        return result.consumesAction();
    }
}
