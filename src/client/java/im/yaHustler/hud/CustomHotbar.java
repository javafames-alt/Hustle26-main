package im.yaHustler.hud;

import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceTheme;
import im.yaHustler.ui.GuiOpacity;
import im.yaHustler.font.MsdfPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Port of CustomHotbarRenderer: 182x22 glass and a sliding 19px selection. */
public final class CustomHotbar {
    private static final AnimatedValue SELECTED=new AnimatedValue();
    private static final Identifier MASK=Identifier.fromNamespaceAndPath("hustle263","textures/gui/round.png");
    private CustomHotbar(){}
    public static void draw(GuiGraphicsExtractor g,Minecraft mc){
        if(mc.player==null||mc.gui.hud.isHidden()||mc.player.isSpectator())return;
        int x=g.guiWidth()/2-91,y=g.guiHeight()-25;
        HudStyle.panel(g,x,y,182,22,1,6);
        float selected=SELECTED.update(mc.player.getInventory().getSelectedSlot()*20,.055f,System.nanoTime());
        Draw.roundedF(g,x+1.5f+selected,y+1.5f,19,19,5,InterfaceTheme.alpha(HudStyle.accent(),.12f));
        for(int i=0;i<9;i++){var stack=mc.player.getInventory().getItem(i);if(!stack.isEmpty()){g.item(stack,x+3+i*20,y+3);g.itemDecorations(mc.font,stack,x+3+i*20,y+3);}}
        g.pose().pushMatrix();
        g.pose().translate(x+1.5f+selected,y+1.5f);
        g.blit(MsdfPipeline.slotOutline(),MASK,0,0,0,0,19,19,128,128,128,128,
                GuiOpacity.color(InterfaceTheme.alpha(HudStyle.accent(),.95f)));
        g.pose().popMatrix();
        var offhand=mc.player.getOffhandItem();if(!offhand.isEmpty()){HudStyle.panel(g,x-24,y,22,22,1,6);g.item(offhand,x-21,y+3);g.itemDecorations(mc.font,offhand,x-21,y+3);}
    }
}
