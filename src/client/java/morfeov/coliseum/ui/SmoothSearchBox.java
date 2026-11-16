package morfeov.coliseum.ui;

import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.module.impl.themes.ThemeColor;
import morfeov.coliseum.util.ColorUtil;
import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.font.Fonts;
import morfeov.coliseum.font.FigmaFont;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class SmoothSearchBox extends AbstractWidget {
    private float SCALE = .76f;
    public void textScale(float scale) { SCALE = scale; }
    private final String placeholder;
    private String value = "";
    private int cursor, anchor;
    private float offset;
    private boolean figmaFont;
    private Consumer<String> responder = s -> {};

    public SmoothSearchBox(int x, int y, int width, int height) {
        this(x, y, width, height, "Поиск модулей", "Поиск...");
    }
    public SmoothSearchBox(int x, int y, int width, int height, String narration, String placeholder) {
        super(x, y, width, height, Component.literal(narration));
        this.placeholder = placeholder;
    }
    public String getValue() { return value; }
    public void useFigmaFont() { figmaFont = true; }
    public void setResponder(Consumer<String> consumer) { responder = consumer; }
    public void setValue(String text) {
        value = text.codePoints().filter(c -> c >= 32 && c != 127).limit(64)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        cursor = anchor = value.length();
        responder.accept(value);
    }
    @Override
    public void setFocused(boolean focused) {
        if (isFocused() == focused) return;
        super.setFocused(focused);
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }
    @Override public boolean capturesInput() { return isFocused(); }
    private float widthOf(String text) { return figmaFont ? FigmaFont.width(text, SCALE) : Fonts.width(text, SCALE); }
    private void drawText(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        if (figmaFont) FigmaFont.draw(g, text, x, y, color, SCALE);
        else Fonts.draw(g, text, x, y, color, SCALE);
    }
    private void keepCursorVisible() {
        float cx = widthOf(value.substring(0, cursor));
        float available = Math.max(1, width - 4);
        if (cx < offset) offset = cx;
        if (cx > offset + available) offset = cx - available;
        offset = Math.max(0, Math.min(offset, Math.max(0, widthOf(value) - available)));
    }
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        keepCursorVisible();
        float textHeight = figmaFont ? FigmaFont.height(SCALE) : 8;
        float tx = getX() + 2, ty = getY() + (height - textHeight) / 2f;
        g.enableScissor(getX(), getY(), getRight(), getBottom());
        if (value.isEmpty() && !isFocused()) {
            drawText(g, placeholder, tx, ty, figmaFont ? 0xffb4c0b8 : Theme.color(ThemeColor.TEXT_OFF));
        } else {
            if (isFocused() && cursor != anchor) {
                int lo = Math.min(cursor, anchor), hi = Math.max(cursor, anchor);
                int a = Math.round(tx + widthOf(value.substring(0, lo)) - offset);
                int b = Math.round(tx + widthOf(value.substring(0, hi)) - offset);
                Draw.fillF(g, a, ty - 1, b - a, textHeight + 2, ColorUtil.alpha(Theme.accent(), 0x70));
            }
            drawText(g, value, tx - offset, ty, Theme.color(ThemeColor.TEXT));
            if (isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = Math.round(tx + widthOf(value.substring(0, cursor)) - offset);
                Draw.fillF(g, cx, ty - .5f, .5f, textHeight + 1, Theme.accent());
            }
        }
        g.disableScissor();
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isMouseOver(event.x(), event.y())) return false;
        setFocused(true);
        float target = (float) event.x() - getX() - 2 + offset;
        cursor = 0;
        while (cursor < value.length()) {
            int next = value.offsetByCodePoints(cursor, 1);
            if (target < (widthOf(value.substring(0, cursor)) + widthOf(value.substring(0, next))) / 2) break;
            cursor = next;
        }
        anchor = doubleClick ? 0 : cursor;
        if (doubleClick) cursor = value.length();
        return true;
    }
    private void move(int position, boolean select) {
        cursor = Math.clamp(position, 0, value.length());
        if (!select) anchor = cursor;
        keepCursorVisible();
    }
    private void replaceSelection(String text) {
        int lo = Math.min(cursor, anchor), hi = Math.max(cursor, anchor);
        String clean = text.codePoints().filter(c -> c >= 32 && c != 127)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        int room = 64 - value.codePointCount(0, lo) - value.codePointCount(hi, value.length());
        int take = Math.min(room, clean.codePointCount(0, clean.length()));
        clean = clean.substring(0, clean.offsetByCodePoints(0, Math.max(0, take)));
        value = value.substring(0, lo) + clean + value.substring(hi);
        cursor = anchor = lo + clean.length();
        keepCursorVisible();
        responder.accept(value);
    }
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isFocused() || !event.isAllowedChatCharacter()) return false;
        replaceSelection(event.codepointAsString());
        return true;
    }
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) return false;
        int key = event.key();
        boolean control = event.hasControlDown(), select = event.hasShiftDown();
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (control && key == InputConstants.KEY_A) { anchor = 0; cursor = value.length(); return true; }
        if (control && (key == InputConstants.KEY_C || key == InputConstants.KEY_X)) {
            keyboard.setClipboard(value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)));
            if (key == InputConstants.KEY_X) replaceSelection("");
            return true;
        }
        if (control && key == InputConstants.KEY_V) { replaceSelection(keyboard.getClipboard()); return true; }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            int direction = key == InputConstants.KEY_LEFT ? -1 : 1;
            int position = control ? (direction < 0 ? 0 : value.length())
                : cursor != anchor && !select ? (direction < 0 ? Math.min(cursor, anchor) : Math.max(cursor, anchor))
                : value.offsetByCodePoints(cursor, direction < 0 ? (cursor > 0 ? -1 : 0) : (cursor < value.length() ? 1 : 0));
            move(position, select); return true;
        }
        if (key == InputConstants.KEY_HOME || key == InputConstants.KEY_END) {
            move(key == InputConstants.KEY_HOME ? 0 : value.length(), select); return true;
        }
        if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
            if (cursor == anchor) {
                if (key == InputConstants.KEY_BACKSPACE && cursor > 0)
                    anchor = control ? 0 : value.offsetByCodePoints(cursor, -1);
                if (key == InputConstants.KEY_DELETE && cursor < value.length())
                    anchor = control ? value.length() : value.offsetByCodePoints(cursor, 1);
            }
            replaceSelection(""); return true;
        }
        return key == InputConstants.KEY_RETURN;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
