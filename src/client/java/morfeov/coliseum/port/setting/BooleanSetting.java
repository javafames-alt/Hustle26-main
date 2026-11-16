package morfeov.coliseum.port.setting;
public final class BooleanSetting extends morfeov.coliseum.setting.BooleanSetting {
    public BooleanSetting(String name, boolean value) { super(name,value); }
    public boolean isValue() { return enabled(); }
}
