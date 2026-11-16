package im.yaHustler.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import im.yaHustler.font.Fonts;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.module.impl.themes.ThemeColor;
import im.yaHustler.util.ColorUtil;

public final class Draw {
    private Draw() {}
    private static final net.minecraft.resources.Identifier ROUND =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("hustle263", "textures/gui/round.png");
    private static final float SDF_HALF = 64f, SDF_RADIUS = 54f;
    public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        roundedF(g, x, y, w, h, Math.min(radius, Math.min(w, h) / 2f), color);
    }
    public static void roundedF(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2f);
        if (r <= .01f) { fillF(g, x, y, w, h, color); return; }
        fillF(g, x + r, y, w - 2 * r, h, color);
        fillF(g, x, y + r, r, h - 2 * r, color);
        fillF(g, x + w - r, y + r, r, h - 2 * r, color);
        corner(g, x + r, y + r, r, -1, -1, color);
        corner(g, x + w - r, y + r, r, 1, -1, color);
        corner(g, x + r, y + h - r, r, -1, 1, color);
        corner(g, x + w - r, y + h - r, r, 1, 1, color);
    }
    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        roundedF(g, cx - r, cy - r, r * 2, r * 2, r, color);
    }
    public static void ring(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
        if (!Float.isFinite(radius) || radius <= 0 || (color >>> 24) == 0) return;
        float diameter = radius * 2 + 4;
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx - diameter / 2, cy - diameter / 2);
            g.pose().scale(diameter / 64, diameter / 64);
            GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.fovRing(), ROUND, 0, 0, 0, 0, 64, 64, 1, 1, 1, 1, color);
        } finally { g.pose().popMatrix(); }
    }
    public static void fillF(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        color = GuiOpacity.color(color);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(w / 64f, h / 64f);
        im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.get(), ROUND, 0, 0, 60, 60, 64, 64, 2, 2, 128, 128, color);
        g.pose().popMatrix();
    }
    private static void corner(GuiGraphicsExtractor g, float cx, float cy, float r, int sx, int sy, int color) {
        color = GuiOpacity.color(color);
        float extent = r * SDF_HALF / SDF_RADIUS;
        g.pose().pushMatrix();
        g.pose().translate(sx < 0 ? cx - extent : cx, sy < 0 ? cy - extent : cy);
        g.pose().scale(extent / 64f, extent / 64f);
        im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.get(), ROUND, 0, 0, sx < 0 ? 0 : 64, sy < 0 ? 0 : 64,
            64, 64, 64, 64, 128, 128, color);
        g.pose().popMatrix();
    }
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius) {
        roundedF(g, x - 3, y - 2.5f, w + 6, h + 6, radius + 3, 0x05000000);
        roundedF(g, x - 1.5f, y - 1, w + 3, h + 3, radius + 1.5f, 0x0a000000);
    }
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float strength, float spread) {
        strength = Math.clamp(strength, 0, 1);
        if (strength == 0) return;
        for (int layer = 4; layer >= 1; layer--) {
            float pad = spread * layer / 4f;
            int alpha = Math.round((10 + (4 - layer) * 3f) * strength);
            roundedF(g, x - pad, y - pad + spread * .2f, w + pad * 2, h + pad * 2, radius + pad, alpha << 24);
        }
    }
    public static void depth(GuiGraphicsExtractor g, int x, int y, int w, int h, float radius) {
        int inset = (int) Math.ceil(Math.min(radius, Math.min(w, h) / 2f) * .6f);
        if (w - inset * 2 <= 0) return;
        g.fillGradient(x + inset, y, x + w - inset, y + Math.min(8, h), 0x09ffffff, 0x00ffffff);
    }
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int side) {
        for (int i = 6; i >= 1; i--) rounded(g, x - i, y - i + 3, w + i * 2, h + i * 2, 9 + i, 0x0c000000);
        int glow = Theme.color(ThemeColor.GLOW);
        rounded(g, x, y, w, h, 9, Theme.color(ThemeColor.OUTLINE));
        rounded(g, x + 1, y + 1, w - 2, h - 2, 8, Theme.color(ThemeColor.BACKGROUND));
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h / 2, glow, glow & 0xffffff);
        g.fillGradient(x + 1, y + 1, x + side, y + h - 1, 0x07ffffff, 0x03ffffff);
        g.disableScissor();
    }
    public static void horizontalGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y + h);
        pose.rotate((float) (-Math.PI / 2));
        pose.scale(h, w);
        g.fillGradient(0, 0, 1, 1, GuiOpacity.color(left), GuiOpacity.color(right));
        pose.popMatrix();
    }
    /**
     * Horizontal gradient inside a rounded rectangle. The corner caps take the end colours and no piece
     * overlaps another, so translucent washes stay even while the GUI fades in or out.
     */
    public static void roundedGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float r,
                                       int left, int right, boolean roundBottom) {
        if (w <= 0 || h <= 0 || (left >>> 24) == 0 && (right >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        if (r <= .01f) { horizontalGradient(g, x, y, w, h, left, right); return; }
        float side = h - r - (roundBottom ? r : 0);
        if ((left >>> 24) != 0) {
            corner(g, x + r, y + r, r, -1, -1, left);
            if (roundBottom) corner(g, x + r, y + h - r, r, -1, 1, left);
            if (side > 0) fillF(g, x, y + r, r, side, left);
        }
        if ((right >>> 24) != 0) {
            corner(g, x + w - r, y + r, r, 1, -1, right);
            if (roundBottom) corner(g, x + w - r, y + h - r, r, 1, 1, right);
            if (side > 0) fillF(g, x + w - r, y + r, r, side, right);
        }
        horizontalGradient(g, x + r, y, w - 2 * r, h, left, right);
    }
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        color = GuiOpacity.color(color);
        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
    }
    public static void keyboard(GuiGraphicsExtractor g, float x, float y, int color) {
        roundedF(g, x, y, 8, 5.5f, 1.2f, color);
        int hole = ColorUtil.lerp(ColorUtil.opaque(Theme.color(ThemeColor.BACKGROUND)), 0xffffffff, .045f);
        for (int i = 0; i < 3; i++) fillF(g, x + 1.5f + i * 2f, y + 1.3f, 1f, 1f, hole);
        fillF(g, x + 2f, y + 3.4f, 4f, .9f, hole);
    }
    /** Soft theme tint over the existing backdrop blur, without a border. */
    public static void searchSurface(GuiGraphicsExtractor g,float x,float y,float w,float h,int color){
        float padding=h*3/16f;
        g.pose().pushMatrix();g.pose().translate(x-padding,y-padding);g.pose().scale((w+padding*2)/128,(h+padding*2)/128);
        im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.searchSurface(),ROUND,0,0,0,0,128,128,128,128,128,128,GuiOpacity.color(color));
        g.pose().popMatrix();
    }
    public static void roundedOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thickness, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        fillF(g, x + r, y - thickness / 2, w - 2 * r, thickness, color);
        fillF(g, x + r, y + h - thickness / 2, w - 2 * r, thickness, color);
        fillF(g, x - thickness / 2, y + r, thickness, h - 2 * r, color);
        fillF(g, x + w - thickness / 2, y + r, thickness, h - 2 * r, color);
        for (int corner = 0; corner < 4; corner++) {
            float cx = x + (corner == 0 || corner == 3 ? w - r : r);
            float cy = y + (corner < 2 ? h - r : r);
            for (int i = 0; i < 12; i++) {
                double a = (corner * 90 + i * 7.5) * Math.PI / 180, b = a + Math.PI / 24;
                float ax = cx + (float)Math.cos(a) * r, ay = cy + (float)Math.sin(a) * r;
                float bx = cx + (float)Math.cos(b) * r, by = cy + (float)Math.sin(b) * r;
                g.pose().pushMatrix();
                g.pose().translate(ax, ay);
                g.pose().rotate((float)Math.atan2(by - ay, bx - ax));
                fillF(g, -.05f, -thickness / 2, (float)Math.hypot(bx - ax, by - ay) + .1f, thickness, color);
                g.pose().popMatrix();
            }
        }
    }
    public static void smoothShape(GuiGraphicsExtractor g, float x, float y, float w, int h, float radius, boolean outline, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x - 1, y - 1);
            pose.scale((w + 2) / 64, (h + 2) / 64f);
            int radiusCode = Math.round(radius * 2);
            int heightCode = h + (outline ? 128 : 0);
            GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.smoothShape(), ROUND,
                0, 0, radiusCode * 64, heightCode * 64, 64, 64, 64, 64, 64, 64, GuiOpacity.color(color));
        } finally {
            pose.popMatrix();
        }
    }
    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
    public static void toggle(GuiGraphicsExtractor g, float x, float y, float t, boolean hover) {
        int off = Theme.color(ThemeColor.TOGGLE_OFF), on = Theme.color(ThemeColor.TOGGLE_ON);
        int track = lerp(hover ? ColorUtil.lerp(off, 0xffffffff, .03f) : off, on, t);
        int knobOn = ColorUtil.luminance(on) > .75f ? 0xff1a1f2b : 0xffffffff;
        roundedF(g, x, y, 20, 11, 5.5f, track);
        circle(g, x + 5.5f + 9 * t, y + 5.5f, 3.9f, lerp(Theme.color(ThemeColor.TEXT_OFF), knobOn, t));
    }
    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            r |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }
}
