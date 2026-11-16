package im.yaHustler.hud;

import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HotKeys extends HudElement {
    private final ModuleRegistry registry;
    private final List<Module> bound = new ArrayList<>();
    private int width, height;
    private boolean showKeys;
    private long measuredRevision = Long.MIN_VALUE;
    public HotKeys(ModuleRegistry registry, NumberSetting x, NumberSetting y) { super(x,y); this.registry=registry; }
    protected void measure(Minecraft mc, boolean editing) {
        var o = HudStyle.options();
        boolean keys = o == null || o.keybindKeys.enabled();
        long revision = registry.revision();
        if (measuredRevision != revision || showKeys != keys) {
            measuredRevision = revision; showKeys = keys;
            bound.clear(); width = 84;
            for (Module module : registry.all()) if (module.hasKey() && module.enabled()) {
                bound.add(module);
                width = Math.max(width, (int)Math.ceil(14 + HudStyle.width(module.name(), 6.75f)
                    + (showKeys ? 16 + HudStyle.width(module.keyName(), 6.25f) : 0)));
            }
        }
        if (bound.isEmpty() && !editing && (o == null || !o.emptyLists.enabled())) return;
        height = 18 + (bound.isEmpty() ? 0 : 5 + bound.size() * 15);
        size(width, height);
    }
    protected void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        HudStyle.panel(g, x, y, width, height);
        HudStyle.header(g, x, y, width, "KeyBinds", "keybinds", !bound.isEmpty());
        for (int i = 0; i < bound.size(); i++) {
            Module m = bound.get(i);
            float cy = y + 28 + i * 15;
            HudStyle.text(g, m.name(), x + 7, cy, HudStyle.TEXT, 6.75f, false);
            if (showKeys) HudStyle.pill(g, m.keyName(), x + width - 7, cy);
        }
    }
}
