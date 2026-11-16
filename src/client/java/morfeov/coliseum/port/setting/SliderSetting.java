package morfeov.coliseum.port.setting;
public final class SliderSetting extends morfeov.coliseum.setting.NumberSetting {
    public SliderSetting(String name, float value, float min, float max, float step) { super(name,value,min,max,step); }
    public SliderSetting(String name, float value, float min, float max, float step, String suffix) { this(name,value,min,max,step); }
    public Float getValue() { return floatValue(); }
}
