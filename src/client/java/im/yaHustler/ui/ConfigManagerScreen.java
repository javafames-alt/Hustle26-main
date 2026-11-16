package im.yaHustler.ui;

import im.yaHustler.Hustle26;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.ModuleRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Named local snapshots of the existing module/settings config format. */
public final class ConfigManagerScreen extends Screen {
    private final Screen parent;
    private final ModuleRegistry registry;
    private final Path directory = profileDirectory();
    private static Path profileDirectory() {
        Path config = FabricLoader.getInstance().getConfigDir();
        Path current = config.resolve("hustle263/profiles");
        Path legacy = config.resolve("dclient/profiles");
        if (!java.nio.file.Files.exists(current) && java.nio.file.Files.exists(legacy)) {
            try {
                java.nio.file.Files.createDirectories(current.getParent());
                java.nio.file.Files.move(legacy, current);
            } catch (java.io.IOException ignored) {}
        }
        return current;
    }
    private SmoothSearchBox name;
    private List<String> profiles = List.of();
    private String status = "Выберите конфиг или введите имя нового";
    private int x, y, w, h, scroll;

    public ConfigManagerScreen(Screen parent, ModuleRegistry registry) {
        super(Component.literal("Менеджер конфигов"));
        this.parent = parent;
        this.registry = registry;
    }
    @Override protected void init() {
        String previous = name == null ? "" : name.getValue();
        if (name != null) name.setFocused(false);
        w = Math.min(360, width - 24); h = Math.min(270, height - 24);
        x = (width - w) / 2; y = (height - h) / 2;
        name = new SmoothSearchBox(x + 12, y + h - 78, w - 24, 20, "Имя конфига", "Имя конфига");
        name.useFigmaFont(); name.setValue(previous);
        addRenderableWidget(name);
        refresh();
    }
    private void refresh() {
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                profiles = files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(s -> s.endsWith(".json")).map(s -> s.substring(0, s.length() - 5)).sorted().toList();
            }
            scroll = Math.clamp(scroll, 0, Math.max(0, profiles.size() * 21 - (h - 135)));
        } catch (IOException e) { status = "Не удалось прочитать конфиги"; }
    }
    private Path selected() {
        String value = name.getValue().strip();
        if (!value.matches("[\\p{L}\\p{N} _-]{1,48}")) {
            status = "Имя: 1–48 букв, цифр, пробелов, - или _";
            return null;
        }
        return directory.resolve(value + ".json");
    }
    private void saveProfile() {
        Path file = selected(); if (file == null) return;
        try {
            ClientConfig.saveProfile(file, registry);
            status = "Сохранено: " + name.getValue().strip(); refresh();
        } catch (IOException e) { status = "Не удалось сохранить конфиг"; }
    }
    private void loadProfile() {
        Path file = selected(); if (file == null) return;
        if (!Files.isRegularFile(file)) { status = "Такого конфига нет"; return; }
        if (new ClientConfig(file, registry).load()) {
            if (Hustle26.CONFIG != null) Hustle26.CONFIG.save();
            status = "Загружено: " + name.getValue().strip();
        } else status = "Не удалось загрузить конфиг";
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.rounded(g, x, y, w, h, 9, FigmaGuiRenderer.panelColor());
        FigmaFont.drawMedium(g, "Менеджер конфигов", x + 12, y + 12, 0xffffffff, 1f);
        int listTop = y + 36, listBottom = y + h - 99;
        g.enableScissor(x + 10, listTop, x + w - 10, listBottom);
        if (profiles.isEmpty()) FigmaFont.draw(g, "Сохранённых конфигов пока нет", x + 15, listTop + 7, FigmaGuiRenderer.mutedColor(), .72f);
        for (int i = 0; i < profiles.size(); i++) {
            int ry = listTop + i * 21 - scroll;
            if (ry + 19 <= listTop || ry >= listBottom) continue;
            boolean selected = profiles.get(i).equals(name.getValue());
            Draw.rounded(g, x + 12, ry, w - 24, 19, 4,
                selected ? im.yaHustler.util.ColorUtil.alpha(FigmaGuiRenderer.accent(), 0x60) : FigmaGuiRenderer.fieldColor());
            FigmaFont.draw(g, FigmaFont.fit(profiles.get(i), w - 38, .76f), x + 18, ry + 5,
                selected ? FigmaGuiRenderer.activeText() : FigmaGuiRenderer.mutedColor(), .76f);
        }
        g.disableScissor();
        Draw.rounded(g, x + 10, y + h - 80, w - 20, 24, 4, FigmaGuiRenderer.fieldColor());
        String[] buttons = {"Сохранить", "Загрузить", "Назад"};
        int bw = (w - 32) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = x + 10 + i * (bw + 6), by = y + h - 49;
            Draw.rounded(g, bx, by, bw, 21, 4,
                Draw.inside(mx, my, bx, by, bw, 21) ? im.yaHustler.util.ColorUtil.alpha(FigmaGuiRenderer.accent(), 0x80) : FigmaGuiRenderer.fieldColor());
            FigmaFont.draw(g, buttons[i], bx + (bw - FigmaFont.width(buttons[i], .72f)) / 2f, by + 6,
                FigmaGuiRenderer.activeText(), .72f);
        }
        FigmaFont.draw(g, FigmaFont.fit(status, w - 24, .6f), x + 12, y + h - 16, FigmaGuiRenderer.mutedColor(), .6f);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        if (Draw.inside(mx, my, x + 10, y + 36, w - 20, h - 135)) {
            int index = (int) ((my - y - 36 + scroll) / 21);
            if (index >= 0 && index < profiles.size()) { name.setValue(profiles.get(index)); name.setFocused(false); return true; }
        }
        int bw = (w - 32) / 3;
        for (int i = 0; i < 3; i++) if (Draw.inside(mx, my, x + 10 + i * (bw + 6), y + h - 49, bw, 21)) {
            name.setFocused(false);
            if (i == 0) saveProfile(); else if (i == 1) loadProfile(); else onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Draw.inside(mx, my, x + 10, y + 36, w - 20, h - 135)) {
            scroll = Math.clamp(scroll - (int) (vertical * 21), 0, Math.max(0, profiles.size() * 21 - (h - 135)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public void onClose() { if (name != null) name.setFocused(false); minecraft.gui.setScreen(parent); }
    @Override public void removed() { if (name != null) name.setFocused(false); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
