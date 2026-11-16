package morfeov.coliseum.ui;

import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.module.impl.combat.AutoSwap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Выбирает полный ItemStack из инвентаря без перемещения предметов. */
public final class SwapItemPickerScreen extends Screen {
    private final AutoSwap module;
    private final int index;
    public SwapItemPickerScreen(AutoSwap module,int index){super(Component.literal("Выбор предмета колеса"));this.module=module;this.index=index;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(minecraft.player==null||!module.enabled()){minecraft.gui.setScreen(null);return;}
        int left=width/2-110,top=height/2-70;
        Draw.rounded(g,left-10,top-30,236,160,10,0xf018211c);
        FigmaFont.draw(g,"Выберите предмет для сектора "+(index+1),left,top-20,0xffffffff,.75f);
        for(int i=0;i<36;i++){
            int slot=i<27?i+9:i-27,x=left+(i%9)*24,y=top+(i/9)*24;
            Draw.rounded(g,x,y,22,22,4,Draw.inside(mx,my,x,y,22,22)?0xff4a6857:0xff28362e);
            var stack=minecraft.player.getInventory().getItem(slot);
            if(!stack.isEmpty())g.item(stack,x+3,y+3);
        }
        FigmaFont.draw(g,"Esc — закрыть",left,top+108,0xffc0c8c2,.65f);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=0||minecraft.player==null)return true;
        int left=width/2-110,top=height/2-70;
        for(int i=0;i<36;i++)if(Draw.inside(event.x(),event.y(),left+(i%9)*24,top+(i/9)*24,22,22)){
            var stack=minecraft.player.getInventory().getItem(i<27?i+9:i-27);
            if(!stack.isEmpty()){module.wheel[index].capture(stack);minecraft.gui.setScreen(null);}
            return true;
        }
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
