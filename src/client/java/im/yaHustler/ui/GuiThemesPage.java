package im.yaHustler.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Theme gallery in the HUD style: the current palette on top and a grid of palette cards below. */
final class GuiThemesPage {
    record Preset(String name, int primary, int secondary) {}
    private static final List<Preset> PRESETS = List.of(
        new Preset("Hustle 26.3 Purple", 0xff9D4EDD, 0xff7B2CBF),
        new Preset("Cyber Neon", 0xff00F0FF, 0xffFF007F),
        new Preset("Nordic Frost", 0xff38EF7D, 0xff11998E),
        new Preset("Solar Flare", 0xffFF416C, 0xffFF4B2B),
        new Preset("Midnight OLED", 0xff94A3B8, 0xff475569),
        new Preset("Crimson Abyss", 0xffFF1361, 0xffE60000),
        new Preset("Emerald Matrix", 0xff00F260, 0xff0575E6),
        new Preset("Sakura Dream", 0xffFF758C, 0xffFF7EB3),
        new Preset("Amethyst Glow", 0xff8E2DE2, 0xff4A00E0),
        new Preset("Toxic Acid", 0xffDCE35B, 0xff45B649),
        new Preset("Electric Blue", 0xff00C6FF, 0xff0072FF),
        new Preset("Sunset Pulse", 0xffFA709A, 0xffFEE140));
    private static final int GAP = 8, HERO_H = 56, BAND = 28, CARD = BAND + 25, BUTTON_W = 74, BUTTON_H = 20, PILL = 0x32101412;
    private record Hit(Preset preset, int x, int y, int w, int h) {}
    private final List<Hit> hits = new ArrayList<>();
    private final UiAnimationEngine animation = new UiAnimationEngine();
    private int x, y, w, h, scroll, total, buttonX, buttonY;
    private float visibleScroll;
    private long lastFrame;
    private String lastQuery = "";
    private int card = 0x0bffffff, cardHover = 0x13ffffff, radius = 5, text = 0xfff1f3ef, muted = 0xffa8afa9;

    /** Surface tokens shared with the module cards of the ClickGUI. */
    GuiThemesPage style(int card, int cardHover, int radius, int text, int muted) {
        this.card = card; this.cardHover = cardHover;
        this.radius = Math.max(0, radius); this.text = text; this.muted = muted;
        return this;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, String query, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (!lastQuery.equals(query)) { scroll = 0; visibleScroll = 0; lastQuery = query; }
        animation.beginFrame(Minecraft.getInstance().getFps());
        long now = System.nanoTime();
        float frame = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        int accent = FigmaGuiRenderer.accent(), second = FigmaGuiRenderer.secondary();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<Preset> shown = needle.isEmpty() ? PRESETS
            : PRESETS.stream().filter(p -> p.name.toLowerCase(Locale.ROOT).contains(needle)).toList();
        int cols = w >= 330 ? 4 : w >= 240 ? 3 : 2;
        float cw = (w - GAP * (cols - 1f)) / cols;
        int rows = (shown.size() + cols - 1) / cols, gridTop = HERO_H + 25;
        total = gridTop + Math.max(0, rows * (CARD + GAP) - GAP) + 4;
        int max = Math.max(0, total - h);
        scroll = Math.clamp(scroll, 0, max);
        visibleScroll += (scroll - visibleScroll) * (1 - (float) Math.exp(-18 * frame));
        visibleScroll = Math.clamp(visibleScroll, 0f, (float) max);
        if (Math.abs(scroll - visibleScroll) < .05f) visibleScroll = scroll;
        float top = y - visibleScroll;
        hits.clear();
        drawHero(g, top, accent, second, mx, my);
        float sectionY = top + HERO_H + 13, countScale = 6.25f / 11;
        InterfaceAssets.centeredIcon(g, "theme", x + 5, sectionY, 8, accent);
        FigmaFont.drawMedium(g, "Палитры", x + 13, sectionY - FigmaFont.mediumHeight(7f / 11) / 2, text, 7f / 11);
        String count = shown.size() + " " + plural(shown.size(), "палитра", "палитры", "палитр");
        float countW = FigmaFont.width(count, countScale) + 7;
        Draw.roundedF(g, x + w - countW, sectionY - 4.75f, countW, 9.5f, 3, PILL);
        FigmaFont.draw(g, count, x + w - countW + 3.5f, sectionY - FigmaFont.height(countScale) / 2, accent, countScale);
        if (shown.isEmpty()) FigmaFont.draw(g, "Темы не найдены", x + 1, top + gridTop + 4, ColorUtil.alpha(muted, 210), 6.5f / 11);
        String current = Theme.paletteName();
        for (int i = 0; i < shown.size(); i++) {
            Preset preset = shown.get(i);
            float left = x + i % cols * (cw + GAP);
            int bx = Math.round(left), bw = Math.round(left + cw) - bx;
            float by = top + gridTop + i / cols * (CARD + GAP);
            if (by + CARD <= y - 4 || by >= y + h + 4) continue;
            int hitY = Math.round(by);
            boolean hot = Draw.inside(mx, my, x, y, w, h) && Draw.inside(mx, my, bx, hitY, bw, CARD);
            drawPreset(g, preset, bx, by, bw, selected(preset, current, accent, second),
                animation.animate01("theme:hover:" + preset.name, hot ? 1 : 0, 22, 0), accent);
            hits.add(new Hit(preset, bx, hitY, bw, CARD));
        }
        if (total > h) {
            float thumb = Math.max(18, h * (float) h / total);
            float thumbY = y + (h - thumb) * visibleScroll / Math.max(1f, total - h);
            Draw.roundedF(g, x + w - 1.25f, thumbY, 1.25f, thumb, .625f, ColorUtil.alpha(accent, 120));
        }
    }

    private void drawHero(GuiGraphicsExtractor g, float top, int accent, int second, int mx, int my) {
        Draw.roundedF(g, x, top, w, HERO_H, radius + 1, card);
        float size = 34, sx = x + 11, sy = top + (HERO_H - size) / 2;
        Draw.roundedGradient(g, sx, sy, size, size, 8, ColorUtil.opaque(accent), ColorUtil.opaque(second), true);
        float tx = sx + size + 10;
        buttonX = x + w - 11 - BUTTON_W;
        buttonY = Math.round(top + (HERO_H - BUTTON_H) / 2f);
        FigmaFont.draw(g, "ТЕКУЩАЯ ТЕМА", tx, top + 12, ColorUtil.alpha(muted, 190), 5f / 11);
        FigmaFont.drawMedium(g, FigmaFont.fitMedium(displayName(), Math.max(0, (int) (buttonX - 10 - tx)), 8.4f / 11),
            tx, top + 20, text, 8.4f / 11);
        float chipX = tx, chipScale = 5.6f / 11;
        for (int color : new int[]{accent, second}) {
            String label = hex(color);
            float chipW = 13 + FigmaFont.width(label, chipScale) + 4;
            Draw.roundedF(g, chipX, top + 34, chipW, 10, 3, PILL);
            Draw.circle(g, chipX + 6, top + 39, 2.5f, ColorUtil.opaque(color));
            FigmaFont.draw(g, label, chipX + 11, top + 39 - FigmaFont.height(chipScale) / 2, 0xffd6dbd7, chipScale);
            chipX += chipW + 4;
        }
        boolean hot = Draw.inside(mx, my, x, y, w, h) && Draw.inside(mx, my, buttonX, buttonY, BUTTON_W, BUTTON_H);
        float hover = animation.animate01("theme:custom", hot ? 1 : 0, 22, 0);
        Draw.roundedF(g, buttonX, buttonY, BUTTON_W, BUTTON_H, 4, ColorUtil.lerp(0x3c101412, ColorUtil.alpha(accent, 0x50), .5f * hover));
        String label = "Свой цвет";
        float labelScale = 6.4f / 11, groupX = buttonX + (BUTTON_W - 13 - FigmaFont.mediumWidth(label, labelScale)) / 2;
        InterfaceAssets.centeredIcon(g, "theme", groupX + 4, buttonY + BUTTON_H / 2f, 7.5f, accent);
        FigmaFont.drawMedium(g, label, groupX + 13, buttonY + (BUTTON_H - FigmaFont.mediumHeight(labelScale)) / 2, text, labelScale);
    }

    private void drawPreset(GuiGraphicsExtractor g, Preset preset, int bx, float by, int bw, boolean selected, float hover, int accent) {
        Draw.roundedF(g, bx, by, bw, CARD, radius, ColorUtil.lerp(card, cardHover, hover));
        Draw.roundedGradient(g, bx, by, bw, BAND, radius, preset.primary, preset.secondary, false);
        if (selected) {
            Draw.circle(g, bx + bw - 10, by + 10, 6, 0xffffffff);
            InterfaceAssets.centeredIcon(g, "checkmark", bx + bw - 10, by + 10.3f, 6.5f,
                ColorUtil.luminance(preset.primary) > .62f ? ColorUtil.lerp(preset.primary, 0xff000000, .55f) : preset.primary);
        }
        float nameScale = 6.4f / 11, subScale = 4.9f / 11;
        FigmaFont.drawMedium(g, FigmaFont.fitMedium(preset.name, bw - 14, nameScale), bx + 7, by + BAND + 6.5f, text, nameScale);
        if (selected) FigmaFont.draw(g, "Активна", bx + 7, by + BAND + 15.5f, accent, 5f / 11);
        else FigmaFont.draw(g, FigmaFont.fit(hex(preset.primary) + "  " + hex(preset.secondary), bw - 14, subScale),
            bx + 7, by + BAND + 15.5f, ColorUtil.alpha(muted, 200), subScale);
        if (selected) Draw.smoothShape(g, bx, by, bw, CARD, radius, true, ColorUtil.alpha(accent, 220));
    }

    private static boolean selected(Preset preset, String current, int accent, int second) {
        if (preset.name.equals(current)) return true;
        return ((preset.primary ^ accent) & 0xffffff) == 0 && ((preset.secondary ^ second) & 0xffffff) == 0;
    }
    private static String displayName() {
        String name = Theme.paletteName();
        return name == null || name.isBlank() || name.equals("Custom Theme") ? "Своя тема" : name;
    }
    private static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xffffff); }
    private static String plural(int n, String one, String few, String many) {
        int mod10 = n % 10, mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return one;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }

    boolean click(double mx, double my, Runnable custom) {
        if (!Draw.inside(mx, my, x, y, w, h)) return false;
        if (Draw.inside(mx, my, buttonX, buttonY, BUTTON_W, BUTTON_H)) { custom.run(); return true; }
        for (Hit hit : hits) if (Draw.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
            Theme.applyGuiPalette(hit.preset.name, hit.preset.primary, hit.preset.secondary, true);
            return true;
        }
        return false;
    }
    void scroll(double amount) { scroll = Math.clamp(scroll - (int) (amount * 24), 0, Math.max(0, total - h)); }
}
