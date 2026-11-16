package im.yaHustler.hud;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.module.impl.render.ClickGui;
import im.yaHustler.module.impl.render.Hud;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.module.impl.themes.ThemeColor;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Width-sorted module names with a staggered theme gradient and animated rows. */
public final class ArrayListHud extends HudElement {
    private static final float PAD = 2.5f, GLOW = 3;

    private static final class Row {
        final Module module;
        final AnimatedValue animation = new AnimatedValue();
        float width;

        Row(Module module) {
            this.module = module;
            animation.setValue(0);
        }
    }

    private final ModuleRegistry registry;
    private final List<Row> rows = new ArrayList<>();
    private EnumSet<Category> categories = EnumSet.noneOf(Category.class);
    private long measuredRevision = Long.MIN_VALUE;
    private int width;
    private float fontSize, rowHeight, gradientOffset;

    public ArrayListHud(ModuleRegistry registry, NumberSetting x, NumberSetting y) {
        super(x, y);
        this.registry = registry;
    }

    @Override
    protected void measure(Minecraft client, boolean editing) {
        Hud hud = HudStyle.options();
        EnumSet<Category> selected = selectedCategories(hud);
        long revision = registry.revision();
        if (measuredRevision != revision || !categories.equals(selected)) {
            measuredRevision = revision;
            categories = selected;
            rows.removeIf(row -> !keep(row.module));
            for (Module module : registry.all()) {
                if (module.enabled() && keep(module) && rows.stream().noneMatch(row -> row.module == module)) {
                    rows.add(new Row(module));
                }
            }
        }
        fontSize = hud == null ? 8 : hud.arrayListFont.floatValue();
        rowHeight = hud == null ? 10 : hud.arrayListSpacing.floatValue();
        gradientOffset = hud == null ? 10 : hud.arrayListGradient.floatValue();
        float speed = hud == null ? .6f : hud.arrayListSpeed.floatValue() / 100;
        float smoothTime = Math.max(.04f, .45f - .4f * speed);
        long now = System.nanoTime();
        for (Row row : rows) {
            row.animation.update(row.module.enabled() ? 1 : 0, smoothTime, now);
            row.width = textWidth(row.module.name());
        }
        rows.removeIf(row -> !row.module.enabled() && row.animation.getValue() <= .01f);
        rows.sort((a, b) -> Float.compare(b.width, a.width));
        if (rows.isEmpty() && !editing && (hud == null || !hud.emptyLists.enabled())) return;

        float height = 0;
        for (Row row : rows) height += rowHeight * row.animation.getValue();
        width = (int) Math.ceil(rows.isEmpty() ? textWidth("Array List") : rows.getFirst().width);
        size(width, Math.max(1, (int) Math.ceil(rows.isEmpty() ? rowHeight : height)));
    }

    private static EnumSet<Category> selectedCategories(Hud hud) {
        EnumSet<Category> result = EnumSet.noneOf(Category.class);
        if (hud == null || hud.arrayListCombat.enabled()) result.add(Category.COMBAT);
        if (hud == null || hud.arrayListMovement.enabled()) result.add(Category.MOVEMENT);
        if (hud == null || hud.arrayListPlayer.enabled()) result.add(Category.PLAYER);
        if (hud != null && hud.arrayListMisc.enabled()) result.add(Category.MISC);
        if (hud != null && !hud.hideRenderList.enabled()) result.add(Category.RENDER);
        return result;
    }

    private boolean keep(Module module) {
        return categories.contains(module.category()) && module != HudStyle.options()
            && !(module instanceof ClickGui) && !registry.isHidden(module);
    }

    private float textWidth(String name) {
        return PAD * 2 + FigmaFont.mediumWidth(name, fontSize / 11);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int x, int y) {
        Hud hud = HudStyle.options();
        boolean right = hud != null && hud.arrayListSide.is("Right");
        boolean blur = HudStyle.blur();
        float phase = (System.nanoTime() / 4_000_000L % 360) / 360f;
        int primary = HudStyle.accent();
        int secondary = Theme.color(ThemeColor.ACCENT_2);

        if (rows.isEmpty()) {
            drawRow(g, "Array List", x, y, width, rowHeight, primary, 1, blur);
            return;
        }
        float offset = 0;
        for (Row row : rows) {
            float alpha = row.animation.getValue();
            if (alpha <= .01f) continue;
            float height = rowHeight * alpha;
            float rowX = right ? x + width - row.width : x;
            float wave = (float) (0.5 - 0.5 * Math.cos((phase + offset * gradientOffset / (rowHeight * 360)) * Math.TAU));
            int color = Draw.lerp(primary, secondary, wave);
            drawRow(g, row.module.name(), rowX, y + offset, row.width, height, color, alpha, blur);
            offset += height;
        }
    }

    private void drawRow(GuiGraphicsExtractor g, String name, float x, float y, float width, float height,
                         int color, float alpha, boolean blur) {
        Draw.roundedF(g, x - GLOW, y - GLOW, width + 2 * GLOW, height + 2 * GLOW, GLOW,
            InterfaceTheme.alpha(0x16000000 | (color & 0xffffff), alpha));
        if (blur) HudBackdrop.add(g, x, y, width, height, 0);
        Draw.fillF(g, x, y, width, height, HudStyle.panelFill(blur, alpha));
        HudStyle.text(g, name, x + PAD, y + height / 2, InterfaceTheme.alpha(color, alpha), fontSize, true);
    }

    @Override
    protected float shadowAlpha() { return 0; }
}
