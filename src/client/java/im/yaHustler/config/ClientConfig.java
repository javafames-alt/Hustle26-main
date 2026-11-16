package im.yaHustler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import im.yaHustler.Hustle26;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ColorSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.setting.Setting;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final ModuleRegistry registry;
    private final AtomicReference<String> pending = new AtomicReference<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Hustle 26.3 Config Writer");
        thread.setDaemon(true);
        return thread;
    });

    public ClientConfig(Path path, ModuleRegistry registry) {
        this.path = path;
        this.registry = registry;
    }
    public boolean load() {
        if (!Files.isRegularFile(path)) return false;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject()) return false;
            JsonObject modules = root.getAsJsonObject("modules");
            for (Module module : registry.all()) {
                if (!modules.has(module.id()) || !modules.get(module.id()).isJsonObject()) continue;
                JsonObject entry = modules.getAsJsonObject(module.id());
                if (isBoolean(entry, "enabled")) module.setEnabled(entry.get("enabled").getAsBoolean());
                if (isBoolean(entry, "favorite")) module.setFavorite(entry.get("favorite").getAsBoolean());
                if (entry.has("key") && entry.get("key").isJsonPrimitive() && entry.getAsJsonPrimitive("key").isNumber())
                    module.setKey(entry.get("key").getAsInt());
                else module.setKey(-1);
                if (entry.has("settings") && entry.get("settings").isJsonObject())
                    readSettings(module, entry.getAsJsonObject("settings"));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Hustle26.LOGGER.warn("Could not load Hustle 26.3 config; using defaults", e);
            return false;
        }
    }
    private static void readSettings(Module module, JsonObject json) {
        for (Setting<?> setting : module.settings()) {
            if (!json.has(setting.name()) || !json.get(setting.name()).isJsonPrimitive()) continue;
            var value = json.getAsJsonPrimitive(setting.name());
            switch (setting) {
                case BooleanSetting b when value.isBoolean() -> b.setSilently(value.getAsBoolean());
                case NumberSetting n when value.isNumber() -> n.setSilently(value.getAsDouble());
                case im.yaHustler.setting.KeySetting k when value.isNumber() -> k.setSilently(value.getAsInt());
                case im.yaHustler.setting.ItemSetting i when value.isString() -> i.setSilently(value.getAsString());
                case ColorSetting c when value.isNumber() -> c.setSilently(value.getAsInt());
                case ModeSetting m when value.isString() -> m.setSilently(value.getAsString());
                case im.yaHustler.setting.BlockListSetting blocks when value.isString() -> blocks.setSilently(value.getAsString());
                case im.yaHustler.setting.TextSetting text when value.isString() -> text.setSilently(value.getAsString());
                default -> {}
            }
        }
    }
    private static void writeSettings(Module module, JsonObject entry) {
        if (module.settings().isEmpty()) return;
        JsonObject json = new JsonObject();
        for (Setting<?> setting : module.settings()) {
            switch (setting.get()) {
                case Boolean b -> json.addProperty(setting.name(), b);
                case Number n -> json.addProperty(setting.name(), n);
                case String str -> json.addProperty(setting.name(), str);
                default -> {}
            }
        }
        entry.add("settings", json);
    }
    private static boolean isBoolean(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.getAsJsonPrimitive(key).isBoolean();
    }
    private static JsonObject snapshot(ModuleRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject modules = new JsonObject();
        for (Module module : registry.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.enabled());
            entry.addProperty("favorite", module.favorite());
            if (module.hasKey()) entry.addProperty("key", module.key());
            writeSettings(module, entry);
            modules.add(module.id(), entry);
        }
        root.add("modules", modules);
        return root;
    }
    public static void saveProfile(Path destination, ModuleRegistry registry) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(snapshot(registry)), StandardCharsets.UTF_8);
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public void save() {
        JsonObject root = snapshot(registry);
        if (pending.getAndSet(GSON.toJson(root)) == null) writer.execute(this::flush);
    }
    public synchronized void flush() {
        String json = pending.getAndSet(null);
        if (json == null) return;
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { Hustle26.LOGGER.warn("Could not save Hustle 26.3 config", e); }
    }
}
