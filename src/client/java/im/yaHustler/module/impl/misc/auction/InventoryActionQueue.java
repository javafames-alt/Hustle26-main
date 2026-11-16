package im.yaHustler.module.impl.misc.auction;
public final class InventoryActionQueue {
    private static final InventoryActionQueue instance=new InventoryActionQueue();
    public static InventoryActionQueue getInstance(){return instance;}
    public boolean hasPendingActions(){return im.yaHustler.port.InvActionUtil.get().busy();}
    public void queueSlotSwap(int source,int hotbar,int delay){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(source<0||source>=36||hotbar<0||hotbar>8||!im.yaHustler.util.InventoryActions.ready(mc))return;
        if(!im.yaHustler.port.InvActionUtil.get().tryAcquire("AutoSell",20,100))return;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,im.yaHustler.util.InventoryActions.menuSlot(source),hotbar,net.minecraft.world.inventory.ContainerInput.SWAP,mc.player);
    }
}
