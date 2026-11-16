package morfeov.coliseum.setting;

public final class TextSetting extends Setting<String> {
    public TextSetting(String name,String initial){super(name,initial);}
    public String getValue(){return get();}
    @Override public void setSilently(String next){value=next==null?"":next.strip().substring(0,Math.min(100,next.strip().length()));}
}
