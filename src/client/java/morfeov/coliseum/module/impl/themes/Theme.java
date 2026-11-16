package morfeov.coliseum.module.impl.themes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.util.ColorUtil;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;

@ModuleInfo(id = "theme", name = "Theme", category = Category.THEMES,
    description = "Цвета интерфейса и HUD")
public final class Theme extends Module {
    public record Color(String name, int argb) {}
    public record Preset(String name, int[] colors) {}

    public static final List<Color> COLORS = List.of(
        new Color("Красный", 0xffe53935),
        new Color("Тёмно-красный", 0xff9b1c1c),
        new Color("Синий", 0xff3d6cf2),
        new Color("Тёмно-синий", 0xff1f3a93),
        new Color("Белый", 0xfff2f3f5),
        new Color("Голубой", 0xff6fb6ff),
        new Color("Бирюзовый", 0xff1fd1c1),
        new Color("Розовый", 0xffff6fae),
        new Color("Фиолетовый", 0xff9b5cff),
        new Color("Оранжевый", 0xffff8a2b),
        new Color("Зелёный", 0xff3ccf6e),
        new Color("Тёмно-фиолетовый", 0xff5b2a9e));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ThemeColor[] SLOTS = ThemeColor.values();
    private static Theme instance;

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("coliseum-themes.json");
    private final Path legacyFile = FabricLoader.getInstance().getConfigDir().resolve("dclient-themes.json");
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Coliseum Theme Writer");
        thread.setDaemon(true);
        return thread;
    });
    private final int[] colors = defaults();
    private final List<Preset> presets = new ArrayList<>();
    private final List<Preset> presetsView = Collections.unmodifiableList(presets);
    private String name = "Стандарт";

    public Theme() {
        instance = this;
        setEnabled(true);
        load();
    }

    public static int color(ThemeColor slot) {
        return instance == null ? slot.defaultValue : instance.colors[slot.ordinal()];
    }
    public static void applyGuiPalette(String name, int primary, int secondary, boolean save) {
        if (instance == null) return;
        int accent = ColorUtil.opaque(primary);
        instance.colors[ThemeColor.GUI_ACCENT.ordinal()] = accent;
        instance.colors[ThemeColor.ACCENT.ordinal()] = accent;
        instance.colors[ThemeColor.ACCENT_2.ordinal()] = ColorUtil.opaque(secondary);
        instance.colors[ThemeColor.GUI_PANEL.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff0a0c12, accent, .14f), 205);
        instance.colors[ThemeColor.GUI_HEADER.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff0a0c12, accent, .14f), 225);
        instance.colors[ThemeColor.GUI_FIELD.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff12131a, accent, .06f), 210);
        instance.colors[ThemeColor.GUI_TEXT.ordinal()] = 0xfff7f7f7;
        instance.colors[ThemeColor.GUI_MUTED.ordinal()] = 0xff8591a7;
        instance.name = name;
        if (save) instance.write();
    }
    public static int accent() { return color(ThemeColor.ACCENT); }
    public static String paletteName() { return instance == null ? "" : instance.name; }
    public static List<Preset> presets() { return instance == null ? List.of() : instance.presetsView; }

    public static void set(ThemeColor slot, int value, boolean save) {
        if (instance == null) return;
        instance.colors[slot.ordinal()] = value;
        instance.name = "Своя";
        if (save) instance.save();
    }
    public static void save() { if (instance != null) instance.write(); }

    public static void applyReady(Color color) {
        if (instance == null) return;
        System.arraycopy(ready(color.argb()), 0, instance.colors, 0, SLOTS.length);
        instance.name = color.name();
        instance.write();
    }
    public static void applyPreset(Preset preset) {
        if (instance == null) return;
        System.arraycopy(preset.colors(), 0, instance.colors, 0, SLOTS.length);
        instance.name = preset.name();
        instance.write();
    }
    public static void createPreset(String presetName) {
        if (instance == null) return;
        if (presetName == null || presetName.isBlank()) presetName = "Пресет " + (instance.presets.size() + 1);
        presetName = presetName.strip();
        String finalName = presetName;
        instance.presets.removeIf(preset -> preset.name().equals(finalName));
        instance.presets.add(new Preset(presetName, instance.colors.clone()));
        instance.name = presetName;
        instance.write();
    }
    public static void deletePreset(Preset preset) {
        if (instance != null && instance.presets.remove(preset)) instance.write();
    }

    private static int[] defaults() {
        int[] values = new int[SLOTS.length];
        for (ThemeColor slot : SLOTS) values[slot.ordinal()] = slot.defaultValue;
        return values;
    }
    private static int[] ready(int accent) {
        int[] p = defaults();
        boolean light = ColorUtil.luminance(accent) > .75f;
        p[ThemeColor.ACCENT.ordinal()] = accent;
        p[ThemeColor.ACCENT_2.ordinal()] = light ? ColorUtil.lerp(accent, 0xff9aa3b5, .35f) : ColorUtil.lerp(accent, 0xffffffff, .3f);
        p[ThemeColor.OUTLINE.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff465570, accent, .35f), 0x50);
        p[ThemeColor.BACKGROUND.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff151b29, accent, .07f), 0xeb);
        p[ThemeColor.GLOW.ordinal()] = ColorUtil.alpha(accent, 0x16);
        p[ThemeColor.SCROLL.ordinal()] = ColorUtil.alpha(accent, 0x70);
        p[ThemeColor.TOGGLE_ON.ordinal()] = accent;
        p[ThemeColor.TOGGLE_OFF.ordinal()] = ColorUtil.lerp(0xff121826, accent, .06f);
        p[ThemeColor.SLIDER.ordinal()] = accent;
        p[ThemeColor.SELECTED.ordinal()] = ColorUtil.alpha(ColorUtil.lerp(0xff0f1628, accent, .12f), 0xb0);
        return p;
    }

    private void load() {
        if (!Files.isRegularFile(file) && !Files.isRegularFile(legacyFile)) return;
        Path source = Files.isRegularFile(file) ? file : legacyFile;
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("name")) name = root.get("name").getAsString();
            if (root.has("colors")) readColors(root.getAsJsonObject("colors"), colors);
            if (!root.has("styleRevision")) migrateLegacyGuiColors();
            if (!root.has("styleRevision") || root.get("styleRevision").getAsInt() < 3) {
                if (colors[ThemeColor.GUI_ACCENT.ordinal()] == 0xffa447e3) colors[ThemeColor.GUI_ACCENT.ordinal()] = 0xffa855f7;
                for (ThemeColor slot : new ThemeColor[]{ThemeColor.GUI_PANEL, ThemeColor.GUI_HEADER, ThemeColor.GUI_FIELD, ThemeColor.GUI_TEXT, ThemeColor.GUI_MUTED}) colors[slot.ordinal()] = slot.defaultValue;
            }
            if (!root.has("styleRevision") || root.get("styleRevision").getAsInt() < 4) {
                colors[ThemeColor.GUI_ACCENT.ordinal()] = 0xffe8b1ce;
                colors[ThemeColor.ACCENT.ordinal()] = 0xffe8b1ce;
                colors[ThemeColor.ACCENT_2.ordinal()] = 0xfff0d0e2;
            }
            if (root.has("presets")) for (JsonElement element : root.getAsJsonArray("presets")) {
                JsonObject entry = element.getAsJsonObject();
                int[] values = defaults();
                if (entry.has("colors")) readColors(entry.getAsJsonObject("colors"), values);
                presets.add(new Preset(entry.get("name").getAsString(), values));
            }
        } catch (IOException | RuntimeException e) {
            Coliseum.LOGGER.warn("Could not load Coliseum themes", e);
        }
    }
    private void migrateLegacyGuiColors() {
        ThemeColor[] slots = {ThemeColor.GUI_ACCENT, ThemeColor.GUI_PANEL, ThemeColor.GUI_HEADER,
            ThemeColor.GUI_FIELD, ThemeColor.GUI_TEXT, ThemeColor.GUI_MUTED};
        int[] old = {0xff4a57e9, 0xd0181f3d, 0xb3263158, 0xc4080c20, 0xffffffff, 0xff858ba2};
        for (int i = 0; i < slots.length; i++)
            if (colors[slots[i].ordinal()] == old[i]) colors[slots[i].ordinal()] = slots[i].defaultValue;
    }
    private static void readColors(JsonObject json, int[] target) {
        for (ThemeColor slot : SLOTS) {
            if (!json.has(slot.name())) continue;
            Integer value = ColorUtil.parse(json.get(slot.name()).getAsString());
            if (value != null) target[slot.ordinal()] = value;
        }
    }
    private static JsonObject writeColors(int[] values) {
        JsonObject json = new JsonObject();
        for (ThemeColor slot : SLOTS) json.addProperty(slot.name(), ColorUtil.hex(values[slot.ordinal()]));
        return json;
    }
    private void write() {
        JsonObject root = new JsonObject();
        root.addProperty("styleRevision", 4);
        root.addProperty("name", name);
        root.add("colors", writeColors(colors));
        JsonArray list = new JsonArray();
        for (Preset preset : presets) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", preset.name());
            entry.add("colors", writeColors(preset.colors()));
            list.add(entry);
        }
        root.add("presets", list);
        String json = GSON.toJson(root);
        writer.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Coliseum.LOGGER.warn("Could not save Coliseum themes", e);
            }
        });
    }
}
