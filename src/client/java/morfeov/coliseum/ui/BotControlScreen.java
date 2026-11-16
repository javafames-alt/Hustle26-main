package morfeov.coliseum.ui;

import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.bot.client.BotManager;
import morfeov.coliseum.bot.client.BotSession;
import morfeov.coliseum.bot.view.BotWorldView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;

public final class BotControlScreen extends Screen {
    private final Screen parent;
    private final BotWorldView view = new BotWorldView();
    private BotSession session;
    private SmoothSearchBox chat;
    private boolean dragging, moved;

    public BotControlScreen(Screen parent, BotSession session) {
        super(Component.literal("Bots — " + session.config().name()));
        this.parent = parent;
        this.session = session;
    }
    @Override protected void init() {
        session.manualControl(true);
        chat = new SmoothSearchBox(10, height - 25, width - 20, 20, "Чат или /команда, Enter — отправить", "Чат бота");
        chat.useFigmaFont();
        addRenderableWidget(chat);
        net.minecraft.client.KeyMapping.releaseAll();
    }
    @Override public void tick() {
        if (chat.isFocused() || !minecraft.isWindowActive() || session.closed()) { session.input(Input.EMPTY); return; }
        session.input(new Input(down(InputConstants.KEY_W), down(InputConstants.KEY_S), down(InputConstants.KEY_A), down(InputConstants.KEY_D),
            down(InputConstants.KEY_SPACE), down(InputConstants.KEY_LSHIFT), down(InputConstants.KEY_LCONTROL)));
        var handler = session.play();
        if (handler == null) return;
        var player = handler.worldPackets().player();
        synchronized (player) {
            var rotation = player.snapshot();
            float yaw = rotation.yRot() + (down(InputConstants.KEY_RIGHT) ? 3 : 0) - (down(InputConstants.KEY_LEFT) ? 3 : 0);
            float pitch = rotation.xRot() + (down(InputConstants.KEY_DOWN) ? 3 : 0) - (down(InputConstants.KEY_UP) ? 3 : 0);
            player.look(yaw, pitch);
        }
    }
    private static boolean down(int key) { return InputConstants.isKeyDown(key); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xff131821);
        var sessions = BotManager.instance().sessions();
        for (int i = 0; i < sessions.size() && i * 100 < width - 90; i++) {
            var bot = sessions.get(i);
            Draw.rounded(graphics, 5 + i * 100, 5, 95, 21, 4, bot == session ? 0xff385a78 : 0xff252c38);
            graphics.text(font, font.plainSubstrByWidth(bot.config().name(), 85), 10 + i * 100, 12, -1);
        }
        graphics.text(font, "Назад [Esc]", width - 82, 12, -1);
        var handler = session.play();
        if (handler != null && handler.worldPackets().world() != null && handler.worldPackets().player().positioned()) {
            graphics.fill(5, 32, width - 5, height - 82, 0xff8cb9da);
            view.extract(graphics, handler.worldPackets().world(), handler.worldPackets().player().snapshot(), 5, 32, width - 10, height - 114);
            graphics.nextStratum();
            graphics.fill(width / 2 - 4, (height - 50) / 2, width / 2 + 5, (height - 50) / 2 + 1, -1);
            graphics.fill(width / 2, (height - 50) / 2 - 4, width / 2 + 1, (height - 50) / 2 + 5, -1);
            var player = handler.worldPackets().player();
            graphics.text(font, "HP " + player.vitals().health() + "   Еда " + player.vitals().food() + "   " + session.status(), 10, height - 76, -1);
            var items = handler.worldPackets().inventory().playerSlots();
            for (int i = 0; i < 9; i++) {
                int x = width / 2 - 90 + i * 20;
                Draw.rounded(graphics, x, height - 58, 19, 20, 2, i == handler.worldPackets().inventory().selectedSlot() ? 0xff657d93 : 0xff252c38);
                graphics.item(items.get(36 + i), x + 1, height - 56);
            }
            var lines = handler.chat();
            for (int i = Math.max(0, lines.size() - 3); i < lines.size(); i++) graphics.text(font, font.plainSubstrByWidth(lines.get(i), width - 30), 10, height - 116 - (lines.size() - 1 - i) * 11, -1);
        } else graphics.centeredText(font, session.status(), width / 2, height / 2, -1);
        graphics.text(font, "WASD · Space · Shift · стрелки / перетаскивание мышью · 1–9 · R: возрождение", 10, height - 36, 0xffaab8c8);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (chat.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN) {
                String message = chat.getValue();
                session.action(handler -> handler.sendChat(message));
                chat.setValue("");
                chat.setFocused(false);
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) { chat.setFocused(false); return true; }
            return super.keyPressed(event);
        }
        if (event.key() >= InputConstants.KEY_1 && event.key() < InputConstants.KEY_1 + 9) {
            int slot = event.key() - InputConstants.KEY_1;
            session.action(handler -> handler.selectSlot(slot));
            return true;
        }
        if (event.key() == InputConstants.KEY_R) { session.action(handler -> handler.respawn()); return true; }
        if (event.key() == InputConstants.KEY_T) { chat.setFocused(true); session.input(Input.EMPTY); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.y() < 29) {
            if (event.x() >= width - 90) { onClose(); return true; }
            var sessions = BotManager.instance().sessions();
            int index = (int) (event.x() - 5) / 100;
            if (index >= 0 && index < sessions.size()) {
                session.manualControl(false);
                session = sessions.get(index);
                session.manualControl(true);
                dragging = false;
                return true;
            }
        }
        if (event.y() >= 32 && event.y() < height - 82) {
            chat.setFocused(false);
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) { dragging = true; moved = false; }
            else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) session.action(handler -> handler.interact(false));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        var handler = session.play();
        if (handler != null) {
            var player = handler.worldPackets().player();
            synchronized (player) { var rotation = player.snapshot(); player.look(rotation.yRot() + (float) dx * .3f, rotation.xRot() + (float) dy * .3f); }
        }
        if (Math.abs(dx) + Math.abs(dy) > .5) moved = true;
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            if (!moved) session.action(handler -> handler.interact(true));
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public void removed() { session.manualControl(false); super.removed(); }
    @Override public void onClose() { session.input(Input.EMPTY); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
