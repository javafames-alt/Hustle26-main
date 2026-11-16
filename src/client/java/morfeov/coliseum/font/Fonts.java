package morfeov.coliseum.font;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class Fonts {
    private static final AtlasFont REGULAR = new AtlasFont("medium");
    private static final AtlasFont MEDIUM = REGULAR;
    public static final AtlasFont INTER_BOLD = new AtlasFont("inter_bold");
    public static final AtlasFont SF_MEDIUM = new AtlasFont("sf_pro_display_medium");
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("coliseum", "textures/font/icons.png");
    private static final float[][] ICON_OFFSETS = {
        {3.5f, -5.5f}, {0, 2}, {0, -4}, {0, 0}, {-4, 0}, {0, 0}, {0, 0}, {.5f, 4.5f},
        {-3.5f, -3.5f}, {-3, 0}, {.5f, .5f}, {0, -4}, {1, 1}, {2, -.5f}, {0, 0}, {.5f, .5f}};
    private static final float[] GLOW_X = {1, .7f, 0, -.7f, -1, -.7f, 0, .7f};
    private static final float[] GLOW_Y = {0, .7f, 1, .7f, 0, -.7f, -1, -.7f};
    private static final int FIT_CACHE_LIMIT = 512;
    private static final Map<FitKey, String> FITTED = new HashMap<>();
    private Fonts() {}
    public static String clean(String text) {
        if (text.indexOf('\u00a7') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') { i++; continue; }
            out.append(c);
        }
        return out.toString();
    }
    public static float width(String text, float scale) { return REGULAR.width(clean(text)) * scale; }
    public static void draw(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) {
        REGULAR.draw(g, clean(text), x, y, color, scale);
    }
    public static void drawMedium(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) {
        MEDIUM.draw(g, clean(text), x, y, color, scale);
    }
    public static void drawIcon(GuiGraphicsExtractor g, String glyph, float x, float y, int color) {
        drawIcon(g, glyph, x, y, color, 1f);
    }
    public static void drawIcon(GuiGraphicsExtractor g, String glyph, float x, float y, int color, float scale) {
        drawIcon(g, glyph, x, y, color, scale, false);
    }
    public static void drawIconCrisp(GuiGraphicsExtractor g, String glyph, float x, float y, int color, float scale) {
        drawIcon(g, glyph, x, y, color, scale, true);
    }
    private static void drawIcon(GuiGraphicsExtractor g, String glyph, float x, float y, int color, float scale, boolean crisp) {
        if (glyph.isEmpty()) return;
        int cell = glyph.codePointAt(0) - 0xE000;
        if (cell < 0 || cell >= Icons.COUNT) return;
        g.pose().pushMatrix();
        float unit = 10f * scale / 128f;
        g.pose().translate(x + ICON_OFFSETS[cell][0] * unit, y + ICON_OFFSETS[cell][1] * unit);
        g.pose().scale(unit, unit);
        morfeov.coliseum.ui.GuiTextures.blit(g, crisp ? MsdfPipeline.crisp() : MsdfPipeline.get(), ICONS, 0, 0, cell * 128, 0, 128, 128, 128, 128, Icons.COUNT * 128, 128, color);
        g.pose().popMatrix();
    }
    public static void drawIconGlow(GuiGraphicsExtractor g, String glyph, float x, float y, int color, float scale) {
        int rgb = color & 0xffffff;
        float alpha = (color >>> 24) / 255f;
        int inner = Math.round(alpha * 40) << 24 | rgb;
        int outer = Math.round(alpha * 18) << 24 | rgb;
        for (int i = 0; i < GLOW_X.length; i++) {
            drawIcon(g, glyph, x + GLOW_X[i] * 1.6f, y + GLOW_Y[i] * 1.6f, outer, scale);
            drawIcon(g, glyph, x + GLOW_X[i] * .8f, y + GLOW_Y[i] * .8f, inner, scale);
        }
        drawIcon(g, glyph, x, y, color, scale);
    }
    public static String fit(String text, int maxWidth, float scale) {
        FitKey key = new FitKey(text, maxWidth, scale);
        String cached = FITTED.get(key);
        if (cached != null) return cached;
        String fitted = shorten(clean(text), maxWidth, scale);
        if (FITTED.size() >= FIT_CACHE_LIMIT) FITTED.clear();
        FITTED.put(key, fitted);
        return fitted;
    }
    private static String shorten(String text, int maxWidth, float scale) {
        if (width(text, scale) <= maxWidth) return text;
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, text.offsetByCodePoints(0, mid)) + "…";
            if (width(candidate, scale) <= maxWidth) low = mid; else high = mid - 1;
        }
        return low == 0 ? "" : text.substring(0, text.offsetByCodePoints(0, low)) + "…";
    }
    private record FitKey(String text, int maxWidth, float scale) {}
}
