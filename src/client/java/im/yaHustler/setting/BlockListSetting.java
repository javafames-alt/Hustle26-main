package im.yaHustler.setting;

public final class BlockListSetting extends Setting<String> {
    public BlockListSetting(String name) { super(name,"");visibleWhen(()->false); }
}
