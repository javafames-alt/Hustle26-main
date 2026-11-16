package im.yaHustler.font;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native SDF/MSDF rendering of the donor Inter faces for ClickGUI and Interface. */
public final class FigmaFont {
    private static final AtlasFont MEDIUM = new AtlasFont("hustle263_semi", true);
    private static final AtlasFont SEMIBOLD = new AtlasFont("hustle263_bold", true);
    private static final AtlasFont REGULAR = MEDIUM;
    private FigmaFont() {}
    public static void drawRegular(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) { REGULAR.draw(g, text, x, y, color, scale); }
    public static String fitRegular(String text, int width, float scale) { return fitFace(REGULAR, text, width, scale); }
    public static float width(String text, float scale) { return MEDIUM.width(text) * scale; }
    public static float mediumWidth(String text, float scale) { return SEMIBOLD.width(text) * scale; }
    public static float mediumHeight(float scale) { return SEMIBOLD.cap() * AtlasFont.BASE * scale; }
    public static float height(float scale) { return MEDIUM.cap() * AtlasFont.BASE * scale; }
    public static void draw(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) {
        MEDIUM.draw(g, text, x, y, color, scale);
    }
    public static void drawMedium(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) {
        SEMIBOLD.draw(g, text, x, y, color, scale);
    }
    public static String fit(String text, int maxWidth, float scale) {
        return fitFace(MEDIUM, text, maxWidth, scale);
    }
    public static String fitMedium(String text, int maxWidth, float scale) {
        return fitFace(SEMIBOLD, text, maxWidth, scale);
    }
    private static String fitFace(AtlasFont face, String text, int maxWidth, float scale) {
        if (face.width(text) * scale <= maxWidth) return text;
        if (maxWidth <= face.width("…") * scale) return "";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, text.offsetByCodePoints(0, mid)) + "…";
            if (face.width(candidate) * scale <= maxWidth) low = mid; else high = mid - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + "…";
    }
}
