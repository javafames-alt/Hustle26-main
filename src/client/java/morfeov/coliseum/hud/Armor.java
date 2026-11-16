package morfeov.coliseum.hud;

import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;

public final class Armor extends HudElement {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    public Armor(NumberSetting x,NumberSetting y){super(x,y);}
    protected void measure(Minecraft mc,boolean editing){size(HudStyle.options().armorOrientation.is("Vertical")?22:86,HudStyle.options().armorOrientation.is("Vertical")?75:25);}
    protected void draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y){
        var o=HudStyle.options(); boolean vertical=o.armorOrientation.is("Vertical");
        HudStyle.panel(g,x,y,vertical?22:86,vertical?75:25);
        for(int i=0;i<4;i++){
            var stack=mc.player.getItemBySlot(SLOTS[o.armorReverse.enabled()?3-i:i]);
            float ix=x+(vertical?3:5+i*20), iy=y+(vertical?2.5f+i*17.5f:2.5f);
            if(stack.isEmpty())continue;
            g.pose().pushMatrix();g.pose().translate(ix,iy);g.pose().scale(vertical?.8f:1,vertical?.8f:1);g.item(stack,0,0);g.pose().popMatrix();
            if(o.armorPercentage.enabled()&&stack.isDamageableItem()){
                float fraction=1-stack.getDamageValue()/(float)Math.max(1,stack.getMaxDamage());String text=Math.round(fraction*100)+"%";
                HudStyle.text(g,text,ix+(16-HudStyle.width(text,4.5f))/2,iy+(vertical?14:19),fraction<.2f?0xffff6b6b:HudStyle.TEXT,4.5f,true);
            }
        }
    }
}
