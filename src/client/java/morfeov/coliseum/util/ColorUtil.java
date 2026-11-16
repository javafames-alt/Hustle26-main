package morfeov.coliseum.util;

import java.util.Locale;

public final class ColorUtil {
    private ColorUtil() {}

    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            r |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }
    public static int alpha(int color, int alpha) { return Math.clamp(alpha, 0, 255) << 24 | color & 0xffffff; }
    public static int opaque(int color) { return color | 0xff000000; }
    public static float luminance(int color) {
        return (.2126f * (color >> 16 & 255) + .7152f * (color >> 8 & 255) + .0722f * (color & 255)) / 255f;
    }
    public static String hex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
            color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24);
    }
    public static Integer parse(String text) {
        if (text == null) return null;
        String value = text.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6 && value.length() != 8) return null;
        try {
            long raw = Long.parseLong(value, 16);
            if (value.length() == 6) return (int) (0xff000000L | raw);
            return (int) ((raw & 0xff) << 24 | raw >>> 8);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    public static float[] toHsv(int color) {
        float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float hue = 0;
        if (delta > 0) {
            if (max == r) hue = ((g - b) / delta) % 6;
            else if (max == g) hue = (b - r) / delta + 2;
            else hue = (r - g) / delta + 4;
            hue /= 6;
            if (hue < 0) hue += 1;
        }
        return new float[]{hue, max == 0 ? 0 : delta / max, max};
    }
    public static int fromHsv(float hue, float saturation, float value, int alpha) {
        float h = (hue - (float) Math.floor(hue)) * 6, c = value * saturation;
        float x = c * (1 - Math.abs(h % 2 - 1)), m = value - c;
        float r, g, b;
        switch ((int) h) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return Math.clamp(alpha, 0, 255) << 24 | Math.round((r + m) * 255) << 16
            | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
