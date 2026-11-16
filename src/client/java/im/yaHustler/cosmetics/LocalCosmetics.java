package im.yaHustler.cosmetics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import im.yaHustler.cosmetics.loader.CosmeticLoader;
import im.yaHustler.cosmetics.model.CosmeticModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalCosmetics {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hustle-Cosmetics");
    private static final int MAX_SCAN_INDEX = 256;
    private static final List<Entry> ENTRIES = loadEntries();
    private static final LinkedHashMap<String, Integer> SELECTED = new LinkedHashMap<>();
    private static final Map<Integer, CosmeticModel> MODELS = new HashMap<>();

    static {
        load();
    }

    private LocalCosmetics() {
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static String name(int i) {
        return entry(i).name;
    }

    public static String type(int i) {
        return entry(i).type;
    }

    public static int resourceIndex(int i) {
        return entry(i).index;
    }

    public static Identifier texture(int i) {
        if (entry(i).importedPath != null) {
            return Identifier.fromNamespaceAndPath("hustle263", entry(i).importedPath + "texture.png");
        }
        return Identifier.fromNamespaceAndPath("pulse", "textures/cosmetics/cosmetic_" + entry(i).index + ".png");
    }

    public static Identifier icon(int i) {
        if (WeaponCosmetics.isWeapon(i)) {
            Identifier icon = WeaponCosmetics.icon(i);
            return icon == null ? Identifier.fromNamespaceAndPath("minecraft", "textures/item/netherite_sword.png") : icon;
        }
        if (entry(i).importedPath != null) {
            return Identifier.fromNamespaceAndPath("hustle263", entry(i).importedPath + "preview.png");
        }
        return Identifier.fromNamespaceAndPath("pulse", "textures/cosmetics/item_" + entry(i).index + ".png");
    }

    public static int selected() {
        return SELECTED.isEmpty() ? -1 : SELECTED.values().stream().reduce((a, b) -> b).orElse(-1);
    }

    public static boolean isSelected(int i) {
        return SELECTED.containsValue(i);
    }

    public static Integer selectedForType(String type) {
        return SELECTED.get(type);
    }

    public static String selectedType() {
        int i = selected();
        return i < 0 ? "" : type(i);
    }

    public static Identifier selectedTexture() {
        int i = selected();
        return i < 0 ? null : texture(i);
    }

    public static Identifier selectedCapeTexture() {
        Integer i = SELECTED.get("cape");
        return i == null ? null : texture(i);
    }

    public static List<Integer> selectedIndices() {
        return List.copyOf(SELECTED.values());
    }

    public static JsonObject serializeSelection() {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Integer> entry : SELECTED.entrySet()) {
            object.addProperty(entry.getKey(), entry(entry.getValue()).index);
        }
        return object;
    }

    public static void deserializeSelection(JsonObject object) {
        SELECTED.clear();
        MODELS.clear();
        if (object == null) {
            return;
        }

        for (String type : object.keySet()) {
            if (object.has(type) && !object.get(type).isJsonNull()) {
                int index = indexByResourceIndex(object.get(type).getAsInt());
                if (index >= 0) {
                    SELECTED.put(type, index);
                    if (!"cape".equals(type) && !WeaponCosmetics.isWeaponType(type)) {
                        model(index);
                    }
                }
            }
        }
    }

    public static void toggle(int i) {
        String type = type(i);
        Integer old = SELECTED.get(type);
        if (old != null && old == i) {
            SELECTED.remove(type);
        } else {
            SELECTED.put(type, i);
            if (!"cape".equals(type) && !WeaponCosmetics.isWeaponType(type)) {
                model(i);
            }
        }
        save();
    }

    public static void unequipAll() {
        SELECTED.clear();
        MODELS.clear();
        save();
    }

    public static CosmeticModel modelFor(int i) {
        return model(i);
    }

    public static boolean isHustle263(int i) {
        return entry(i).importedPath != null;
    }

    public static int hustle263Id(int i) {
        return entry(i).index - HustleCosmetics.ID_OFFSET;
    }

    private static CosmeticModel model(int i) {
        if (WeaponCosmetics.isWeapon(i)) return null;
        int resourceIndex = entry(i).index;
        return MODELS.computeIfAbsent(resourceIndex, k -> {
            if (entry(i).importedPath != null) {
                return HustleCosmetics.loadModel(entry(i), texture(i));
            }
            try (InputStream in = LocalCosmetics.class.getResourceAsStream("/assets/pulse/cosmetics/models/cosmetic_" + k + ".json")) {
                return in == null ? null : CosmeticLoader.getInstance().loadFromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), texture(i), k);
            } catch (Exception e) {
                LOGGER.error("Failed to load cosmetic model for index " + k, e);
                return null;
            }
        });
    }

    private static Entry entry(int i) {
        if (i < 0 || i >= ENTRIES.size()) {
            throw new IndexOutOfBoundsException("Cosmetic index " + i + " out of " + ENTRIES.size());
        }
        return ENTRIES.get(i);
    }

    public static int indexByResourceIndex(int resourceIndex) {
        resourceIndex = HustleCosmetics.resolveResourceIndex(resourceIndex);
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).index == resourceIndex) {
                return i;
            }
        }
        return -1;
    }

    private static List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < MAX_SCAN_INDEX; i++) {
            if (isExcludedIndex(i)) {
                continue;
            }
            String path = "/assets/pulse/cosmetics/models/cosmetic_" + i + ".json";
            try (InputStream in = LocalCosmetics.class.getResourceAsStream(path)) {
                if (in != null) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                    String rawName = object.has("name") ? object.get("name").getAsString() : "Cosmetic " + (i + 1);
                    if (isExcludedName(rawName)) {
                        continue;
                    }
                    String rawType = object.has("type") ? object.get("type").getAsString() : "";
                    int pos = object.has("pos") ? object.get("pos").getAsInt() : -1;
                    String disp = displayName(rawName, i);
                    entries.add(new Entry(i, disp, classifyType(i, rawName, rawType, pos)));
                }
            } catch (Exception ignored) {
            }
        }

        entries.sort(Comparator.comparingInt(entry -> entry.index));
        entries.addAll(HustleCosmetics.loadEntries());
        entries.addAll(im.yaHustler.cosmetics.extra.ExtraCosmetics.entries());
        entries.addAll(WeaponCosmetics.entries());
        return List.copyOf(entries);
    }

    private static boolean isExcludedIndex(int index) {
        return index == 21
                || index == 23
                || index == 25;
    }

    private static boolean isExcludedName(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase().trim();
        return lower.equals("easter wings")
                || lower.equals("baby dragon wings")
                || lower.equals("babydragon wings")
                || lower.equals("spider wings");
    }

    private static String displayName(String rawName, int index) {
        String name = rawName == null || rawName.isBlank() ? "Cosmetic " + (index + 1) : rawName;
        if (name.startsWith("pulse_")) {
            name = name.substring("pulse_".length());
        }
        return name.replace('_', ' ').trim();
    }

    public static String classifyType(int index, String name, String rawType, int pos) {
        if (rawType != null && !rawType.isBlank()) {
            return rawType.trim().toLowerCase();
        }

        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("cape")) {
            return "cape";
        } else if (lower.contains("wing")) {
            return "wings";
        } else if (lower.contains("pet") || lower.contains("bee") || lower.contains("radish")) {
            return "pet";
        } else if (lower.contains("hat") || lower.contains("nimb") || lower.contains("crown") || pos == 2) {
            return "hat";
        } else if (index <= 13) {
            return "cape";
        } else if (index <= 26) {
            return "wings";
        } else if (index <= 37) {
            return "bodywear";
        } else if (index <= 49) {
            return "pet";
        } else {
            return "bodywear";
        }
    }

    public static void save() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("hustle263/cosmetics.json");
            Files.createDirectories(path.getParent());
            JsonObject obj = serializeSelection();
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (Exception e) {
            LOGGER.error("Failed to save cosmetics config", e);
        }
    }

    public static void load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("hustle263/cosmetics.json");
            if (Files.exists(path)) {
                String json = Files.readString(path);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                deserializeSelection(obj);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load cosmetics config", e);
        }
    }

    public static final class Entry {
        public final int index;
        public final String name;
        public final String type;
        public final String importedPath;

        public Entry(int index, String name, String type) {
            this(index, name, type, null);
        }

        public Entry(int index, String name, String type, String importedPath) {
            this.index = index;
            this.name = name;
            this.type = type;
            this.importedPath = importedPath;
        }
    }
}
