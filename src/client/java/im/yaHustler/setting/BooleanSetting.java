package im.yaHustler.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) { super(name, defaultValue); }
    public boolean enabled() { return value; }
    public void toggle() { set(!value); }
}
