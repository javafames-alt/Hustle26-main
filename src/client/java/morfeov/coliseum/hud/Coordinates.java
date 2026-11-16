package morfeov.coliseum.hud;

import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.ui.*;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Coordinates extends HudElement {
    private static final String[] LABELS = {"XYZ", "BPS", "TPS"};
    private InlineHudLayout.Row layout;
    private String coords,bps,tps;private int width;private double px,pz,speed;private long last;private Object world;
    public Coordinates(NumberSetting x,NumberSetting y){super(x,y);}
    protected void measure(Minecraft mc,boolean editing){
        long now=System.nanoTime();
        if(world!=mc.level){world=mc.level;px=mc.player.getX();pz=mc.player.getZ();last=now;speed=0;ServerTelemetry.reset();}
        if(now-last>=50_000_000L){speed=Math.hypot(mc.player.getX()-px,mc.player.getZ()-pz)*1e9/(now-last);px=mc.player.getX();pz=mc.player.getZ();last=now;}
        coords=(int)Math.floor(mc.player.getX())+" "+(int)Math.floor(mc.player.getY())+" "+(int)Math.floor(mc.player.getZ());
        bps=String.format(Locale.ROOT,"%.1f",speed);double value=mc.hasSingleplayerServer()?20:ServerTelemetry.tps();tps=Double.isNaN(value)?"—":String.format(Locale.ROOT,"%.1f",value);
        float[] labels = new float[3], values = new float[3];
        String[] text = {coords, bps, tps};
        for (int i = 0; i < 3; i++) {
            labels[i] = morfeov.coliseum.font.FigmaFont.mediumWidth(LABELS[i], 5.76f / 11);
            values[i] = HudStyle.width(text[i], 6.6f);
        }
        layout = InlineHudLayout.measure(labels, values, new boolean[]{false, true, true});
        width = layout.width(); size(width,20);
    }
    protected void draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y){
        HudStyle.panel(g,x,y,width,20,1,5.4f);
        String[] values={coords,bps,tps};
        for(int i=0;i<3;i++){
            var metric = layout.metrics()[i];
            if(i>0){Draw.fillF(g,x+metric.divider(),y+4,.6f,12,0x0effffff);InterfaceAssets.icon(g,i==1?"hud/bps":"hud/tps",x+metric.icon(),y+6.4f,7.2f,HudStyle.accent());}
            HudStyle.text(g,LABELS[i],x+metric.label(),y+10,HudStyle.accent(),5.76f,true);
            HudStyle.text(g,values[i],x+metric.value(),y+10,HudStyle.TEXT,6.6f,false);
        }
    }
}
