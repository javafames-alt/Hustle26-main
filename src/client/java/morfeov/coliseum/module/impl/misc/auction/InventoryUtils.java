package morfeov.coliseum.module.impl.misc.auction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
public final class InventoryUtils {
    public static int getSelectedSlot(){return Minecraft.getInstance().player.getInventory().getSelectedSlot();}
    public static int getScreenSlotId(int slot){var mc=Minecraft.getInstance();for(var entry:mc.player.containerMenu.slots)if(entry.container==mc.player.getInventory()&&entry.getContainerSlot()==slot)return entry.index;return -1;}
    public static int findEmptySlot(int from,int to){var mc=Minecraft.getInstance();for(int i=from;i<=to;i++)if(mc.player.getInventory().getItem(i).isEmpty())return i;return -1;}
    public static void sendSlotClickPacket(int slot,int button,ContainerInput input,boolean silent){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.gameMode==null||slot<0||slot>=mc.player.containerMenu.slots.size())return;
        if(morfeov.coliseum.port.InvActionUtil.get().tryAcquire("AutoSell",20,100))mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,slot,button,input,mc.player);
    }
    public static int findSlotMatching(java.util.function.Predicate<net.minecraft.world.item.ItemStack> test,int from,int to){var mc=Minecraft.getInstance();for(int i=from;i<=to;i++)if(test.test(mc.player.getInventory().getItem(i)))return i;return -1;}
    public static int findOffhandSlot(java.util.function.Predicate<net.minecraft.world.item.ItemStack> test){int found=findSlotMatching(test,0,35);return found>=0?found:test.test(Minecraft.getInstance().player.getOffhandItem())?40:-1;}
    public static int findHotbarSlot(net.minecraft.world.item.Item item){return findSlotMatching(s->s.is(item),0,8);}
    public static int findInventorySlot(net.minecraft.world.item.Item item){return findSlotMatching(s->s.is(item),0,35);}
    public static void setSelectedSlot(int slot,boolean silent){var mc=Minecraft.getInstance();if(slot<0||slot>8)return;mc.player.getInventory().setSelectedSlot(slot);((morfeov.coliseum.mixin.CombatGameModeAccessor)mc.gameMode).coliseum$syncSelectedSlot();}
    public static void clickSlot(net.minecraft.world.inventory.Slot slot,int button,ContainerInput input,boolean silent){sendSlotClickPacket(slot.index,button,input,silent);}
    public static void pickupAll(){var mc=Minecraft.getInstance();if(mc.player==null||mc.player.containerMenu.getCarried().isEmpty())return;int slot=findEmptySlot(0,35);if(slot>=0)sendSlotClickPacket(getScreenSlotId(slot),0,ContainerInput.PICKUP,false);}
    public static void useItem(net.minecraft.world.InteractionHand hand){var mc=Minecraft.getInstance();mc.gameMode.useItem(mc.player,hand);}
}
