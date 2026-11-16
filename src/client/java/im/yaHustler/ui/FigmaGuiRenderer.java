package im.yaHustler.ui;

import im.yaHustler.font.Fonts;
import im.yaHustler.font.Icons;
import im.yaHustler.module.Category;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Native GUI primitives for the ClickGUI at Figma nodes 123:32–216. */
public final class FigmaGuiRenderer {
    public static final int ACCENT = 0xff4a57e9;
    public static final int HEADER = 0xb3263158;
    public static final int PANEL = 0xd0181f3d;
    public static final int GLASS = 0x12060a22;
    public static final int FIELD = 0xc4080c20;
    public static final int MUTED = 0xff858ba2;
    public static final int ACTIVE_TEXT = 0xff7189e8;
    private static final Identifier MODULE_GLOW = Identifier.fromNamespaceAndPath(
        "hustle263", "textures/gui/figma/module_glow.png");
    private FigmaGuiRenderer() {}
    public static int accent() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_ACCENT); }
    public static int secondary() {
        int color = im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.ACCENT_2);
        return color == 0xff6f93ff ? Draw.lerp(accent(), 0xffffffff, .18f) : color;
    }
    public static int panelColor() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_PANEL); }
    public static int headerColor() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_HEADER); }
    public static int fieldColor() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_FIELD); }
    public static int textColor() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_TEXT); }
    public static int mutedColor() { return im.yaHustler.module.impl.themes.Theme.color(im.yaHustler.module.impl.themes.ThemeColor.GUI_MUTED); }
    public static int activeText() { return Draw.lerp(accent(), 0xffffffff, .22f); }

    public static void moduleGlow(GuiGraphicsExtractor g, int x, int y, int w, int h, float amount) {
        if (amount < .005f) return;
        Draw.rounded(g, x, y, w, h, 8, Math.round(30 * amount) << 24 | (Draw.lerp(0xff000000, accent(), .4f) & 0xffffff));
        // Nine slices preserve the soft rounded inset light at every card height.
        int corner = Math.min(8, Math.min(w, h) / 2);
        int[] source = {0, 32, 96, 128};
        int[] dx = {x, x + corner, x + w - corner, x + w};
        int[] dy = {y, y + corner, y + h - corner, y + h};
        int tint = Math.round(130 * amount) << 24 | (accent() & 0xffffff);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            if (dx[col + 1] <= dx[col] || dy[row + 1] <= dy[row]) continue;
            im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.alphaMask(), MODULE_GLOW, dx[col], dy[row], source[col], source[row],
                dx[col + 1] - dx[col], dy[row + 1] - dy[row],
                source[col + 1] - source[col], source[row + 1] - source[row], 128, 128, tint);
        }
    }

    public static void settingsMark(GuiGraphicsExtractor g, float x, float y, int color) {
        for (int i = 0; i < 3; i++) {
            int left = Math.round(x), top = Math.round(y) + i * 2;
            g.fill(left, top, left + 8, top + 1, color);
        }
    }

    public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color, boolean active) {
        Draw.rounded(g, x, y, w, h, radius, color);
        if (active) {
            // Soft inset blue light, matching the expanded-module effect in 123:88.
            for (int i = 0; i < 4; i++) {
                int alpha = 18 - i * 4;
                Draw.roundedF(g, x + 1 + i, y + 1 + i, w - 2 - i * 2, 1, .5f,
                    alpha << 24 | 0x3e5cf2);
            }
        }
    }

    public static void icon(GuiGraphicsExtractor g, Category category, float x, float y, float size, int color) {
        String asset = switch (category) {
            case COMBAT -> "combat";
            case MOVEMENT -> "movement";
            case PLAYER -> "player";
            case RENDER -> "render";
            case MISC -> "misc";
            default -> null;
        };
        if (asset == null) {
            Fonts.drawIconCrisp(g, category.icon, x, y, color, size / 10f);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 20f, size / 20f);
        im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.crisp(),
            Identifier.fromNamespaceAndPath("hustle263", "textures/gui/figma/" + asset + "_sdf.png"),
            0, 0, 0, 0, 20, 20, 20, 20, color);
        g.pose().popMatrix();
    }

    public static void searchIcon(GuiGraphicsExtractor g, float x, float y, float size, int color) {
        color = GuiOpacity.color(color);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 20f, size / 20f);
        im.yaHustler.ui.GuiTextures.blit(g, im.yaHustler.font.MsdfPipeline.searchIcon(),
            Identifier.fromNamespaceAndPath("hustle263", "textures/gui/round.png"),
            0, 0, 0, 0, 20, 20, 20, 20, color);
        g.pose().popMatrix();
    }

    public static void toggle(GuiGraphicsExtractor g, float x, float y, float progress, boolean hover) {
        int off = 0xff24262c, on = Draw.lerp(0xff343640, accent(), .45f);
        Draw.roundedF(g, x, y, 16, 10, 5, Draw.lerp(off, on, progress));
        Draw.circle(g, x + 4.75f + 6.5f * progress, y + 5, 3.5f, 0xffffffff);
    }
}
