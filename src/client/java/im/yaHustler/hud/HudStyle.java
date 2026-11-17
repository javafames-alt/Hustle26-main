package im.yaHustler.hud;

import im.yaHustler.Hustle26;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.impl.render.Hud;
import im.yaHustler.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudStyle {
    static final int PANEL = 0xff101521, FIELD = 0xff1b2639, TEXT = 0xfff0f4fc, MUTED = 0xffa2b1c9;
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
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) { panel(g, x, y, w, h, alpha, 8f); }
    static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, float radius) {
        boolean blurred = blur();
        if (blurred) HudBackdrop.add(g, x, y, w, h, radius);
        int fill = panelFill(blurred, alpha);
        Draw.roundedF(g, x, y, w, h, radius, fill);
        Draw.roundedOutline(g, x, y, w, h, radius, .65f, InterfaceTheme.alpha(0x24ffffff, alpha));
    }
    static void header(GuiGraphicsExtractor g, int x, int y, int w, String title, String icon, boolean rows) {
        boolean icons = options() == null || options().listIcons.enabled();
        if (icons) {
            Draw.rounded(g, x + 5, y + 3, 13, 13, 4, InterfaceTheme.alpha(accent(), .18f));
            InterfaceAssets.centeredIcon(g, "hud/" + icon, x + 11.5f, y + 9.5f, 7, accent());
        }
        text(g, title, x + (icons ? 22 : 7), y + 9.5f, TEXT, 7.6f, true);
        if (rows) Draw.fillF(g, x + 6, y + 18, w - 12, .5f, 0x1affffff);
    }
    static void row(GuiGraphicsExtractor g, float x, float y, float w) {
        Draw.roundedF(g, x + 4, y, w - 8, 13, 4, 0x981b2639);
    }
    static void text(GuiGraphicsExtractor g, String value, float x, float midY, int color, float size, boolean bold) {
        float scale = size / 11, ty = midY - FigmaFont.mediumHeight(scale) / 2;
        if (bold) FigmaFont.drawMedium(g, value, x, ty, color, scale); else FigmaFont.draw(g, value, x, ty, color, scale);
    }
    static float width(String text, float size) { return FigmaFont.width(text, size / 11); }
    static void pill(GuiGraphicsExtractor g, String text, float right, float cy) {
        float tw = width(text, 6.25f), pw = tw + 7;
        Draw.roundedF(g, right - pw, cy - 4.75f, pw, 9.5f, 3, 0xff263752);
        text(g, text, right - pw + 3.5f, cy, accent(), 6.25f, false);
    }

    static void arc(GuiGraphicsExtractor g, float cx, float cy, float radius, float progress, int color) {
        final int segments = 32;
        float turn = (float)(2 * Math.PI / segments);
        float remaining = Math.clamp(progress, 0, 1) * segments;
        if (remaining <= 0) return;
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx, cy);
            for (; remaining > 0; remaining -= 1) {
                Draw.fillF(g, -.05f, -radius - .75f, radius * turn * Math.min(1, remaining) + .10f, 1.5f, color);
                g.pose().rotate(turn);
            }
        } finally { g.pose().popMatrix(); }
    }
}
