package im.yaHustler.port.setting;
public final class KeySetting extends im.yaHustler.setting.KeySetting {
    public KeySetting(String name, int key) { super(name); setSilently(key); }
    public int getKeyCode() { return get(); }
}
