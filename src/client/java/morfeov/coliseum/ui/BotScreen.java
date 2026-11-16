package morfeov.coliseum.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.util.ColorUtil;
import morfeov.coliseum.bot.client.BotClientConfig;
import morfeov.coliseum.bot.client.BotManager;

public final class BotScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("coliseum/bots.json");
    private final Screen parent;
    private final List<Profile> profiles = new ArrayList<>();
    private SmoothSearchBox name, address, proxy, version;
    private int x, y, w, h, selected = -1, scroll;
    private volatile String status = "Офлайн-аккаунты для серверов без лицензии";

    public BotScreen(Screen parent) {
        super(Component.literal("Bot"));
        this.parent = parent;
        load();
    }

    @Override protected void init() {
        String n = name == null ? "" : name.getValue();
        String a = address == null ? "" : address.getValue();
        String p = proxy == null ? "" : proxy.getValue();
        String v = version == null ? "" : version.getValue();
        w = Math.min(480, width - 20); h = Math.min(320, height - 20);
        x = (width - w) / 2; y = (height - h) / 2;
        int left = w / 2, right = w - left - 24;
        name = field(x + left + 8, y + 54, right, "Ник бота", n);
        address = field(x + left + 8, y + 86, right, "Адрес сервера", a);
        proxy = field(x + left + 8, y + 118, right, "Прокси (необязательно)", p);
        version = field(x + left + 8, y + 150, right, "Версия протокола", v);
    }

    private SmoothSearchBox field(int x, int y, int width, String hint, String value) {
        SmoothSearchBox box = new SmoothSearchBox(x, y, width, 20, hint, hint);
        box.useFigmaFont(); box.setValue(value); addRenderableWidget(box); return box;
    }

    private void load() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonArray array = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var element : array) {
                JsonObject o = element.getAsJsonObject();
                profiles.add(new Profile(value(o, "name"), value(o, "address"), value(o, "proxy"), value(o, "version")));
            }
        } catch (Exception e) { profiles.clear(); status = "Не удалось прочитать bots.json"; }
    }

    private static String value(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private boolean save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(profiles), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) { status = "Не удалось сохранить bots.json"; return false; }
    }

    private void select(int index) {
        selected = index;
        Profile p = profiles.get(index);
        name.setValue(p.name); address.setValue(p.address); proxy.setValue(p.proxy); version.setValue(p.version);
        var session = BotManager.instance().find(p.name);
        status = session == null ? "Профиль выбран" : session.status();
    }

    private void saveProfile() {
        String n = name.getValue().trim();
        if (!n.matches("[A-Za-z0-9_]{3,16}")) { status = "Ник: 3–16 латинских букв, цифр или _"; return; }
        try { morfeov.coliseum.bot.net.BotProxy.parse(proxy.getValue()); }
        catch (IllegalArgumentException ex) { status = "Некорректный адрес прокси"; return; }
        for (int i = 0; i < profiles.size(); i++) {
            if (i != selected && profiles.get(i).name.equalsIgnoreCase(n)) { status = "Такой ник уже добавлен"; return; }
        }
        Profile p = new Profile(n, address.getValue().trim(), proxy.getValue().trim(), version.getValue().trim());
        if (selected < 0) { profiles.add(p); selected = profiles.size() - 1; }
        else profiles.set(selected, p);
        if (save()) status = "Профиль сохранён";
    }

    private void connect() {
        try {
            String target = address.getValue().trim();
            if (target.isEmpty() && minecraft.getCurrentServer() != null) target = minecraft.getCurrentServer().ip;
            if (target.isEmpty()) throw new IllegalArgumentException("Укажи адрес сервера");
            var server = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(target);
            String protocol = version.getValue().trim();
            var config = BotClientConfig.offline(name.getValue().trim(), server.getHost(), server.getPort(), proxy.getValue().trim(), protocol.isEmpty() ? -1 : Integer.parseInt(protocol));
            BotManager.instance().connect(config);
            status = "Подключение";
        } catch (IllegalArgumentException | IllegalStateException error) { status = error.getMessage(); }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.rounded(g, x, y, w, h, 9, FigmaGuiRenderer.panelColor());
        int left = w / 2;
        FigmaFont.drawMedium(g, "Bots", x + 12, y + 12, FigmaGuiRenderer.activeText(), 1f);
        FigmaFont.draw(g, "Профиль бота", x + left + 8, y + 34, FigmaGuiRenderer.mutedColor(), .75f);
        int top = y + 38, bottom = y + h - 50;
        g.enableScissor(x + 8, top, x + left - 5, bottom);
        if (profiles.isEmpty()) FigmaFont.draw(g, "Нет сохранённых ботов", x + 12, top + 8, FigmaGuiRenderer.mutedColor(), .7f);
        for (int i = 0; i < profiles.size(); i++) {
            int row = top + i * 25 - scroll;
            if (row + 22 <= top || row >= bottom) continue;
            Draw.rounded(g, x + 8, row, left - 16, 22, 4, i == selected ? ColorUtil.alpha(FigmaGuiRenderer.accent(), 0x60) : FigmaGuiRenderer.fieldColor());
            FigmaFont.draw(g, FigmaFont.fit(profiles.get(i).name, left - 30, .75f), x + 14, row + 7, FigmaGuiRenderer.activeText(), .75f);
        }
        g.disableScissor();
        for (int i = 0; i < 4; i++) Draw.rounded(g, x + left + 6, y + 52 + i * 32, w - left - 20, 24, 4, FigmaGuiRenderer.fieldColor());
        String[] actions = {"Подключить", "Отключить"};
        int actionWidth = (w - left - 26) / 2;
        for (int i = 0; i < actions.length; i++) {
            int bx = x + left + 8 + i * (actionWidth + 6);
            Draw.rounded(g, bx, y + 214, actionWidth, 22, 4, Draw.inside(mx, my, bx, y + 214, actionWidth, 22) ? ColorUtil.alpha(FigmaGuiRenderer.accent(), 0x80) : FigmaGuiRenderer.fieldColor());
            FigmaFont.draw(g, actions[i], bx + 4, y + 221, FigmaGuiRenderer.activeText(), .65f);
        }
        Draw.rounded(g, x + left + 8, y + 243, w - left - 20, 22, 4, FigmaGuiRenderer.fieldColor());
        FigmaFont.draw(g, "Открыть управление", x + left + 12, y + 250, FigmaGuiRenderer.activeText(), .7f);
        String[] buttons = {"Новый", "Сохранить", "Удалить", "Назад"};
        int bw = (w - 38) / 4;
        for (int i = 0; i < buttons.length; i++) {
            int bx = x + 10 + i * (bw + 6), by = y + h - 40;
            Draw.rounded(g, bx, by, bw, 21, 4, Draw.inside(mx, my, bx, by, bw, 21) ? ColorUtil.alpha(FigmaGuiRenderer.accent(), 0x80) : FigmaGuiRenderer.fieldColor());
            FigmaFont.draw(g, buttons[i], bx + (bw - FigmaFont.width(buttons[i], .7f)) / 2f, by + 6, FigmaGuiRenderer.activeText(), .7f);
        }
        var session = BotManager.instance().find(name.getValue().trim());
        String message = session != null && !session.closed() ? session.status() : status;
        if (session != null && session.closed() && status.equals("Подключение")) message = session.status();
        FigmaFont.draw(g, FigmaFont.fit(message, w - left - 20, .6f), x + left + 8, y + 184, FigmaGuiRenderer.mutedColor(), .6f);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(e, doubleClick);
        int left = w / 2;
        int actionWidth = (w - left - 26) / 2;
        if (Draw.inside(e.x(), e.y(), x + left + 8, y + 243, w - left - 20, 22)) {
            var session = BotManager.instance().find(name.getValue().trim());
            if (session == null) status = "Сначала подключи бота";
            else minecraft.gui.setScreen(new BotControlScreen(this, session));
            return true;
        }
        if (Draw.inside(e.x(), e.y(), x + left + 8, y + 214, actionWidth, 22)) { connect(); return true; }
        if (Draw.inside(e.x(), e.y(), x + left + 14 + actionWidth, y + 214, actionWidth, 22)) { BotManager.instance().disconnect(name.getValue().trim()); status = "Отключён"; return true; }
        if (Draw.inside(e.x(), e.y(), x + 8, y + 38, left - 16, h - 88)) {
            int i = (int)(e.y() - y - 38 + scroll) / 25;
            if (i >= 0 && i < profiles.size()) { select(i); return true; }
        }
        int bw = (w - 38) / 4;
        for (int i = 0; i < 4; i++) if (Draw.inside(e.x(), e.y(), x + 10 + i * (bw + 6), y + h - 40, bw, 21)) {
            if (i == 0) { selected = -1; name.setValue(""); address.setValue(minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().ip); proxy.setValue(""); version.setValue(""); status = "Новый профиль"; }
            else if (i == 1) saveProfile();
            else if (i == 2 && selected >= 0) { profiles.remove(selected); selected = -1; name.setValue(""); address.setValue(""); proxy.setValue(""); version.setValue(""); if (save()) status = "Профиль удалён"; }
            else if (i == 3) onClose();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Draw.inside(mx, my, x + 8, y + 38, w / 2 - 16, h - 88)) {
            scroll = Math.clamp(scroll - (int)(vertical * 25), 0, Math.max(0, profiles.size() * 25 - (h - 88)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private record Profile(String name, String address, String proxy, String version) {}
}
