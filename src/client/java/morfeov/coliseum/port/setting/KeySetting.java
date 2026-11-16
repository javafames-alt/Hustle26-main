package morfeov.coliseum.port.setting;
public final class KeySetting extends morfeov.coliseum.setting.KeySetting {
    public KeySetting(String name, int key) { super(name); setSilently(key); }
    public int getKeyCode() { return get(); }
}
