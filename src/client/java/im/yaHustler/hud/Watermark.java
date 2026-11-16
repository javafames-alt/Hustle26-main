package im.yaHustler.hud;

import im.yaHustler.font.FigmaFont;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact brand and live performance/network metrics. */
public final class Watermark extends HudElement {
    private static final float FONT = 7.5f;
    private static final float LABEL_FONT = 4.75f;
    private String fps, ping;
    private int width;
    private boolean logo, showFps, showPing;
    public Watermark(NumberSetting x, NumberSetting y) { super(x, y); }
    protected void measure(Minecraft mc, boolean editing) {
        var o = HudStyle.options();
        logo = o == null || o.watermarkLogo.enabled();
        showFps = o == null || o.watermarkFps.enabled();
        showPing = o == null || o.watermarkPing.enabled();
        fps = Integer.toString(im.yaHustler.module.impl.render.Optimization.displayedFps(mc));
        var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        ping = Integer.toString(info == null ? 0 : info.getLatency());
        float w = 14 + (logo ? 15 : 0) + brandWidth();
        if (showFps) w += metricWidth(fps, "fps");
        if (showPing) w += metricWidth(ping, "ping");
        width = (int)Math.ceil(w);
        size(width, 18);
    }
    private static float metricWidth(String value, String label) {
        return 23 + HudStyle.width(value, FONT) + 1.25f + HudStyle.width(label, LABEL_FONT);
    }
    private static float brandWidth() { return FigmaFont.mediumWidth("Hustle 26.3", FONT / 11); }
    protected void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        HudStyle.panel(g, x, y, width, 18, 1, 6);
        float cx = x + 7;
        if (logo) { InterfaceAssets.icon(g, "brand", cx, y + 3, 12, HudStyle.accent()); cx += 15; }
        HudStyle.text(g, "Hustle 26.3", cx, y + 9, HudStyle.TEXT, FONT, true);
        cx += brandWidth();
        if (showFps) cx = metric(g, cx, y, "hud/watermark_fps", fps, "fps");
        if (showPing) metric(g, cx, y, "hud/watermark_ping", ping, "ping");
    }
    private static float metric(GuiGraphicsExtractor g, float x, int y, String icon, String value, String label) {
        Draw.fillF(g, x + 5.5f, y + 4.5f, .5f, 9, 0x26ffffff);
        // Match the visible symbols: the supplied PNGs have different transparent margins.
        if (icon.equals("hud/watermark_ping")) InterfaceAssets.icon(g, icon, x + 10.5f, y + 2, 13.5f, HudStyle.accent());
        else InterfaceAssets.icon(g, icon, x + 11, y + 4, 10, HudStyle.accent());
        HudStyle.text(g, value, x + 23, y + 9, HudStyle.TEXT, FONT, false);
        float labelX = x + 24.25f + HudStyle.width(value, FONT);
        float labelY = y + 9 + (FigmaFont.mediumHeight(FONT / 11) - FigmaFont.mediumHeight(LABEL_FONT / 11)) / 2;
        HudStyle.text(g, label, labelX, labelY, HudStyle.MUTED, LABEL_FONT, false);
        return x + metricWidth(value, label);
    }
    protected float radius() { return 6; }
}
