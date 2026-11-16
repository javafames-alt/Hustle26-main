package morfeov.coliseum.module.impl.misc.auction;
public final class InventorySlotManager {
    private static final InventorySlotManager instance=new InventorySlotManager();
    public static InventorySlotManager getInstance(){return instance;}
    public boolean isLocked(){return morfeov.coliseum.port.InvActionUtil.get().busy();}
}
