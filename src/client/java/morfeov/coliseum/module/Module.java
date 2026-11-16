package morfeov.coliseum.module;

import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module {
    private final ModuleInfo info;
    private boolean enabled;
    private boolean favorite;
    private int key = -1;
    private String keyName = "";
    private final String searchName, searchDescription;
    boolean keyHeld;
    private Runnable changed = () -> {};
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module() {
        this(null);
    }
    protected Module(ModuleInfo metadata) {
        info = metadata == null ? getClass().getAnnotation(ModuleInfo.class) : metadata;
        if (info == null) throw new IllegalStateException("Missing @ModuleInfo: " + getClass().getName());
        searchName = info.name().toLowerCase(java.util.Locale.ROOT);
        searchDescription = info.description().toLowerCase(java.util.Locale.ROOT);
    }

    public final String id() { return info.id(); }
    public final String name() { return info.name(); }
    public final String description() { return info.description(); }
    public final Category category() { return info.category(); }
    public final boolean enabled() { return enabled; }
    public final boolean favorite() { return favorite; }
    public final void toggle() { setEnabled(!enabled); }
    public final void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
        changed.run();
    }
    public final void setFavorite(boolean value) {
        if (favorite == value) return;
        favorite = value;
        changed.run();
    }
    public final int key() { return key; }
    public final boolean hasKey() { return key >= 0 || key >= -108 && key <= -101; }
    public final void setKey(int value) {
        if (key == value) return;
        key = value;
        keyName = resolveKeyName(value);
        changed.run();
    }
    public final String keyName() { return keyName; }
    final boolean matches(String needle) {
        return needle.isEmpty() || searchName.contains(needle) || searchDescription.contains(needle);
    }
    private static String resolveKeyName(int key) {
        if (key >= -108 && key <= -101) return "MOUSE" + morfeov.coliseum.util.ActionMouseButtons.number(-100 - key);
        if (key < 0) return "";
        if (key >= 'A' && key <= 'Z' || key >= '0' && key <= '9') return String.valueOf((char) key);
        String name = InputConstants.Type.KEYBOARD.getOrCreate(key).getName();
        if (name.startsWith("key.keyboard.")) name = name.substring("key.keyboard.".length());
        return name.replace('.', ' ').toUpperCase(java.util.Locale.ROOT);
    }
    final void onChanged(Runnable listener) { changed = listener; }
    protected final <S extends Setting<?>> S add(S setting) {
        setting.onChanged(() -> changed.run());
        settings.add(setting);
        return setting;
    }
    public final List<Setting<?>> settings() { return settings; }
    protected void onEnable() {}
    protected void onDisable() {}
    public void tick(Minecraft client) {}
}
