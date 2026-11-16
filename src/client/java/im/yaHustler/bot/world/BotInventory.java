package im.yaHustler.bot.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class BotInventory {
    public record Container(int id, int stateId, MenuType<?> type, Component title,
                            List<ItemStack> slots, Map<Integer, Integer> properties) {}
    private final List<ItemStack> playerSlots = emptySlots(46);
    private List<ItemStack> containerSlots = List.of();
    private final Map<Integer, Integer> properties = new HashMap<>();
    private int containerId, playerStateId, containerStateId, selectedSlot;
    private MenuType<?> containerType;
    private Component title = Component.empty();
    private ItemStack playerCursor = ItemStack.EMPTY, containerCursor = ItemStack.EMPTY;

    public synchronized boolean handle(Packet<?> packet) {
        if (packet instanceof ClientboundOpenScreenPacket open) {
            containerId = open.getContainerId();
            containerType = open.getType();
            title = open.getTitle().copy();
            containerStateId = 0;
            containerSlots = List.of();
            containerCursor = ItemStack.EMPTY;
            properties.clear();
        } else if (packet instanceof ClientboundContainerClosePacket close) {
            if (close.getContainerId() == containerId) closeContainer();
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            if (content.containerId() == 0) {
                replace(playerSlots, content.items());
                updateContainerInventory();
                playerStateId = content.stateId();
                playerCursor = content.carriedItem().copy();
            } else if (content.containerId() == containerId) {
                containerSlots = copyItems(content.items());
                updatePlayerInventory();
                containerStateId = content.stateId();
                containerCursor = content.carriedItem().copy();
            } else return true;
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            if (slot.getContainerId() == 0) {
                setPlayerSlot(slot.getSlot(), slot.getItem());
                playerStateId = slot.getStateId();
            } else if (slot.getContainerId() == containerId) {
                set(containerSlots, slot.getSlot(), slot.getItem());
                int playerSlot = slot.getSlot() - inventoryStart() + 9;
                if (inventoryStart() >= 0 && playerSlot >= 9 && playerSlot < 45) set(playerSlots, playerSlot, slot.getItem());
                containerStateId = slot.getStateId();
            }
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket slot) {
            int index = slot.slot();
            if (index >= 0 && index < 9) setPlayerSlot(36 + index, slot.contents());
            else if (index >= 9 && index < 36) setPlayerSlot(index, slot.contents());
            else if (index >= 36 && index < 40) setPlayerSlot(44 - index, slot.contents());
            else if (index == 40) setPlayerSlot(45, slot.contents());
        } else if (packet instanceof ClientboundSetCursorItemPacket item) {
            if (containerId == 0) playerCursor = item.contents().copy();
            else containerCursor = item.contents().copy();
        } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
            if (held.slot() >= 0 && held.slot() < 9) selectedSlot = held.slot();
        } else if (packet instanceof ClientboundContainerSetDataPacket data) {
            if (data.getContainerId() == containerId && containerId != 0) properties.put(data.getId(), data.getValue());
        } else return false;
        return true;
    }

    public synchronized Container snapshot() {
        return new Container(containerId, containerId == 0 ? playerStateId : containerStateId, containerType,
            title.copy(), List.copyOf(copyItems(containerId == 0 ? playerSlots : containerSlots)), Map.copyOf(properties));
    }
    public synchronized List<ItemStack> playerSlots() { return List.copyOf(copyItems(playerSlots)); }
    public synchronized ItemStack mainHand() { return playerSlots.get(36 + selectedSlot).copy(); }
    public synchronized ItemStack offHand() { return playerSlots.get(45).copy(); }
    public synchronized ItemStack cursor() { return (containerId == 0 ? playerCursor : containerCursor).copy(); }
    public synchronized int selectedSlot() { return selectedSlot; }

    private int inventoryStart() { return containerId != 0 && containerType != MenuType.LECTERN && containerSlots.size() >= 36 ? containerSlots.size() - 36 : -1; }
    private void setPlayerSlot(int index, ItemStack item) {
        set(playerSlots, index, item);
        if (inventoryStart() >= 0 && index >= 9 && index < 45) set(containerSlots, inventoryStart() + index - 9, item);
    }
    private void updatePlayerInventory() {
        int start = inventoryStart();
        if (start >= 0) for (int i = 0; i < 36; i++) set(playerSlots, 9 + i, containerSlots.get(start + i));
    }
    private void updateContainerInventory() {
        int start = inventoryStart();
        if (start >= 0) for (int i = 0; i < 36; i++) set(containerSlots, start + i, playerSlots.get(9 + i));
    }

    public synchronized void closeContainer() {
        containerId = containerStateId = 0;
        containerType = null;
        title = Component.empty();
        containerSlots = List.of();
        containerCursor = ItemStack.EMPTY;
        properties.clear();
    }
    public synchronized void reset() {
        closeContainer();
        for (int i = 0; i < playerSlots.size(); i++) playerSlots.set(i, ItemStack.EMPTY);
        playerCursor = ItemStack.EMPTY;
        selectedSlot = playerStateId = 0;
    }
    private static void set(List<ItemStack> slots, int index, ItemStack item) {
        if (index >= 0 && index < slots.size()) slots.set(index, item.copy());
    }
    private static void replace(List<ItemStack> target, List<ItemStack> source) {
        for (int i = 0; i < target.size(); i++) target.set(i, i < source.size() ? source.get(i).copy() : ItemStack.EMPTY);
    }
    private static List<ItemStack> copyItems(List<ItemStack> source) {
        var result = new ArrayList<ItemStack>(source.size());
        for (var item : source) result.add(item.copy());
        return result;
    }
    private static List<ItemStack> emptySlots(int size) {
        var result = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) result.add(ItemStack.EMPTY);
        return result;
    }
}
