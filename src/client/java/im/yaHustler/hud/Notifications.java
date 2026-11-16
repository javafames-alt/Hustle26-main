package im.yaHustler.hud;

import im.yaHustler.Hustle26;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.Module;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Single-line status pills, with wrapping for longer messages. */
public final class Notifications extends HudElement {
    private static final int CARD_HEIGHT=18, GAP=4, TEXT_X=35, MAX_WIDTH=260;
    private static final float FONT=7, LINE_HEIGHT=9;
    private static final float RADIUS=5.5f;
    private record Notice(String text,Boolean enabled,long born,long end){}
    private record Card(Notice notice,List<String> lines,int width,int height){}
    private static final Deque<Notice> QUEUE=new ArrayDeque<>();
    private final Map<Module,Boolean> states=new IdentityHashMap<>();
    private final Set<String> warned=new HashSet<>();
    private final List<Card> shown=new ArrayList<>();
    private Object level;
    public Notifications(NumberSetting x,NumberSetting y){super(x,y);}
    public static void post(String text,Boolean enabled){long now=System.currentTimeMillis();QUEUE.addLast(new Notice(text,enabled,now,now+3500));while(QUEUE.size()>6)QUEUE.removeFirst();}
    public void tick(Minecraft mc){
        if(mc.player==null){states.clear();warned.clear();QUEUE.clear();level=null;return;}
        if(level!=mc.level){level=mc.level;states.clear();warned.clear();QUEUE.clear();}
        for(Module m:Hustle26.MODULES.all()){
            Boolean previous=states.put(m,m.enabled());
            if(previous!=null&&previous!=m.enabled()&&!m.id().equals("clickgui"))post(m.name(),m.enabled());
        }
        Set<String> current=new HashSet<>();
        if(HudStyle.options().potionWarnings.enabled())for(var effect:mc.player.getActiveEffects()){
            String key=effect.getEffect().getRegisteredName();current.add(key);
            if(effect.getDuration()>200)warned.remove(key);
            else if(effect.getDuration()>0&&!effect.isInfiniteDuration()&&warned.add(key))post(effect.getEffect().value().getDisplayName().getString()+" закончится через "+Math.max(1,effect.getDuration()/20)+"с",null);
        }
        warned.retainAll(current);
    }
    protected void measure(Minecraft mc,boolean editing){
        long now=System.currentTimeMillis();QUEUE.removeIf(n->now>=n.end);shown.clear();
        for(var n:QUEUE)shown.add(card(n));
        if(shown.isEmpty()&&editing)shown.add(card(new Notice("AttackAura",true,now-500,now+3500)));
        if(shown.isEmpty())return;
        int width=0,height=-GAP;
        for(var card:shown){width=Math.max(width,card.width);height+=card.height+GAP;}
        size(width,height);
    }
    private static float textWidth(String text){return FigmaFont.mediumWidth(text,FONT/11);}
    private static String status(Notice n){return n.enabled==null?"":n.enabled?"включена":"выключена";}
    private static Card card(Notice n){
        String text=n.enabled==null?n.text:"Функция "+n.text+" "+status(n);
        List<String> lines=TextWrap.lines(text,MAX_WIDTH-TEXT_X-9,Notifications::textWidth);
        int width=TEXT_X+9;
        for(String line:lines)width=Math.max(width,TEXT_X+9+(int)Math.ceil(textWidth(line)));
        return new Card(n,lines,Math.max(100,width),CARD_HEIGHT+Math.max(0,lines.size()-1)*(int)LINE_HEIGHT);
    }
    @Override protected float radius(){return RADIUS;}
    // Each animated pill draws its own shadow instead of a rectangle behind the whole stack.
    @Override protected float shadowAlpha(){return 0;}
    protected void draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y){
        long now=System.currentTimeMillis();
        int ry=y;
        for(Card card:shown){
            Notice n=card.notice;
            float time=Math.clamp((now-n.born)/220f,0,1),enter=1-(float)Math.pow(1-time,3);
            float fade=Math.clamp((n.end-now)/250f,0,1),a=enter*fade;
            int accent=Boolean.FALSE.equals(n.enabled)?0xffff4f73:HudStyle.accent();
            g.pose().pushMatrix();g.pose().translate((1-enter)*8,(1-enter)*3);
            float previous=GuiOpacity.push(a);
            try {
                var options=HudStyle.options();
                if(options!=null&&options.shadows.enabled())Draw.shadow(g,x,ry,card.width,card.height,RADIUS,
                    options.shadowStrength.floatValue()/100,options.shadowSize.floatValue());
                HudStyle.panel(g,x,ry,card.width,card.height,1,RADIUS);
                float cy=ry+card.height/2f;
                if(n.enabled==null)InterfaceAssets.centeredIcon(g,"info",x+16,cy,9,accent);
                else{
                    Draw.roundedF(g,x+7,cy-5,18,10,5,accent);
                    float enabled=n.enabled?enter:1-enter;
                    Draw.circle(g,x+12+8*enabled,cy,3.5f,0xfff8faf9);
                }
                Draw.fillF(g,x+30,cy-5,.5f,10,0x24ffffff);
                float lineY=cy-(card.lines.size()-1)*LINE_HEIGHT/2;
                String status=status(n);
                for(String line:card.lines){
                    boolean colored=!status.isEmpty()&&line.endsWith(status);
                    String prefix=colored?line.substring(0,line.length()-status.length()):line;
                    HudStyle.text(g,prefix,x+TEXT_X,lineY,HudStyle.TEXT,FONT,true);
                    if(colored)HudStyle.text(g,status,x+TEXT_X+textWidth(prefix),lineY,accent,FONT,true);
                    lineY+=LINE_HEIGHT;
                }
            } finally {GuiOpacity.restore(previous);g.pose().popMatrix();}
            ry+=card.height+GAP;
        }
    }
}
