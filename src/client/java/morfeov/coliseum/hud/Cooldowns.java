package morfeov.coliseum.hud;

import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.mixin.CooldownInstanceAccessor;
import morfeov.coliseum.mixin.ItemCooldownsAccessor;
import morfeov.coliseum.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Uses actual cooldown ticks, including groups whose item has left the inventory. */
public final class Cooldowns extends HudElement {
    private static final int HEADER = 18, ROW = 15, PAD = 6;
    private static final float SCALE = 6.75f / 11;
    private final List<Entry> entries = new ArrayList<>();
    private int width, height;
    private boolean icons, showTime;
    public Cooldowns(NumberSetting x, NumberSetting y) { super(x, y); }
    @Override
    protected void measure(Minecraft client, boolean editing) {
        var o = HudStyle.options();
        icons = o == null || o.listIcons.enabled();
        showTime = o == null || o.cooldownTime.enabled();
        entries.clear();
        var cooldowns = client.player.getCooldowns();
        var access = (ItemCooldownsAccessor) cooldowns;
        for (var row : access.coliseum$cooldowns().entrySet()) {
            var timer = (CooldownInstanceAccessor) row.getValue();
            int remaining = timer.coliseum$endTime() - access.coliseum$tickCount();
            if (remaining <= 0) continue;
            ItemStack item = ItemStack.EMPTY;
            for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = client.player.getInventory().getItem(slot);
                if (!candidate.isEmpty() && cooldowns.getCooldownGroup(candidate).equals(row.getKey())) {
                    item = candidate; break;
                }
            }
            String name = item.isEmpty() ? row.getKey().getPath().replace('_', ' ') : item.getHoverName().getString();
            String time = String.format(java.util.Locale.ROOT, "%.1fs", remaining / 20f);
            float progress = Math.clamp((float) remaining / Math.max(1, timer.coliseum$endTime() - timer.coliseum$startTime()), 0, 1);
            entries.add(new Entry(row.getKey(), item, name, time, progress));
        }
        entries.sort(Comparator.comparing(e -> e.id.toString()));
        if (entries.isEmpty() && !editing && (o == null || !o.emptyLists.enabled())) return;
        width = 84;
        for (Entry e : entries) width = Math.max(width, (int) Math.ceil(PAD * 2 + (icons ? 14 : 0) + FigmaFont.width(e.name, SCALE) + (showTime ? 10 + FigmaFont.width(e.time, SCALE) : 0)));
        height = entries.isEmpty() ? HEADER : HEADER + entries.size() * ROW + 4;
        size(width, height);
    }
    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int x, int y) {
        HudStyle.panel(g, x, y, width, height);
        HudStyle.header(g, x, y, width, "Cooldowns", "clock", !entries.isEmpty());
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int ry = y + HEADER + 3 + i * ROW;
            if (icons && !e.item.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(x + PAD, ry);
                g.pose().scale(.625f, .625f);
                g.item(e.item, 0, 0);
                g.pose().popMatrix();
            }
            FigmaFont.draw(g, e.name, x + PAD + (icons ? 14 : 0), ry + 2, HudStyle.textColor(), SCALE);
            if (showTime) FigmaFont.draw(g, e.time, x + width - PAD - FigmaFont.width(e.time, SCALE), ry + 2, HudStyle.accent(), SCALE);
        }
    }
    private record Entry(Identifier id, ItemStack item, String name, String time, float progress) {}
}
