package morfeov.coliseum.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import morfeov.coliseum.hud.Notifications;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(id="auto_swap",name="AutoSwap",description="Свапает талисманы и сферы (Дуо и Колесо из brokeSOURSE)",category=Category.COMBAT)
public final class AutoSwap extends Module {
    private static final int MAX_WHEEL_SLOTS=8;
    public final ModeSetting mod=add(new ModeSetting("Мод","Дуо","Дуо","Колесо"));
    public final KeySetting button=add(new KeySetting("Кнопка").visibleWhen(this::duoMode));
    public final ModeSetting itemMode=add(new ModeSetting("Предмет","Талисман","Талисман","Сфера").visibleWhen(this::duoMode));
    public final ModeSetting swapToMode=add(new ModeSetting("Свапать на","Сфера","Талисман","Сфера").visibleWhen(this::duoMode));
    public final ModeSetting server=add(new ModeSetting("Сервер","HW","HW","FT").visibleWhen(this::duoMode));
    public final BooleanSetting separateSwaps=add(new BooleanSetting("Отдельные свапы",false).visibleWhen(this::duoMode));
    public final KeySetting cerberusSphereBind=add(new KeySetting("Сфера цербера").visibleWhen(()->duoMode()&&separateSwaps.enabled()&&server.is("HW")));
    public final KeySetting vortexTalismanBind=add(new KeySetting("Талисман вихря").visibleWhen(()->duoMode()&&separateSwaps.enabled()&&server.is("FT")));
    public final KeySetting wheelBind=add(new KeySetting("Кнопка колеса").visibleWhen(this::wheelMode));
    public final NumberSetting wheelSlots=add(new NumberSetting("Ячейки",6,3,8,1).visibleWhen(this::wheelMode));
    public final ItemSetting[] wheel=new ItemSetting[MAX_WHEEL_SLOTS];
    {for(int i=0;i<MAX_WHEEL_SLOTS;i++){int index=i;wheel[i]=add(new ItemSetting("Предмет круга "+(i+1)).visibleWhen(()->wheelMode()&&index<slotCount()));}}
    private long lastSwapTime;

    public boolean duoMode(){return mod.is("Дуо");}
    public boolean wheelMode(){return mod.is("Колесо");}
    public int slotCount(){return Math.clamp((int)Math.round(wheelSlots.floatValue()),3,MAX_WHEEL_SLOTS);}
    public boolean wheelHeld(){return (wheelBind.get()!=-1?wheelBind:button).down();}
    private boolean totemReservesOffhand(){
        var totem=morfeov.coliseum.Coliseum.MODULES.get(AutoTotem.class);
        return totem!=null&&totem.reservesOffhand();
    }
    @Override public void tick(Minecraft mc){
        if(mc.player==null||mc.level==null)return;
        if(wheelMode()){
            if((wheelBind.get()!=-1?wheelBind:button).pressed(mc))mc.gui.setScreen(new morfeov.coliseum.ui.SwapWheelScreen(this));
            return;
        }
        if(mc.gui.screen()!=null)return;
        if(separateSwaps.enabled()){
            if(server.is("HW")&&cerberusSphereBind.pressed(mc)){swapCerberus(mc);return;}
            if(server.is("FT")&&vortexTalismanBind.pressed(mc)){swapVortex(mc);return;}
        }
        if(button.pressed(mc))executeDuoSwap(mc);
    }
    private void executeDuoSwap(Minecraft mc){
        if(System.currentTimeMillis()-lastSwapTime<150L)return;
        if(itemMode.is("Сфера")&&swapToMode.is("Сфера")&&server.is("HW")){swapDualSpheresHW(mc);return;}
        List<InvItem> firstList=findItems(mc,itemMode.get()),secondList=findItems(mc,swapToMode.get());
        if(firstList.isEmpty()&&secondList.isEmpty()){Notifications.post("AutoSwap: предметы для свапа не найдены",null);return;}
        InvItem first=firstList.isEmpty()?null:firstList.getFirst(),second=secondList.isEmpty()?null:secondList.getFirst();
        ItemStack offhand=mc.player.getOffhandItem();
        boolean holdsFirst=first!=null&&isSameItemType(offhand,first.stack),holdsSecond=second!=null&&isSameItemType(offhand,second.stack);
        InvItem target=holdsFirst&&!holdsSecond?(second!=null?second:first):holdsSecond&&!holdsFirst?(first!=null?first:second):(first!=null?first:second);
        if(target!=null)doSwap(mc,target.slot,target.stack);
    }
    private void swapDualSpheresHW(Minecraft mc){
        List<InvItem> spheres=findItems(mc,"Сфера");
        if(spheres.isEmpty()){Notifications.post("AutoSwap: сферы не найдены",null);return;}
        ItemStack offhand=mc.player.getOffhandItem();
        for(InvItem sphere:spheres)if(!ItemStack.isSameItemSameComponents(offhand,sphere.stack)){doSwap(mc,sphere.slot,sphere.stack);return;}
    }
    private void swapCerberus(Minecraft mc){
        if(System.currentTimeMillis()-lastSwapTime<150L)return;
        for(InvItem item:findItems(mc,"Сфера"))if(isCerberusSphere(item.stack)){doSwap(mc,item.slot,item.stack);return;}
        Notifications.post("AutoSwap: Сфера цербера не найдена",null);
    }
    private void swapVortex(Minecraft mc){
        if(System.currentTimeMillis()-lastSwapTime<150L)return;
        for(InvItem item:findItems(mc,"Талисман"))if(isVortexTalisman(item.stack)){doSwap(mc,item.slot,item.stack);return;}
        Notifications.post("AutoSwap: Талисман вихря не найден",null);
    }
    private void doSwap(Minecraft mc,int menuSlot,ItemStack stack){
        if(totemReservesOffhand()||morfeov.coliseum.port.InvActionUtil.get().busy())return;
        if(mc.player.containerMenu!=mc.player.inventoryMenu||!mc.player.containerMenu.getCarried().isEmpty())return;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,menuSlot,40,ContainerInput.SWAP,mc.player);
        lastSwapTime=System.currentTimeMillis();
        Notifications.post("AutoSwap: "+stack.getHoverName().getString(),null);
    }
    private List<InvItem> findItems(Minecraft mc,String type){
        List<InvItem> result=new ArrayList<>();
        for(Slot slot:mc.player.inventoryMenu.slots){
            if(slot.index==45)continue;
            ItemStack stack=slot.getItem();
            if(stack.isEmpty())continue;
            if("Сфера".equalsIgnoreCase(type)){
                if(isSphere(stack)&&(!separateSwaps.enabled()||!server.is("HW")||!isCerberusSphere(stack)))result.add(new InvItem(slot.index,stack));
            }else if(isTalisman(stack)&&(!separateSwaps.enabled()||!server.is("FT")||!isVortexTalisman(stack)))result.add(new InvItem(slot.index,stack));
        }
        result.sort(Comparator.comparing(item->!item.stack.isEnchanted()));
        return result;
    }
    private static boolean isSameItemType(ItemStack a,ItemStack b){
        if(a.isEmpty()||b.isEmpty()||a.getItem()!=b.getItem())return false;
        return a.getHoverName().getString().toLowerCase(Locale.ROOT).equals(b.getHoverName().getString().toLowerCase(Locale.ROOT));
    }
    private static boolean isSphere(ItemStack stack){
        if(stack.is(Items.PLAYER_HEAD))return true;
        String name=stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("сфера")||name.contains("sphere");
    }
    private static boolean isTalisman(ItemStack stack){
        String name=stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("талисман")||name.contains("talisman");
    }
    private static boolean isCerberusSphere(ItemStack stack){
        String name=stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("цербер")||name.contains("cerber");
    }
    private static boolean isVortexTalisman(ItemStack stack){
        String name=stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("вихр")||name.contains("vortex");
    }
    public boolean matchesWheel(int index,ItemStack stack){
        var wanted=wheel[index].stack();
        return !wanted.isEmpty()&&!stack.isEmpty()&&ItemStack.isSameItemSameComponents(wanted,stack);
    }
    public void selectWheel(int index){
        Minecraft mc=Minecraft.getInstance();
        if(index<0||index>=slotCount()||!enabled()||mc.player==null||mc.gameMode==null)return;
        if(System.currentTimeMillis()-lastSwapTime<150L)return;
        ItemStack template=wheel[index].stack();
        if(template.isEmpty()||totemReservesOffhand()||matchesWheel(index,mc.player.getOffhandItem()))return;
        int slot=InventoryActions.find(mc,stack->matchesWheel(index,stack));
        if(slot>=0)doSwap(mc,InventoryActions.menuSlot(slot),template);
        else Notifications.post("AutoSwap: предмет не найден в инвентаре",null);
    }
    @Override protected void onDisable(){
        var mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof morfeov.coliseum.ui.SwapWheelScreen||mc.gui.screen() instanceof morfeov.coliseum.ui.SwapItemPickerScreen)mc.gui.setScreen(null);
    }
    private record InvItem(int slot,ItemStack stack){}
}
