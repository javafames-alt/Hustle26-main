package im.yaHustler.port;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
public final class InvActionUtil implements IMinecraft {
    public static final int PRIORITY_COMBAT=50,PRIORITY_SURVIVAL=100;
    private static final InvActionUtil INSTANCE=new InvActionUtil();
    private String owner; private int priority; private long expiry;
    public static InvActionUtil get() { return INSTANCE; }
    public boolean tryAcquire(String name,int prio,long duration) { if(mc.player==null||mc.gameMode==null)return false; long now=System.currentTimeMillis(); if(owner!=null&&!owner.equals(name)&&now<expiry)return false; owner=name;priority=prio;expiry=now+duration;return true; }
    public boolean owns(String name) { return name.equals(owner)&&System.currentTimeMillis()<expiry; }
    public void release(String name) { if(name.equals(owner))owner=null; }
    public void reset() { owner=null; }
    public boolean busy() { return owner!=null && System.currentTimeMillis()<expiry; }
    public boolean clickSlot(String name,int prio,int id,int slot,int button,ContainerInput action,long duration) { if(!im.yaHustler.util.InventoryActions.ready(mc)||!tryAcquire(name,prio,duration))return false; mc.gameMode.handleContainerInput(id,slot,button,action,mc.player);return true; }
    public boolean selectHotbar(String name,int prio,int slot,boolean sync,long duration) { if(!tryAcquire(name,prio,duration))return false;mc.player.getInventory().setSelectedSlot(slot);if(sync)mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));return true; }
}
