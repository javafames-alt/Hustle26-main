package morfeov.coliseum.module.impl.misc;

import java.util.*;
import java.util.regex.Pattern;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.*;
import morfeov.coliseum.port.event.*;
import morfeov.coliseum.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Input;

@ModuleInfo(id="rw_helper",name="RWHelper",category=Category.MISC,description="Предметы ReallyWorld, GPS и автоматическая смена сферы")
public final class RWHelper extends PortModule {
    private final KeySetting antiFly=add(new KeySetting("Бинд анти-полёта"));
    private final KeySetting scroll=add(new KeySetting("Бинд свитка"));
    private final KeySetting trap=add(new KeySetting("Бинд трапки"));
    private final BooleanSetting gps=add(new BooleanSetting("Авто-GPS на ивенты",true));
    private final BooleanSetting protectPvp=add(new BooleanSetting("Игнор друзей в /pvp",true));
    private final BooleanSetting orbSwap=add(new BooleanSetting("Авто-свап сферы",false));
    private final BooleanSetting lowerHealth=add(new BooleanSetting("Ниже здоровья",false));
    private final BooleanSetting eating=add(new BooleanSetting("Враг ест",false));
    private final BooleanSetting retreating=add(new BooleanSetting("Враг отступает",false));
    private final BooleanSetting lostTotem=add(new BooleanSetting("Враг потерял тотем",false));
    private final Pattern coordinates=Pattern.compile("x[:\\s]*(-?\\d+).*?z[:\\s]*(-?\\d+)",Pattern.CASE_INSENSITIVE);
    private String lastGps="";
    private int orbSlot=-1,orbTarget=-1,lastHurt,popped=-1;
    private long checked,poppedAt;
    private ItemStack originalOrb=ItemStack.EMPTY;
    private final boolean[] queued=new boolean[3];
    private net.minecraft.client.player.LocalPlayer session;
    @Override public void tick(Minecraft client){
        if(session!=client.player){reset();session=client.player;}
        boolean a=antiFly.pressed(client),s=scroll.pressed(client),t=trap.pressed(client);
        queued[0]|=a;queued[1]|=s;queued[2]|=t;
        if(!scroll.down())queued[1]=false;
        if(client.player==null||client.level==null){reset();return;}
        if(!morfeov.coliseum.util.InventoryActions.ready(client)||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty())return;
        if(client.gui.screen()==null)for(int i=0;i<3;i++)if(queued[i]){int slot=find(i);if(slot<0){queued[i]=false;continue;}if(use(slot,i==0)){if(i!=1)queued[i]=false;break;}}
        var target=CombatHooks.target();
        if(orbSlot>=0){
            boolean damaged=client.player.hurtTime>lastHurt&&target!=null&&target.getId()==orbTarget;
            if(damaged){var source=client.player.getLastDamageSource();damaged=source!=null&&source.getEntity()!=null?source.getEntity().getId()==orbTarget:client.player.distanceTo(target)<=6;}
            if(damaged||!orbSwap.enabled()||target==null||target.getId()!=orbTarget)restore();
        }else if(orbSwap.enabled()&&target!=null&&System.currentTimeMillis()-checked>=1000){checked=System.currentTimeMillis();var current=client.player.getOffhandItem();double damage=damage(current);if(isOrb(current)&&damage>0&&condition(target)){int best=-1;for(int i=0;i<36;i++){var item=client.player.getInventory().getItem(i);if(isOrb(item)&&damage(item)>damage){best=i;damage=damage(item);}}if(best>=0&&acquire()){originalOrb=current.copy();orbTarget=target.getId();orbSlot=best;swapOffhand(best);InvActionUtil.get().release(id());}}}
        lastHurt=client.player.hurtTime;
    }
    private boolean condition(LivingEntity target){return !lowerHealth.enabled()&&!eating.enabled()&&!retreating.enabled()&&!lostTotem.enabled()
        ||lowerHealth.enabled()&&target.getHealth()+target.getAbsorptionAmount()<mc.player.getHealth()+mc.player.getAbsorptionAmount()
        ||eating.enabled()&&target.isUsingItem()&&target.getUseItem().has(DataComponents.FOOD)
        ||retreating.enabled()&&target.position().subtract(mc.player.position()).multiply(1,0,1).dot(target.getDeltaMovement().subtract(mc.player.getDeltaMovement()).multiply(1,0,1))>.01
        ||lostTotem.enabled()&&target.getId()==popped&&System.currentTimeMillis()-poppedAt<3000;}
    private boolean acquire(){return InvActionUtil.get().tryAcquire(id(),50,100);}
    private int menuSlot(int slot){return slot<9?36+slot:slot;}
    private void swapOffhand(int slot){mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,menuSlot(slot),40,ContainerInput.SWAP,mc.player);}
    private void restore(){if(mc.player==null||mc.gameMode==null){reset();return;}if(mc.player.containerMenu!=mc.player.inventoryMenu||!mc.player.inventoryMenu.getCarried().isEmpty())return;if(!ItemStack.isSameItemSameComponents(mc.player.getInventory().getItem(orbSlot),originalOrb)){orbSlot=-1;originalOrb=ItemStack.EMPTY;return;}if(acquire()){swapOffhand(orbSlot);orbSlot=-1;originalOrb=ItemStack.EMPTY;InvActionUtil.get().release(id());}}
    private int find(int kind){Item item=kind==0?Items.FIREWORK_STAR:kind==1?Items.FLOWER_BANNER_PATTERN:Items.HEART_OF_THE_SEA;String name=kind==0?"Анти Полет":kind==1?"Свиток опыта":"Ловушка";for(int i=0;i<36;i++){var stack=mc.player.getInventory().getItem(i);var custom=stack.getCustomName();if(stack.is(item)&&(custom!=null&&custom.getString().equals(name)||kind!=2&&stack.getHoverName().getString().equals(name)))return i;}return -1;}
    private boolean use(int slot,boolean anti){
        if(mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(slot)))return true;
        if(!acquire())return false;int selected=mc.player.getInventory().getSelectedSlot();
        try{
            if(anti){swapOffhand(slot);try{Input input=mc.player.input.keyPresses;if(input.shift())mc.player.connection.send(new ServerboundPlayerInputPacket(shift(input,false)));mc.player.connection.send(new ServerboundPlayerInputPacket(shift(input,true)));if(!input.shift())mc.player.connection.send(new ServerboundPlayerInputPacket(shift(input,false)));}finally{swapOffhand(slot);}}
            else{boolean swapped=slot>=9;if(swapped)mc.gameMode.handleContainerInput(0,slot,selected,ContainerInput.SWAP,mc.player);else mc.player.getInventory().setSelectedSlot(slot);try{((morfeov.coliseum.mixin.CombatGameModeAccessor)mc.gameMode).coliseum$syncSelectedSlot();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);mc.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,false);}finally{if(swapped)mc.gameMode.handleContainerInput(0,slot,selected,ContainerInput.SWAP,mc.player);mc.player.getInventory().setSelectedSlot(selected);((morfeov.coliseum.mixin.CombatGameModeAccessor)mc.gameMode).coliseum$syncSelectedSlot();}}
        }finally{InvActionUtil.get().release(id());}return true;
    }
    private Input shift(Input input,boolean shift){return new Input(input.forward(),input.backward(),input.left(),input.right(),input.jump(),shift,input.sprint());}
    public boolean blocksPvp(int slot){if(!enabled()||!protectPvp.enabled()||!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen)||!screen.getTitle().getString().equals("PVP » Ожидание...")||slot<0||slot>=screen.getMenu().slots.size())return false;return morfeov.coliseum.friend.Friends.contains(screen.getMenu().getSlot(slot).getItem().getHoverName().getString());}
    private static boolean isOrb(ItemStack stack){return stack.is(Items.PLAYER_HEAD)&&stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("шар");}
    private static double damage(ItemStack stack){double[] result={0};stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY).forEach(EquipmentSlot.OFFHAND,(attribute,modifier)->{if(attribute.is(Attributes.ATTACK_DAMAGE)&&modifier.operation()==AttributeModifier.Operation.ADD_VALUE)result[0]+=modifier.amount();});return result[0];}
    @Override public void onPacket(EventPacketReceive event){
        if(mc.level==null||mc.player==null)return;
        if(event.getPacket() instanceof ClientboundEntityEventPacket packet&&packet.getEventId()==35){var target=CombatHooks.target();if(target!=null&&packet.getEntity(mc.level)==target){popped=target.getId();poppedAt=System.currentTimeMillis();}}
        if(gps.enabled()&&event.getPacket() instanceof ClientboundBossEventPacket packet)packet.dispatch(new ClientboundBossEventPacket.Handler(){@Override public void add(UUID id,Component name,float progress,BossEvent.BossBarColor color,BossEvent.BossBarOverlay overlay,boolean darken,boolean music,boolean fog){boss(name);}@Override public void updateName(UUID id,Component name){boss(name);}});
    }
    private void boss(Component name){var matcher=coordinates.matcher(name.getString());if(!matcher.find())return;try{int x=Integer.parseInt(matcher.group(1)),z=Integer.parseInt(matcher.group(2));String key=x+":"+z;if(lastGps.equals(key))return;lastGps=key;morfeov.coliseum.command.GpsMarker.set(x,z);morfeov.coliseum.util.chat.ChatUtil.sendMessage(morfeov.coliseum.command.ClientCommands.prefixed("RWHelper: GPS X: "+x+" Z: "+z));}catch(NumberFormatException ignored){}}
    private void reset(){orbSlot=-1;orbTarget=-1;originalOrb=ItemStack.EMPTY;Arrays.fill(queued,false);popped=-1;poppedAt=0;checked=0;lastHurt=0;lastGps="";}
    @Override protected void onDisable(){if(orbSlot>=0)restore();Arrays.fill(queued,false);}
    public void restoreWhileDisabled(){if(enabled()||orbSlot<0)return;if(session!=mc.player){reset();return;}if(morfeov.coliseum.util.InventoryActions.ready(mc))restore();}
    @Override public void onDisconnect(EventDisconnect event){reset();}
}
