package im.yaHustler.hud;

import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public final class TotemIndicator extends HudElement {
    private int count;
    public TotemIndicator(NumberSetting x,NumberSetting y){super(x,y);}
    protected void measure(Minecraft mc,boolean editing){
        count=0;
        for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
            var stack=mc.player.getInventory().getItem(i);
            if(stack.is(Items.TOTEM_OF_UNDYING))count+=stack.getCount();
        }
        if(count>0||editing)size(22,29);
    }
    protected void draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y){
        HudStyle.panel(g,x,y,22,29);
        String value=Integer.toString(count);HudStyle.text(g,value,x+11-HudStyle.width(value,8)/2,y+6,HudStyle.TEXT,8,true);
        g.item(new ItemStack(Items.TOTEM_OF_UNDYING),x+3,y+10);
    }
}
