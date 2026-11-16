package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import im.yaHustler.font.Fonts;
import im.yaHustler.font.Icons;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.module.impl.themes.ThemeColor;
import im.yaHustler.util.ColorUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

final class ThemeEditor {
    record Colors(int text, int muted, int accent, int card, int cardHover, int field, int fieldHover,
                  int popupBorder, int popup, int line, int selected) {}

    private static final ThemeColor[] SLOTS = ThemeColor.values();
    private static final String CREATE = "Создать пресет", READY = "Готовые темы", TITLE = "Кастомизируй свой стиль";
    private static final int MODAL_W = 160, MODAL_H = 52, FIELD_H = 18;
    private static final int PRESET_H = 20, PICKER_W = 92, SV_H = 56, HUE_H = 5, BUTTON_H = 11;
    private static final int PICKER_H = 6 + SV_H + 6 + HUE_H + 6 + BUTTON_H + 6;
    private static final int READY_COLS = 2, READY_W = 74, READY_ROW = 14;

    private int x, y, w, h, leftW, rightX, rightW, rowsTop, buttonX, buttonY, buttonW;
    private float rowH;
    private ThemeColor editing;
    private int pickerX, pickerY, alpha;
    private float hue, saturation, value;
    private boolean draggingSv, draggingHue, readyOpen;
    private int readyX, readyY;
    private boolean creating;
    private int modalX, modalY;
    private final SmoothSearchBox nameBox = new SmoothSearchBox(0, 0, 100, 16, "Название пресета", "Название");

    void close() {
        if (editing != null && (draggingSv || draggingHue)) Theme.save();
        editing = null;
        draggingSv = draggingHue = readyOpen = false;
        closeCreate();
    }
    boolean creating() { return creating; }
    private void openCreate() {
        creating = true;
        nameBox.setValue("");
        nameBox.setFocused(true);
    }
    private void closeCreate() {
        creating = false;
        nameBox.setFocused(false);
    }
    private void confirmCreate() {
        Theme.createPreset(nameBox.getValue());
        closeCreate();
    }
    boolean keyPressed(KeyEvent event) {
        if (!creating) return false;
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) closeCreate();
        else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) confirmCreate();
        else nameBox.keyPressed(event);
        return true;
    }
    boolean charTyped(CharacterEvent event) {
        if (!creating) return false;
        nameBox.charTyped(event);
        return true;
    }
    private void layout(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        leftW = Math.clamp(Math.round(w * .32f), 90, 140);
        rightX = x + leftW + 16;
        rightW = x + w - rightX;
        buttonW = (int) Math.ceil(Fonts.width(READY, .66f)) + 12;
        buttonX = rightX + rightW - buttonW;
        buttonY = y + 2;
        rowsTop = y + 24;
        rowH = Math.min(14, (h - 26) / (float) SLOTS.length);
    }
    void draw(GuiGraphicsExtractor g, Colors c, int x, int y, int w, int h, int mx, int my) {
        layout(x, y, w, h);
        boolean free = editing == null && !readyOpen && !creating;
        boolean createHover = free && Draw.inside(mx, my, x, y + 2, leftW, 18);
        if (createHover) Draw.rounded(g, x, y + 2, leftW, 18, 4, c.cardHover());
        Fonts.draw(g, Fonts.fit(CREATE, leftW - 24, .7f), x + 6, y + 7.5f, createHover ? c.text() : c.muted(), .7f);
        Fonts.drawMedium(g, "+", x + leftW - 12, y + 6, createHover ? c.accent() : c.muted(), .9f);

        List<Theme.Preset> presets = Theme.presets();
        for (int i = 0; i < presets.size(); i++) {
            Theme.Preset preset = presets.get(i);
            int py = y + 26 + i * (PRESET_H + 4);
            boolean hover = free && Draw.inside(mx, my, x, py, leftW, PRESET_H);
            boolean current = preset.name().equals(Theme.paletteName());
            if (current || hover) Draw.rounded(g, x, py, leftW, PRESET_H, 4, current ? c.selected() : c.cardHover());
            Fonts.drawMedium(g, Fonts.fit(preset.name(), leftW - 22, .74f), x + 6, py + (PRESET_H - 8 * .74f) / 2f, c.text(), .74f);
            if (hover) {
                boolean deleteHover = Draw.inside(mx, my, x + leftW - 15, py + (PRESET_H - 14) / 2, 12, 14);
                Fonts.draw(g, "x", x + leftW - 11, py + (PRESET_H - 8 * .7f) / 2f, deleteHover ? 0xffff5a5a : c.muted(), .7f);
            }
        }
        g.fill(x + leftW + 7, y, x + leftW + 8, y + h, c.line());

        boolean buttonHover = (free || readyOpen) && Draw.inside(mx, my, buttonX, buttonY, buttonW, 14);
        Draw.rounded(g, buttonX, buttonY, buttonW, 14, 4, buttonHover || readyOpen ? c.fieldHover() : c.field());
        Fonts.draw(g, READY, buttonX + 6, buttonY + 4.5f, buttonHover || readyOpen ? c.text() : c.muted(), .66f);
        int titleRoom = buttonX - rightX - 6;
        String title = Fonts.fit(TITLE, titleRoom, .74f);
        Fonts.drawMedium(g, title, rightX, y + 5, c.text(), .74f);
        String suffix = " (" + Theme.paletteName() + ")";
        float titleW = Fonts.width(title, .74f);
        if (title.equals(TITLE) && titleW + Fonts.width(suffix, .62f) <= titleRoom)
            Fonts.draw(g, suffix, rightX + titleW, y + 6, c.muted(), .62f);

        for (int i = 0; i < SLOTS.length; i++) {
            ThemeColor slot = SLOTS[i];
            float ry = rowsTop + i * rowH, cy = ry + rowH / 2f, cx = rightX + rightW - 5;
            boolean hover = free && my >= ry && my < ry + rowH && mx >= rightX && mx < rightX + rightW;
            if (hover || slot == editing) Draw.roundedF(g, rightX - 3, ry, rightW + 6, rowH, 3, c.cardHover());
            int color = Theme.color(slot);
            Fonts.draw(g, slot.label, rightX, cy - 3, hover || slot == editing ? c.text() : ColorUtil.lerp(c.text(), c.muted(), .25f), .64f);
            String hex = ColorUtil.hex(color);
            Fonts.draw(g, hex, cx - 8 - Fonts.width(hex, .6f), cy - 2.8f, c.muted(), .6f);
            Draw.circle(g, cx, cy, 4.3f, 0x30ffffff);
            Draw.circle(g, cx, cy, 3.6f, ColorUtil.opaque(color));
        }
    }
    void drawOverlay(GuiGraphicsExtractor g, Colors c, int mx, int my, int screenW, int screenH, float delta) {
        if (readyOpen) drawReady(g, c, mx, my);
        if (editing != null) drawPicker(g, c);
        if (creating) drawCreate(g, c, mx, my, screenW, screenH, delta);
    }
    private void drawCreate(GuiGraphicsExtractor g, Colors c, int mx, int my, int screenW, int screenH, float delta) {
        modalX = (screenW - MODAL_W) / 2;
        modalY = (screenH - MODAL_H) / 2;
        g.fill(0, 0, screenW, screenH, 0x66000000);
        Draw.shadow(g, modalX, modalY, MODAL_W, MODAL_H, 6);
        Draw.rounded(g, modalX, modalY, MODAL_W, MODAL_H, 6, c.popupBorder());
        Draw.rounded(g, modalX + 1, modalY + 1, MODAL_W - 2, MODAL_H - 2, 5, c.popup());
        Fonts.drawMedium(g, "Создайте свой пресет", modalX + 9, modalY + 9, c.text(), .74f);
        boolean closeHover = Draw.inside(mx, my, modalX + MODAL_W - 17, modalY + 5, 13, 13);
        Fonts.draw(g, "x", modalX + MODAL_W - 13, modalY + 8.5f, closeHover ? c.text() : c.muted(), .74f);
        int fx = fieldX(), fy = fieldY(), fw = fieldW();
        Draw.rounded(g, fx, fy, fw, FIELD_H, 4, c.field());
        Fonts.drawIcon(g, Icons.THEMES, fx + 5, fy + (FIELD_H - 10 * .62f) / 2f, c.muted(), .62f);
        nameBox.setX(fx + 16);
        nameBox.setY(fy + 1);
        nameBox.setWidth(fw - 34);
        nameBox.extractWidgetRenderState(g, mx, my, delta);
        int cx = checkX(), cy = fy;
        boolean checkHover = Draw.inside(mx, my, cx, cy, FIELD_H, FIELD_H);
        if (checkHover) Draw.rounded(g, cx, cy, FIELD_H, FIELD_H, 4, c.fieldHover());
        check(g, cx + FIELD_H / 2f, cy + FIELD_H / 2f, checkHover ? c.accent() : c.muted());
    }
    private static void check(GuiGraphicsExtractor g, float cx, float cy, int color) {
        stroke(g, cx - 3.2f, cy, cx - 1, cy + 2.3f, color);
        stroke(g, cx - 1, cy + 2.3f, cx + 3.4f, cy - 2.4f, color);
    }
    private static void stroke(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
        int steps = (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1) * 3);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            Draw.circle(g, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, .6f, color);
        }
    }
    private int fieldX() { return modalX + 9; }
    private int fieldY() { return modalY + 25; }
    private int fieldW() { return MODAL_W - 18; }
    private int checkX() { return fieldX() + fieldW() - FIELD_H; }
    private void drawReady(GuiGraphicsExtractor g, Colors c, int mx, int my) {
        int rows = (Theme.COLORS.size() + READY_COLS - 1) / READY_COLS;
        int pw = READY_COLS * READY_W + 8, ph = rows * READY_ROW + 8;
        readyX = buttonX + buttonW - pw;
        readyY = buttonY + 17;
        Draw.rounded(g, readyX, readyY, pw, ph, 5, c.popupBorder());
        Draw.rounded(g, readyX + 1, readyY + 1, pw - 2, ph - 2, 4, c.popup());
        for (int i = 0; i < Theme.COLORS.size(); i++) {
            Theme.Color color = Theme.COLORS.get(i);
            int ex = readyX + 4 + i % READY_COLS * READY_W, ey = readyY + 4 + i / READY_COLS * READY_ROW;
            boolean hover = Draw.inside(mx, my, ex, ey, READY_W, READY_ROW);
            if (hover) Draw.rounded(g, ex, ey, READY_W, READY_ROW, 3, c.cardHover());
            Draw.circle(g, ex + 8, ey + 7, 3.6f, color.argb());
            boolean current = color.name().equals(Theme.paletteName());
            Fonts.draw(g, Fonts.fit(color.name(), READY_W - 18, .62f), ex + 15, ey + 4.5f,
                current ? c.accent() : hover ? c.text() : c.muted(), .62f);
        }
    }
    private void drawPicker(GuiGraphicsExtractor g, Colors c) {
        Draw.rounded(g, pickerX, pickerY, PICKER_W, PICKER_H, 5, c.popupBorder());
        Draw.rounded(g, pickerX + 1, pickerY + 1, PICKER_W - 2, PICKER_H - 2, 4, c.popup());
        int sx = svX(), sy = svY(), sw = svW();
        g.fill(sx, sy, sx + sw, sy + SV_H, ColorUtil.fromHsv(hue, 1, 1, 255));
        Draw.horizontalGradient(g, sx, sy, sw, SV_H, 0xffffffff, 0x00ffffff);
        g.fillGradient(sx, sy, sx + sw, sy + SV_H, 0x00000000, 0xff000000);
        float kx = sx + saturation * sw, ky = sy + (1 - value) * SV_H;
        Draw.circle(g, kx, ky, 3.2f, 0xffffffff);
        Draw.circle(g, kx, ky, 2.3f, ColorUtil.fromHsv(hue, saturation, value, 255));

        int hy = hueY();
        for (int i = 0; i < 6; i++)
            Draw.horizontalGradient(g, sx + sw * i / 6f, hy, sw / 6f + .5f, HUE_H,
                ColorUtil.fromHsv(i / 6f, 1, 1, 255), ColorUtil.fromHsv((i + 1) / 6f, 1, 1, 255));
        float hx = sx + hue * sw;
        Draw.circle(g, hx, hy + HUE_H / 2f, 3.4f, 0xffffffff);
        Draw.circle(g, hx, hy + HUE_H / 2f, 2.5f, ColorUtil.fromHsv(hue, 1, 1, 255));

        int by = buttonsY(), bw = (sw - 4) / 2;
        button(g, c, "Копировать", sx, by, bw);
        button(g, c, "Вставить", sx + bw + 4, by, bw);
    }
    private static void button(GuiGraphicsExtractor g, Colors c, String text, int bx, int by, int bw) {
        Draw.rounded(g, bx, by, bw, BUTTON_H, 3, c.field());
        Fonts.draw(g, text, bx + (bw - Fonts.width(text, .56f)) / 2f, by + 3.3f, c.muted(), .56f);
    }
    private int svX() { return pickerX + 6; }
    private int svY() { return pickerY + 6; }
    private int svW() { return PICKER_W - 12; }
    private int hueY() { return svY() + SV_H + 6; }
    private int buttonsY() { return hueY() + HUE_H + 6; }

    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, int screenHeight) {
        double mx = event.x(), my = event.y();
        if (creating) {
            if (!Draw.inside(mx, my, modalX, modalY, MODAL_W, MODAL_H)
                || Draw.inside(mx, my, modalX + MODAL_W - 17, modalY + 5, 13, 13)) closeCreate();
            else if (Draw.inside(mx, my, checkX(), fieldY(), FIELD_H, FIELD_H)) confirmCreate();
            else if (Draw.inside(mx, my, fieldX(), fieldY(), fieldW(), FIELD_H)) {
                nameBox.setFocused(true);
                nameBox.mouseClicked(event, doubleClick);
            }
            return true;
        }
        if (editing != null) {
            if (Draw.inside(mx, my, svX(), svY(), svW(), SV_H)) { draggingSv = true; drag(mx, my); return true; }
            if (Draw.inside(mx, my, svX() - 2, hueY() - 2, svW() + 4, HUE_H + 4)) { draggingHue = true; drag(mx, my); return true; }
            int bw = (svW() - 4) / 2;
            if (Draw.inside(mx, my, svX(), buttonsY(), bw, BUTTON_H)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(ColorUtil.hex(Theme.color(editing)));
                return true;
            }
            if (Draw.inside(mx, my, svX() + bw + 4, buttonsY(), bw, BUTTON_H)) {
                Integer pasted = ColorUtil.parse(Minecraft.getInstance().keyboardHandler.getClipboard());
                if (pasted != null) {
                    Theme.set(editing, pasted, true);
                    load(pasted);
                }
                return true;
            }
            if (Draw.inside(mx, my, pickerX, pickerY, PICKER_W, PICKER_H)) return true;
            close();
            return true;
        }
        if (readyOpen) {
            int rows = (Theme.COLORS.size() + READY_COLS - 1) / READY_COLS;
            for (int i = 0; i < Theme.COLORS.size(); i++) {
                int ex = readyX + 4 + i % READY_COLS * READY_W, ey = readyY + 4 + i / READY_COLS * READY_ROW;
                if (Draw.inside(mx, my, ex, ey, READY_W, READY_ROW)) {
                    Theme.applyReady(Theme.COLORS.get(i));
                    break;
                }
            }
            if (!Draw.inside(mx, my, readyX, readyY, READY_COLS * READY_W + 8, rows * READY_ROW + 8)
                || Draw.inside(mx, my, readyX + 4, readyY + 4, READY_COLS * READY_W, rows * READY_ROW)) readyOpen = false;
            return true;
        }
        if (!Draw.inside(mx, my, x, y, w, h)) return false;
        if (Draw.inside(mx, my, buttonX, buttonY, buttonW, 14)) { readyOpen = true; return true; }
        if (Draw.inside(mx, my, x, y + 2, leftW, 18)) { openCreate(); return true; }
        List<Theme.Preset> presets = Theme.presets();
        for (int i = 0; i < presets.size(); i++) {
            int py = y + 26 + i * (PRESET_H + 4);
            if (!Draw.inside(mx, my, x, py, leftW, PRESET_H)) continue;
            if (Draw.inside(mx, my, x + leftW - 15, py + (PRESET_H - 14) / 2, 12, 14)) Theme.deletePreset(presets.get(i));
            else Theme.applyPreset(presets.get(i));
            return true;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            float ry = rowsTop + i * rowH;
            if (my < ry || my >= ry + rowH || mx < rightX || mx >= rightX + rightW) continue;
            editing = SLOTS[i];
            load(Theme.color(editing));
            pickerX = Math.max(4, rightX + rightW - 5 - PICKER_W - 10);
            pickerY = Math.clamp(Math.round(ry + rowH / 2f) - 10, 4, Math.max(4, screenHeight - PICKER_H - 4));
            return true;
        }
        return true;
    }
    boolean mouseDragged(double mx, double my) {
        if (!draggingSv && !draggingHue) return false;
        drag(mx, my);
        return true;
    }
    boolean mouseReleased() {
        if (!draggingSv && !draggingHue) return false;
        draggingSv = draggingHue = false;
        Theme.save();
        return true;
    }
    private void drag(double mx, double my) {
        if (draggingSv) {
            saturation = (float) Math.clamp((mx - svX()) / svW(), 0, 1);
            value = 1 - (float) Math.clamp((my - svY()) / SV_H, 0, 1);
        } else if (draggingHue) {
            hue = (float) Math.clamp((mx - svX()) / svW(), 0, .999);
        }
        Theme.set(editing, ColorUtil.fromHsv(hue, saturation, value, alpha), false);
    }
    private void load(int color) {
        float[] hsv = ColorUtil.toHsv(color);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        alpha = color >>> 24;
    }
}
