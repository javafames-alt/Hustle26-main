package im.yaHustler.ui;

import im.yaHustler.util.ColorUtil;

/** Surface tokens from Coliseum1.21.11's MenuScreen and HustleHud. */
public final class InterfaceTheme {
    private InterfaceTheme() {}
    public static int tint(int color, boolean tinted, float amount) {
        return tinted ? ColorUtil.alpha(ColorUtil.lerp(color, FigmaGuiRenderer.accent(), amount), color >>> 24) : color;
    }
    public static int menu(boolean blur, boolean tinted, float opacity) {
        int base = blur ? 0x90121614 : 0xff121614;
        return alpha(tint(base, blur && tinted, .035f), opacity);
    }
    public static int card(boolean blur, boolean tinted, boolean hover) {
        int base = blur ? (hover ? 0x651a211c : 0x4b101712) : (hover ? 0xff252c27 : 0xff1a211c);
        return tint(base, blur && tinted, .035f);
    }
    public static int alpha(int color, float amount) {
        return Math.round((color >>> 24) * Math.clamp(amount, 0, 1)) << 24 | color & 0xffffff;
    }
}
