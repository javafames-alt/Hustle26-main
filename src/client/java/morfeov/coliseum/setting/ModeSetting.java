package morfeov.coliseum.setting;

import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Unknown mode: " + defaultValue);
    }
    public List<String> modes() { return modes; }
    public boolean is(String mode) { return value.equals(mode); }
    @Override
    public void setSilently(String next) { if (modes.contains(next)) value = next; }
}
