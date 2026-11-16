package morfeov.coliseum.ui;

import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.font.MsdfPipeline;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ThemePickerScreen extends Screen {
    private final ClickGuiScreen parent;
    private final morfeov.coliseum.setting.ColorSetting setting;
    private int x, y, w, h, sx, sy, sw, sh, hueY, dragging, alpha;
    private float hue, saturation, value, scale = 1;

    public ThemePickerScreen(ClickGuiScreen parent) { this(parent, null); }
    public ThemePickerScreen(ClickGuiScreen parent, morfeov.coliseum.setting.ColorSetting setting) {
        super(Component.literal(setting == null ? "Color Picker" : setting.name()));
        this.parent = parent;
        this.setting = setting;
    }
    public ClickGuiScreen backdropScreen() { return parent; }
    public float[] popupSurface() { return new float[]{x, y, w * scale, h * scale, 7 * scale}; }
    private void layout() {
        w = 78;
        h = 76;
        scale = Math.max(.1f, Math.min(parent.transform().scale(), Math.min((width - 16f) / w, (height - 16f) / h)));
        x = Math.clamp(Math.round(parent.windowRight() + 6 * scale), 8, Math.max(8, width - Math.round(w * scale) - 8));
        y = Math.clamp(Math.round(parent.windowTop() + 28 * scale), 8, Math.max(8, height - Math.round(h * scale) - 8));
        sx = 6;
        sy = 6;
        sw = w - 12;
        sh = 52;
        hueY = sy + sh + 7;
    }
    @Override protected void init() {
        layout();
        int color = setting == null ? FigmaGuiRenderer.accent() : setting.color();
        float[] hsv = ColorUtil.toHsv(color);
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2];
        alpha = color >>> 24;
        dragging = 0;
    }
    private void save() { if (setting == null) Theme.save(); else setting.set(setting.get()); }
    private void apply() {
        int color = ColorUtil.fromHsv(hue, saturation, value, alpha);
        if (setting != null) setting.setSilently(color);
        else Theme.applyGuiPalette("Custom Theme", ColorUtil.opaque(color), ColorUtil.lerp(ColorUtil.opaque(color), 0xffffffff, .18f), false);
    }
    private void update(double mx, double my) {
        mx = (mx - x) / scale;
        my = (my - y) / scale;
        float t = Math.clamp((float) (mx - sx) / sw, 0, 1);
        if (dragging == 1) {
            saturation = t;
            value = 1 - Math.clamp((float) (my - sy) / sh, 0, 1);
        } else if (dragging == 2) hue = Math.min(.99999f, t);
        apply();
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        parent.extractRenderState(graphics, -1000, -1000, delta);
        layout();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        float opacity = GuiOpacity.push(parent.backdropOpacity());
        try {
            parent.drawSurface(graphics, 0, 0, w, h, 7);
            int hueColor = ColorUtil.fromHsv(hue, 1, 1, 255);
            int border = ColorUtil.alpha(FigmaGuiRenderer.textColor(), 28);
            var texture = Identifier.fromNamespaceAndPath("coliseum", "textures/gui/round.png");
            Draw.roundedF(graphics, sx - .5f, sy - .5f, sw + 1, sh + 1, 3.5f, border);
            GuiTextures.blit(graphics, MsdfPipeline.colorField(), texture, sx, sy, 0, 0, sw, sh, 1, 1, 1, 1, GuiOpacity.color(hueColor));
            GuiTextures.blit(graphics, MsdfPipeline.hueStrip(), texture, sx, hueY, 0, 0, sw, 5, 1, 1, 1, 1, GuiOpacity.color(0xffffffff));
            knob(graphics, sx + saturation * sw, sy + (1 - value) * sh, ColorUtil.fromHsv(hue, saturation, value, 255));
            knob(graphics, sx + hue * sw, hueY + 2.5f, hueColor);
        } finally {
            GuiOpacity.restore(opacity);
            graphics.pose().popMatrix();
        }
    }
    private static void knob(GuiGraphicsExtractor graphics, float x, float y, int color) {
        Draw.circle(graphics, x, y, 2.6f, 0x66000000);
        Draw.circle(graphics, x, y, 2, FigmaGuiRenderer.textColor());
        Draw.circle(graphics, x, y, 1.2f, color);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = (event.x() - x) / scale, my = (event.y() - y) / scale;
        if (!Draw.inside(mx, my, 0, 0, w, h)) { onClose(); return true; }
        if (Draw.inside(mx, my, sx, sy, sw, sh)) dragging = 1;
        else if (Draw.inside(mx, my, sx - 3, hueY - 3, sw + 6, 13)) dragging = 2;
        if (dragging != 0) update(event.x(), event.y());
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == 0) return super.mouseDragged(event, dx, dy);
        update(event.x(), event.y());
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || dragging == 0) return super.mouseReleased(event);
        dragging = 0;
        save();
        return true;
    }
    @Override public void onClose() { save(); minecraft.gui.setScreen(parent); }
    @Override public void removed() { dragging = 0; save(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
