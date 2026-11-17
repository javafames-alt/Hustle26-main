package im.yaHustler.hud;

import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.InterfaceAssets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Watermark extends HudElement {
    private static final float FONT = 7.5f, ICON = 8f, ICON_GAP = 4f, GAP = 10f, PADDING = 7f;
    private static final int HEIGHT = 21, AVERAGE_TICKS = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private record Metric(String icon, String label) {
        float width() {
            return (icon == null ? 0 : ICON + ICON_GAP) + HudStyle.width(label, FONT);
        }
    }

    private final List<Metric> metrics = new ArrayList<>(7);
    private final ArrayDeque<Double> moves = new ArrayDeque<>();
    private LocalPlayer player;
    private Vec3 previousPosition;
    private int panelWidth;
    private double blocksPerSecond;

    public Watermark(NumberSetting x, NumberSetting y) { super(x, y); }

    public void tick(Minecraft client) {
        if (player != client.player) {
            player = client.player;
            previousPosition = null;
            moves.clear();
            blocksPerSecond = 0;
        }
        if (player == null) return;
        Vec3 current = player.position();
        if (previousPosition != null) {
            double step = previousPosition.distanceTo(current);
            moves.addLast(step > 10 ? 0 : step);
            if (moves.size() > AVERAGE_TICKS) moves.removeFirst();
            blocksPerSecond = moves.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 20;
        }
        previousPosition = current;
    }

    @Override
    protected void measure(Minecraft client, boolean editing) {
        var options = HudStyle.options();
        var player = client.player;
        metrics.clear();
        metrics.add(new Metric(options == null || options.watermarkLogo.enabled() ? "brand" : null, "Hustle"));
        metrics.add(new Metric("user", player.getName().getString()));
        if (options == null || options.watermarkFps.enabled()) {
            metrics.add(new Metric("fps", im.yaHustler.module.impl.render.Optimization.displayedFps(client) + " FPS"));
        }
        if (options == null || options.watermarkPing.enabled()) {
            var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player.getUUID());
            metrics.add(new Metric("hud/wifi", info == null ? "-" : info.getLatency() + " ms"));
        }
        var position = player.blockPosition();
        metrics.add(new Metric("coords", position.getX() + ", " + position.getY() + ", " + position.getZ()));
        metrics.add(new Metric("bps", String.format(Locale.ROOT, "%.1f BPS", blocksPerSecond)));
        metrics.add(new Metric("clock", LocalTime.now().format(CLOCK)));
        float total = PADDING * 2 + (metrics.size() - 1) * GAP;
        for (Metric metric : metrics) total += metric.width();
        panelWidth = (int)Math.ceil(total);
        size(panelWidth, HEIGHT);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Minecraft client, int x, int y) {
        HudStyle.panel(g, x, y, panelWidth, HEIGHT, 1, HEIGHT / 2f);
        float left = x + PADDING, cy = y + HEIGHT / 2f;
        for (int i = 0; i < metrics.size(); i++) {
            Metric metric = metrics.get(i);
            if (i > 0) {
                Draw.fillF(g, left + GAP / 2, cy - 3, .5f, 6, 0x38afc7e8);
                left += GAP;
            }
            if (metric.icon != null) {
                if (i == 0) InterfaceAssets.icon(g, metric.icon, left, cy - ICON / 2, ICON, HudStyle.accent());
                else InterfaceAssets.centeredIcon(g, metric.icon, left + ICON / 2, cy, ICON, HudStyle.accent());
            }
            float textX = left + (metric.icon == null ? 0 : ICON + ICON_GAP);
            HudStyle.text(g, metric.label, textX, cy, i == 0 ? HudStyle.TEXT : HudStyle.MUTED, FONT, i == 0);
            left += metric.width();
        }
    }

    @Override
    protected float radius() { return HEIGHT / 2f; }
}
