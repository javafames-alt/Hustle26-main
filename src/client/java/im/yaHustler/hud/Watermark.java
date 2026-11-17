package im.yaHustler.hud;

import im.yaHustler.font.FigmaFont;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceAssets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

public final class Watermark extends HudElement {
    private static final float FONT = 7.2f;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int AVERAGE_TICKS = 10;
    private String fps = "0", ping = "0", userName = "", timeStr = "", bpsStr = "0.0";
    private int width;
    private boolean logo, showFps, showPing;
    private Vec3 lastPos;
    private final ArrayDeque<Double> moves = new ArrayDeque<>();
    private double blocksPerSecond;

    public Watermark(NumberSetting x, NumberSetting y) {
        super(x, y);
    }

    private void updateBps(Minecraft mc) {
        if (mc.player == null) {
            lastPos = null;
            moves.clear();
            blocksPerSecond = 0;
            return;
        }
        Vec3 pos = mc.player.position();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dy = pos.y - lastPos.y;
            double dz = pos.z - lastPos.z;
            double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
            moves.addLast(step > 10.0 ? 0.0 : step);
            while (moves.size() > AVERAGE_TICKS) moves.removeFirst();
            double sum = 0;
            for (double m : moves) sum += m;
            blocksPerSecond = moves.isEmpty() ? 0 : (sum / moves.size()) * 20.0;
        }
        lastPos = pos;
    }

    @Override
    protected void measure(Minecraft mc, boolean editing) {
        updateBps(mc);
        var o = HudStyle.options();
        logo = o == null || o.watermarkLogo.enabled();
        showFps = o == null || o.watermarkFps.enabled();
        showPing = o == null || o.watermarkPing.enabled();

        fps = Integer.toString(im.yaHustler.module.impl.render.Optimization.displayedFps(mc));
        var info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        ping = Integer.toString(info == null ? 0 : info.getLatency());
        userName = mc.player != null ? mc.player.getName().getString() : (editing ? "User" : "");
        timeStr = LocalTime.now().format(CLOCK);
        bpsStr = String.format(Locale.ROOT, "%.1f BPS", blocksPerSecond);

        float w = 10f;
        if (logo) w += 13f;
        w += brandWidth();
        if (!userName.isEmpty()) w += segmentWidth(userName, true);
        if (showFps) w += segmentWidth(fps + " FPS", true);
        if (showPing) w += segmentWidth(ping + " MS", true);
        if (blocksPerSecond > 0.05 || editing) w += segmentWidth(bpsStr, true);
        w += segmentWidth(timeStr, false);
        w += 10f;

        width = (int) Math.ceil(w);
        size(width, 19);
    }

    private static float brandWidth() {
        return FigmaFont.mediumWidth("Hustle 26.3", FONT / 11);
    }

    private static float segmentWidth(String text, boolean separator) {
        return FigmaFont.width(text, FONT / 11) + (separator ? 14f : 4f);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        HudStyle.panel(g, x, y, width, 19, 1, 6f);
        float cx = x + 7f;
        float cy = y + 9.5f;

        if (logo) {
            InterfaceAssets.icon(g, "brand", cx, cy - 5f, 10, HudStyle.accent());
            cx += 13f;
        }
        HudStyle.text(g, "Hustle 26.3", cx, cy, HudStyle.TEXT, FONT, true);
        cx += brandWidth();

        if (!userName.isEmpty()) {
            cx = drawSeparator(g, cx, cy);
            InterfaceAssets.icon(g, "user", cx, cy - 4.5f, 9, HudStyle.accent());
            cx += 11f;
            HudStyle.text(g, userName, cx, cy, HudStyle.TEXT, FONT, false);
            cx += FigmaFont.width(userName, FONT / 11);
        }

        if (showFps) {
            cx = drawSeparator(g, cx, cy);
            InterfaceAssets.icon(g, "hud/watermark_fps", cx, cy - 4.5f, 9, HudStyle.accent());
            cx += 11f;
            String text = fps + " FPS";
            HudStyle.text(g, text, cx, cy, HudStyle.TEXT, FONT, false);
            cx += FigmaFont.width(text, FONT / 11);
        }

        if (showPing) {
            cx = drawSeparator(g, cx, cy);
            InterfaceAssets.icon(g, "hud/watermark_ping", cx, cy - 4.5f, 9, HudStyle.accent());
            cx += 11f;
            String text = ping + " MS";
            HudStyle.text(g, text, cx, cy, HudStyle.TEXT, FONT, false);
            cx += FigmaFont.width(text, FONT / 11);
        }

        if (blocksPerSecond > 0.05 || (mc.player == null)) {
            cx = drawSeparator(g, cx, cy);
            InterfaceAssets.icon(g, "movement", cx, cy - 4.5f, 9, HudStyle.accent());
            cx += 11f;
            HudStyle.text(g, bpsStr, cx, cy, HudStyle.TEXT, FONT, false);
            cx += FigmaFont.width(bpsStr, FONT / 11);
        }

        cx = drawSeparator(g, cx, cy);
        InterfaceAssets.icon(g, "activity", cx, cy - 4.5f, 9, HudStyle.accent());
        cx += 11f;
        HudStyle.text(g, timeStr, cx, cy, HudStyle.MUTED, FONT, false);
    }

    private static float drawSeparator(GuiGraphicsExtractor g, float cx, float cy) {
        cx += 5f;
        Draw.circle(g, cx, cy, 1f, 0x40ffffff);
        return cx + 5f;
    }

    @Override
    protected float radius() {
        return 6f;
    }
}
