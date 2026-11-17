package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import im.yaHustler.Hustle26;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.hud.HudBackdrop;
import im.yaHustler.hud.HudStyle;
import im.yaHustler.module.impl.render.Hud;
import im.yaHustler.setting.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Per-widget settings opened with RMB while editing the HUD. */
public final class HudEditorScreen extends Screen {
    private static final int POPUP_WIDTH = 220;
    private static final int HEADER = 23;
    private static final int TEXT = 0xfff0f4fc, MUTED = 0xffa2b1c9;
    private static final int RADIUS = 9;
    private final Screen parent;
    private final List<Setting<?>> settings;
    private int x, y;
    private int popupHeight, rowHeight;
    private NumberSetting dragging;
    public HudEditorScreen(Screen parent, String name, List<Setting<?>> settings, int x, int y) {
        super(Component.literal(name)); this.parent = parent; this.settings = settings;
        this.x = x; this.y = y;
    }
    public ClickGuiScreen backdropScreen() { return parent instanceof ClickGuiScreen gui ? gui : null; }
    public float[] popupSurface() { return new float[]{x, y, POPUP_WIDTH, popupHeight, RADIUS}; }
    @Override protected void init() {
        rowHeight = Math.clamp((height - HEADER - 16) / Math.max(1, settings.size()), 19, 26);
        popupHeight = HEADER + 6 + settings.size() * rowHeight;
        x = Math.clamp(x, 4, Math.max(4, width - POPUP_WIDTH - 4));
        y = Math.clamp(y, 4, Math.max(4, height - popupHeight - 4));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var hud = Hustle26.MODULES.get(Hud.class);
        ClickGuiScreen gui = backdropScreen();
        boolean blurred = gui != null ? gui.blurEnabled() : hud != null && hud.enabled() && hud.blur.enabled();
        boolean tinted = gui != null ? gui.themeBlur() : hud != null && hud.themeBlur.enabled();
        if (gui != null) gui.extractRenderState(g, -1000, -1000, delta);
        else if (blurred) HudBackdrop.add(g, x, y, POPUP_WIDTH, popupHeight, RADIUS);
        Draw.shadow(g, x, y, POPUP_WIDTH, popupHeight, RADIUS, .45f, 6);
        Draw.rounded(g, x, y, POPUP_WIDTH, popupHeight, RADIUS, HudStyle.panelFill(blurred, 1, tinted));
        Draw.roundedOutline(g, x, y, POPUP_WIDTH, popupHeight, RADIUS, .7f, 0x24ffffff);
        InterfaceAssets.centeredIcon(g, "hud/settings", x + 14, y + 12, 9, FigmaGuiRenderer.accent());
        FigmaFont.drawMedium(g, getTitle().getString(), x + 24, y + 8, TEXT, 8f / 11);
        Draw.fillF(g, x + 7, y + HEADER - 1, POPUP_WIDTH - 14, .5f, 0x24ffffff);
        for (int i = 0; i < settings.size(); i++) {
            Setting<?> setting = settings.get(i);
            int ry = y + HEADER + i * rowHeight;
            Draw.rounded(g, x + 6, ry + 1, POPUP_WIDTH - 12, rowHeight - 2, 5, 0x981b2639);
            String label = i == 0 ? "Размер" : SettingPresentation.of(setting).label();
            FigmaFont.draw(g, FigmaFont.fit(label, 160, 7f / 11), x + 10, ry + 4, TEXT, 7f / 11);
            switch (setting) {
                case NumberSetting n -> {
                    FigmaFont.draw(g, n.display(), x + 210 - FigmaFont.width(n.display(), 6.25f / 11), ry + 4, MUTED, 6.25f / 11);
                    float filled = (float)n.progress() * 200;
                    int trackY = ry + rowHeight - 6;
                    Draw.roundedF(g, x + 10, trackY, 200, 2, 1, 0x30eef3ef);
                    Draw.roundedF(g, x + 10, trackY, filled, 2, 1, FigmaGuiRenderer.accent());
                    Draw.circle(g, x + 10 + filled, trackY + 1, 2.3f, TEXT);
                }
                case BooleanSetting b -> FigmaGuiRenderer.toggle(g, x + 190, ry + (rowHeight - 10) / 2, b.enabled() ? 1 : 0, Draw.inside(mx, my, x + 10, ry, 200, rowHeight));
                case ModeSetting m -> FigmaFont.draw(g, m.get(), x + 10, ry + 12, FigmaGuiRenderer.accent(), 6.25f / 11);
                default -> {}
            }
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (!Draw.inside(e.x(), e.y(), x, y, POPUP_WIDTH, popupHeight)) { onClose(); return true; }
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        int index = (int)(e.y() - y - HEADER) / rowHeight;
        if (e.y() < y + HEADER || index < 0 || index >= settings.size()) return true;
        switch (settings.get(index)) {
            case NumberSetting n -> { dragging = n; update(e.x()); }
            case BooleanSetting b -> b.toggle();
            case ModeSetting m -> m.set(m.modes().get((m.modes().indexOf(m.get()) + 1) % m.modes().size()));
            default -> {}
        }
        return true;
    }
    private void update(double mx) { dragging.setProgress((mx - x - 10) / 200, false); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { if (dragging != null) { update(e.x()); return true; } return false; }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if (dragging != null) { dragging.set(dragging.get()); dragging = null; } return true; }
    @Override public void onClose() { if (dragging != null) dragging.set(dragging.get()); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
