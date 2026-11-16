package morfeov.coliseum.render.coliseimvisual;

import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.setting.Setting;
import net.minecraft.world.entity.player.Player;

/** Native lifecycle/configuration adapter for the visual modules. */
public abstract class PortedModule extends Module implements MediaSessionHost {
    protected PortedModule(String name, String description, Category category) { super(); }
    public final boolean is() { return enabled(); }
    protected final void addSettings(Object... settings) {
        for (Object value : settings) add(value instanceof TimeSetting time ? time.setting : (Setting<?>)value);
    }
    public void onVisualEnable() {}
    public void onVisualDisable() {}
    @Override protected final void onEnable() { onVisualEnable(); }
    @Override protected final void onDisable() { onVisualDisable(); }
    public void readLegacyConfig(com.google.gson.JsonObject legacy) {}
    public static boolean isFriend(Player player) { return mc.player != null && player != mc.player && mc.player.isAlliedTo(player); }
}
