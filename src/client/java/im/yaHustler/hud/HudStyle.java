package im.yaHustler.hud;

import im.yaHustler.Hustle26;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.impl.render.Hud;
import im.yaHustler.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudStyle {
    static final int PANEL = 0xff171816, FIELD = 0x28101412, TEXT = 0xfff1f3ef, MUTED = 0xffa8afa9;
    static Hud options() { return Hustle26.MODULES.get(Hud.class); }
    static int panelColor() { Hud o = options(); return InterfaceTheme.tint(PANEL, o != null && o.themeBlur.enabled(), .08f); }
    static int fieldColor() { return FIELD; }
    static int accent() { return FigmaGuiRenderer.accent(); }
    static int textColor() { return TEXT; }
    static int mutedColor() { return MUTED; }
    static boolean blur() { Hud o = options(); return o != null && o.blur.enabled(); }
    public static int panelFill(boolean blurred, float alpha) {
        Hud o = options();
        return panelFill(blurred, alpha, o != null && o.themeBlur.enabled());
    }
    public static int panelFill(boolean blurred, float alpha, boolean themeTint) {
        Hud o = options();
        float opacity = o == null ? .85f : o.opacity.floatValue() / 100;
        return InterfaceTheme.alpha(InterfaceTheme.tint(PANEL, themeTint, .08f), alpha * opacity * (blurred ? .68f : 1));
    }
    public static float blurStrength() { Hud o = options(); return o == null ? 12 : o.blurRadius.floatValue(); }
    public static float blurTint() { Hud o = options(); return o == null || o.themeBlur.enabled() ? .12f : 0; }
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) { panel(g, x, y, w, h, 1); }
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) { panel(g, x, y, w, h, alpha, 5.5f); }
    static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, float radius) {
        boolean blurred = blur();
        if (blurred) HudBackdrop.add(g, x, y, w, h, radius);
        int fill = panelFill(blurred, alpha);
        int rgb = accent() & 0xffffff;
        float glowAlpha = (fill >>> 24) / 255f;
        Draw.roundedF(g, x - 2, y - 2, w + 4, h + 4, radius + 2,
            InterfaceTheme.alpha(0x10000000 | rgb, glowAlpha));
        Draw.roundedF(g, x - 1, y - 1, w + 2, h + 2, radius + 1,
            InterfaceTheme.alpha(0x20000000 | rgb, glowAlpha));
        Draw.roundedF(g, x, y, w, h, radius, fill);
        Draw.roundedOutline(g, x, y, w, h, radius, 0.5f, InterfaceTheme.alpha(0x18ffffff, glowAlpha));
    }
    static void header(GuiGraphicsExtractor g, int x, int y, int w, String title, String icon, boolean rows) {
        boolean icons = options() == null || options().listIcons.enabled();
        if (icons) InterfaceAssets.icon(g, "hud/" + icon, x + 6, y + 5, 8, accent());
        text(g, title, x + (icons ? 19 : 7), y + 9, TEXT, 7.5f, false);
        if (rows) Draw.fillF(g, x + 6, y + 18, w - 12, .5f, 0x12ffffff);
    }
    static void row(GuiGraphicsExtractor g, float x, float y, float w) {
        if (!blur()) Draw.roundedF(g, x + 4, y, w - 8, 13, 3, FIELD);
    }
    static void text(GuiGraphicsExtractor g, String value, float x, float midY, int color, float size, boolean bold) {
        float scale = size / 11, ty = midY - FigmaFont.mediumHeight(scale) / 2;
        if (bold) FigmaFont.drawMedium(g, value, x, ty, color, scale); else FigmaFont.draw(g, value, x, ty, color, scale);
    }
    static float width(String text, float size) { return FigmaFont.width(text, size / 11); }
    static void pill(GuiGraphicsExtractor g, String text, float right, float cy) {
        float tw = width(text, 6.25f), pw = tw + 7;
        Draw.roundedF(g, right - pw, cy - 4.75f, pw, 9.5f, 3, 0x32101412);
        text(g, text, right - pw + 3.5f, cy, accent(), 6.25f, false);
    }
}
