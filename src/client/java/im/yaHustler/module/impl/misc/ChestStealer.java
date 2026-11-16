package im.yaHustler.module.impl.misc;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(id="chest_stealer", name="ChestStealer", category=Category.MISC, description="Забирает предметы из сундуков с задержкой и закрывает пустые сундуки")
public final class ChestStealer extends Module {
    private final ModeSetting mode=add(new ModeSetting("Режим","FunTime","FunTime","HolyWorld","ReallyWorld","Custom"));
    private final NumberSetting startDelay=add(new NumberSetting("Начальная задержка, тики",4,0,60,1).visibleWhen(()->mode.is("Custom")));
    private final NumberSetting minDelay=add(new NumberSetting("Мин. задержка, тики",0,0,60,1).visibleWhen(()->mode.is("Custom")));
    private final NumberSetting maxDelay=add(new NumberSetting("Макс. задержка, тики",6,0,60,1).visibleWhen(()->mode.is("Custom")));
    private final BooleanSetting closeScreen=add(new BooleanSetting("Закрывать пустой сундук",true));
    private final NumberSetting closeDelay=add(new NumberSetting("Задержка закрытия, тики",4,0,60,1).visibleWhen(()->mode.is("Custom")&&closeScreen.enabled()));
    private ChestMenu lastMenu;
    private long nextClick,emptySince=-1;
    private Object level;

    public static boolean excludedTitle(String title) {
        String value=title.toLowerCase(Locale.ROOT);
        return value.contains("аукцион") || value.contains("покупки") || value.contains("auction") || value.contains("purchases");
    }
    private int preset(int fun,int holy,int rw,NumberSetting custom) {
        return mode.is("FunTime")?fun:mode.is("HolyWorld")?holy:mode.is("ReallyWorld")?rw:custom.intValue();
    }
    @Override public void tick(Minecraft mc) {
        if(mc.player==null || mc.level==null || mc.gameMode==null || mc.gui.screen()==null || !(mc.player.containerMenu instanceof ChestMenu menu)) { reset();return; }
        long now=System.nanoTime();
        if(lastMenu!=menu || level!=mc.level) { reset();lastMenu=menu;level=mc.level;nextClick=now+preset(8,1,5,startDelay)*50_000_000L; }
        if(excludedTitle(mc.gui.screen().getTitle().getString()) || !menu.getCarried().isEmpty()) return;
        int count=menu.getRowCount()*9;
        boolean empty=true;
        int selected=-1,seen=0;
        for(int i=0;i<count;i++) {
            var slot=menu.getSlot(i);
            if(!slot.hasItem())continue;
            empty=false;
            if(slot.mayPickup(mc.player) && canReceive(mc,slot.getItem()) && ThreadLocalRandom.current().nextInt(++seen)==0) selected=i;
        }
        if(empty) {
            if(emptySince<0) emptySince=now;
            if(closeScreen.enabled() && now-emptySince>=preset(7,0,2,closeDelay)*50_000_000L) { mc.player.closeContainer();reset(); }
            return;
        }
        emptySince=-1;
        if(selected<0 || now<nextClick || !InvActionUtil.get().tryAcquire(id(),10,50)) return;
        mc.gameMode.handleContainerInput(menu.containerId,selected,0,ContainerInput.QUICK_MOVE,mc.player);
        int min=preset(4,1,1,minDelay),max=preset(8,1,4,maxDelay);
        nextClick=now+ThreadLocalRandom.current().nextLong(Math.min(min,max),Math.max(min,max)+1L)*50_000_000L;
    }
    private boolean canReceive(Minecraft mc,ItemStack stack) {
        for(int i=0;i<36;i++) {
            var current=mc.player.getInventory().getItem(i);
            if(current.isEmpty() || ItemStack.isSameItemSameComponents(current,stack) && current.getCount()<current.getMaxStackSize()) return true;
        }
        return false;
    }
    private void reset() { lastMenu=null;level=null;nextClick=0;emptySince=-1;InvActionUtil.get().release(id()); }
    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
}
