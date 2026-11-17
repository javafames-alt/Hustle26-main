package im.yaHustler.hud;

import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class HudElement {
    public static final int CENTER = -1, RIGHT = -2;
    private final NumberSetting posX, posY;
    private int x, y, width, height;
    private NumberSetting scaleSetting;
    private final HudDragMotion motion = new HudDragMotion();
    private long frameTime, moveTime;
    private float rotation;
    public final void scaleSetting(NumberSetting setting) { scaleSetting = setting; }
    public final NumberSetting scaleSetting() { return scaleSetting; }

    protected HudElement(NumberSetting posX, NumberSetting posY) {
        this.posX = posX;
        this.posY = posY;
    }
    protected abstract void measure(Minecraft client, boolean editing);
    protected abstract void draw(GuiGraphicsExtractor g, Minecraft client, int x, int y);
    protected final void size(int width, int height) {
        this.width = width;
        this.height = height;
    }
    protected float radius() { return 5; }
    protected float shadowTop() { return 0; }
    protected float shadowAlpha() { return 1; }
    public final void extractRenderState(GuiGraphicsExtractor g, Minecraft client, boolean editing, boolean highlight,
                                         boolean shadow) {
        width = height = 0;
        measure(client, editing);
        if (width <= 0 || height <= 0) return;
        int rawWidth = width, rawHeight = height;
        float scale = scaleSetting == null ? 1 : scaleSetting.floatValue();
        scale = Math.min(scale, Math.min(g.guiWidth() / (float)rawWidth, g.guiHeight() / (float)rawHeight));
        if (scale <= 0) return;
        width = Math.max(1, Math.round(width * scale)); height = Math.max(1, Math.round(height * scale));
        int availableX = Math.max(0, g.guiWidth() - width);
        x = switch (posX.intValue()) {
            case CENTER -> availableX / 2;
            case RIGHT -> Math.max(0, availableX - 7);
            default -> Math.clamp(posX.intValue(), 0, availableX);
        };
        y = Math.clamp(posY.intValue(), 0, Math.max(0, g.guiHeight() - height));
        long now = System.nanoTime();
        double seconds = frameTime == 0 ? 0 : (now - frameTime) / 1e9;
        frameTime = now;
        var options = HudStyle.options();
        rotation = motion.step(seconds, editing && highlight, options != null && options.dragTilt.enabled());
        if (editing && highlight && options != null && options.dragGuides.enabled()) {
            int color = 0x55ffffff & 0xff000000 | HudStyle.accent() & 0xffffff;
            Draw.fillF(g, 0, y + height / 2f, g.guiWidth(), .5f, color);
            Draw.fillF(g, x + width / 2f, 0, .5f, g.guiHeight(), color);
        }
        g.pose().pushMatrix();
        g.pose().translate(x + width / 2f, y + height / 2f);
        g.pose().rotate(rotation);
        g.pose().scale(scale, scale);
        g.pose().translate(-x - rawWidth / 2f, -y - rawHeight / 2f);
        if (shadow) Draw.shadow(g, x, y + shadowTop(), rawWidth, rawHeight - shadowTop(), radius(),
            (options == null ? .65f : options.shadowStrength.floatValue() / 100) * shadowAlpha(),
            options == null ? 6 : options.shadowSize.floatValue());
        draw(g, client, x, y);
        if (editing && highlight) {
            int rgb = HudStyle.accent() & 0xffffff;
            for (int i = 3; i >= 1; i--) {
                float pad = (1 + i * 1.1f) / scale;
                Draw.roundedOutline(g, x - pad, y - pad, rawWidth + pad * 2, rawHeight + pad * 2,
                    radius() + pad, 1.3f / scale, (i == 1 ? 0x48000000 : 0x1c000000) | rgb);
            }
            float pad = 1.5f / scale;
            Draw.roundedOutline(g, x - pad, y - pad, rawWidth + pad * 2, rawHeight + pad * 2,
                radius() + pad, .8f / scale, 0xd9000000 | rgb);
        }
        g.pose().popMatrix();
    }
    public final boolean contains(double mx, double my) {
        double dx = mx - x - width / 2d, dy = my - y - height / 2d;
        double c = Math.cos(rotation), s = Math.sin(rotation);
        return width > 0 && Math.abs(c * dx + s * dy) <= width / 2d && Math.abs(-s * dx + c * dy) <= height / 2d;
    }
    public final int x() { return x; }
    public final int y() { return y; }
    public final int width() { return width; }
    public final int height() { return height; }
    public final void beginDrag() { moveTime = System.nanoTime(); }
    public final void dragTo(int nx, int ny, int screenWidth, int screenHeight, boolean save) {
        long now = System.nanoTime();
        int currentX = posX.intValue() < 0 ? x : posX.intValue();
        motion.move(nx - currentX, ny - posY.intValue(), (now - moveTime) / 1e9);
        moveTime = now;
        moveTo(nx, ny, screenWidth, screenHeight, save);
    }
    public final void savePosition() { posX.set(posX.get()); posY.set(posY.get()); }
    public final void moveTo(int nx, int ny, int screenWidth, int screenHeight, boolean save) {
        double cx = Math.clamp(nx, 0, Math.max(0, screenWidth - width));
        double cy = Math.clamp(ny, 0, Math.max(0, screenHeight - height));
        if (save) { posX.set(cx); posY.set(cy); }
        else { posX.setSilently(cx); posY.setSilently(cy); }
    }
}
