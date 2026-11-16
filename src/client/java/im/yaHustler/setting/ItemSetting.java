package im.yaHustler.setting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/** Saved stack components let AutoSwap distinguish named spheres and custom heads. */
public final class ItemSetting extends Setting<String> {
    public ItemSetting(String name){super(name,"");}
    public ItemStack stack(){
        var mc=Minecraft.getInstance();
        if(value.isBlank()||mc.level==null)return ItemStack.EMPTY;
        try{return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,mc.level.registryAccess()),JsonParser.parseString(value)).result().orElse(ItemStack.EMPTY);}
        catch(RuntimeException e){return ItemStack.EMPTY;}
    }
    public void capture(ItemStack stack){
        var mc=Minecraft.getInstance();
        if(stack.isEmpty()||mc.level==null){set("");return;}
        set(ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,mc.level.registryAccess()),stack.copyWithCount(1)).getOrThrow().toString());
    }
    public boolean matches(ItemStack other){return ItemStack.isSameItemSameComponents(stack(),other);}
    public String label(){var stack=stack();return stack.isEmpty()?"Взять из руки":stack.getHoverName().getString();}
}
