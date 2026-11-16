package im.yaHustler.port.setting;
public final class BooleanSetting extends im.yaHustler.setting.BooleanSetting {
    public BooleanSetting(String name, boolean value) { super(name,value); }
    public boolean isValue() { return enabled(); }
}
