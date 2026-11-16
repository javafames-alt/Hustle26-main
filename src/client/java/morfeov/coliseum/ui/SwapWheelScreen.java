package morfeov.coliseum.ui;

import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.module.impl.combat.AutoSwap;
import morfeov.coliseum.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Колесо исходника: держишь клавишу, выбираешь сектор, отпускаешь — свап; ЛКМ по пустому — выбор предмета, ПКМ — очистить. */
public final class SwapWheelScreen extends Screen {
    private final AutoSwap module;
    private final float[] sectorHover=new float[8];
    private int hovered=-1;
    private float openProgress;
    public SwapWheelScreen(AutoSwap module){super(Component.literal("AutoSwap"));this.module=module;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(minecraft.player==null||!module.enabled()){minecraft.gui.setScreen(null);return;}
        openProgress=Math.clamp(openProgress+.12f,0,1);
        float scale=.88f+.12f*openProgress;
        float cx=width*.5f,cy=height*.5f,outerR=76*scale,innerR=42*scale;
        int count=module.slotCount();
        float step=360f/count;
        float dx=mx-cx,dy=my-cy,dist=(float)Math.hypot(dx,dy);
        hovered=-1;
        if(dist>=14&&dist<=outerR+25){
            double angle=Math.toDegrees(Math.atan2(dy,dx))+90;
            if(angle<0)angle+=360;
            hovered=(int)Math.floor(angle/step)%count;
        }
        Draw.roundedF(g,cx-outerR-8,cy-outerR-8,(outerR+8)*2,(outerR+8)*2,outerR+8,ColorUtil.alpha(0x0e0f16,(int)(180*openProgress)));
        int accent=FigmaGuiRenderer.accent();
        for(int i=0;i<count;i++){
            sectorHover[i]+=((i==hovered?1f:0f)-sectorHover[i])*.2f;
            double mid=Math.toRadians(-90+step*(i+.5f));
            float midR=(innerR+outerR)*.5f;
            float ix=cx+(float)Math.cos(mid)*midR,iy=cy+(float)Math.sin(mid)*midR;
            int color=ColorUtil.alpha(Draw.lerp(0x232634,accent,sectorHover[i]),(int)((190+30*sectorHover[i])*openProgress));
            Draw.roundedF(g,ix-16,iy-16,32,32,6,color);
            ItemStack stack=module.wheel[i].stack();
            if(!stack.isEmpty()){
                g.item(stack,(int)(ix-8),(int)(iy-8));
                g.itemDecorations(minecraft.font,stack,(int)(ix-8),(int)(iy-8));
            }else FigmaFont.draw(g,"+",ix-3.5f,iy-4.5f,i==hovered?0xffffffff:0x88ffffff,9f/11);
        }
    }
    @Override public void tick(){
        if(!module.wheelHeld()){
            int slot=hovered;
            minecraft.gui.setScreen(null);
            module.selectWheel(slot);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==1){
            if(hovered>=0)module.wheel[hovered].capture(ItemStack.EMPTY);
            return true;
        }
        if(event.button()==0&&hovered>=0){
            int index=hovered;
            if(module.wheel[index].stack().isEmpty())minecraft.gui.setScreen(new SwapItemPickerScreen(module,index));
            else{minecraft.gui.setScreen(null);module.selectWheel(index);}
        }
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
