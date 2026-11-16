package im.yaHustler.hud;

import im.yaHustler.setting.NumberSetting;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.ui.Draw;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Staff extends HudElement {
    private static final int MIN_WIDTH=84, HEADER=18, ROW=15;
    private static final float NAME_SIZE=6.75f, BADGE_SIZE=6.25f;
    private static float badgeWidth(String text){return FigmaFont.mediumWidth(text,BADGE_SIZE/11);}
    private static final String[] MARKERS={"admin","moder","helper","support","curator","owner","developer","staff","админ","модер","хелпер","куратор"};
    private record Entry(String name,String role,int color,int ping){}
    private final List<Entry> entries=new ArrayList<>();private int width,height;
    private Object connection;
    private long nextRefresh;
    public Staff(NumberSetting x,NumberSetting y){super(x,y);}
    protected void measure(Minecraft mc,boolean editing){
        long now=System.nanoTime();
        if(width>0 && connection==mc.getConnection() && now<nextRefresh){size(width,height);return;}
        connection=mc.getConnection();nextRefresh=now+250_000_000L;
        entries.clear();width=MIN_WIDTH;
        if(mc.getConnection()!=null)for(var p:mc.getConnection().getOnlinePlayers()){
            String name=p.getProfile().name(),display=p.getTabListDisplayName()==null?name:p.getTabListDisplayName().getString();
            String lower=(display+" "+name).toLowerCase(Locale.ROOT);
            if(!staffName(lower))continue;
            String role="";int color=HudStyle.accent();
            if(lower.contains("admin")||lower.contains("админ")){role="ADMIN";color=0xffff5050;}
            else if(lower.contains("moder")||lower.contains("модер")){role="MOD";color=0xff5ab4ff;}
            else if(lower.contains("helper")||lower.contains("хелпер")){role="HELPER";color=0xff5ae68c;}
            else if(lower.contains("curator")||lower.contains("owner")||lower.contains("куратор")){role="CURATOR";color=0xffffbe3c;}
            entries.add(new Entry(name,role,color,p.getLatency()));
            width=Math.max(width,(int)Math.ceil(26+HudStyle.width(name,NAME_SIZE)+badgeWidth(p.getLatency()+"ms")
                +(role.isEmpty()?0:badgeWidth(role)+5)));
        }
        entries.sort(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER));
        height=HEADER+(entries.isEmpty()?0:5+entries.size()*ROW);size(width,height);
    }
    private static boolean staffName(String name){
        for(String marker:MARKERS)if(name.contains(marker))return true;
        return false;
    }
    protected void draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y){
        HudStyle.panel(g,x,y,width,height);HudStyle.header(g,x,y,width,"Staff","staff",!entries.isEmpty());
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);float cy=y+28+i*ROW,cx=x+7;
            if(!e.role.isEmpty()){HudStyle.text(g,e.role,cx,cy,e.color,BADGE_SIZE,true);cx+=badgeWidth(e.role)+5;}
            HudStyle.text(g,e.name,cx,cy,HudStyle.TEXT,NAME_SIZE,false);
            int ping=e.ping<80?0xff5bd584:e.ping<160?0xfff5be46:0xffff5a5a;
            float pingX=x+width-7-badgeWidth(e.ping+"ms");
            Draw.circle(g,pingX-4,cy,1,ping);
            HudStyle.text(g,e.ping+"ms",pingX,cy,HudStyle.TEXT,BADGE_SIZE,true);
        }
    }
}
