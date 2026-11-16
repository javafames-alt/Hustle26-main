package morfeov.coliseum.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import morfeov.coliseum.cosmetics.loader.CosmeticLoader;
import morfeov.coliseum.cosmetics.model.CosmeticModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Local Coliseum catalog. IDs have a separate range to preserve existing selections. */
public final class ColiseumCosmetics {
    public static final int ID_OFFSET = 10_000;
    private static final Logger LOGGER = LoggerFactory.getLogger("Coliseum-Cosmetics");
    private static final Map<Integer, JsonObject> TEXTURE_ANIMATIONS = new HashMap<>();
    private static final Map<Integer, Integer> REPLACEMENTS = loadReplacements();

    private ColiseumCosmetics() {}

    private static Map<Integer, Integer> loadReplacements() {
        Map<Integer, Integer> replacements = new HashMap<>();
        try (InputStream input = ColiseumCosmetics.class.getResourceAsStream(
                "/assets/coliseum/cosmetics/coliseum/replacements.json")) {
            if (input == null) return replacements;
            JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : json.keySet()) replacements.put(Integer.parseInt(id), json.get(id).getAsInt());
        } catch (Exception exception) {
            LOGGER.error("Failed to load Coliseum cosmetic replacements", exception);
        }
        return replacements;
    }

    /** Translate a removed Coliseum duplicate to the existing Pulse model in saved loadouts. */
    public static int resolveResourceIndex(int id) {
        return REPLACEMENTS.getOrDefault(id, id);
    }

    public static List<LocalCosmetics.Entry> loadEntries() {
        List<LocalCosmetics.Entry> entries = new ArrayList<>();
        try (InputStream input = ColiseumCosmetics.class.getResourceAsStream(
                "/assets/coliseum/cosmetics/coliseum/catalog.json")) {
            if (input == null) return entries;
            for (JsonElement element : JsonParser.parseString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                entries.add(new LocalCosmetics.Entry(ID_OFFSET + entry.get("id").getAsInt(),
                        entry.get("name").getAsString() + " [Coliseum]", entry.get("type").getAsString(),
                        entry.get("path").getAsString()));
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load local Coliseum catalog", exception);
        }
        return entries;
    }

    public static CosmeticModel loadModel(LocalCosmetics.Entry entry, Identifier texture) {
        try (InputStream input = ColiseumCosmetics.class.getResourceAsStream(
                "/assets/coliseum/" + entry.importedPath + "model.json")) {
            if (input == null) return null;
            JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("textureAnimation")) {
                JsonObject animation = json.getAsJsonObject("textureAnimation");
                TEXTURE_ANIMATIONS.put(entry.index, animation);
                // Geometry UVs describe one frame; the PNG contains the entire vertical strip.
                json.getAsJsonObject("model").getAsJsonArray("minecraft:geometry").get(0)
                        .getAsJsonObject().getAsJsonObject("description")
                        .addProperty("texture_height", animation.get("frameHeight").getAsInt());
            }
            return CosmeticLoader.getInstance().loadFromJson(json, texture, entry.index);
        } catch (Exception exception) {
            LOGGER.error("Failed to load Coliseum cosmetic {}", entry.name, exception);
            return null;
        }
    }

    public static JsonObject textureAnimation(int id) {
        return TEXTURE_ANIMATIONS.get(id);
    }
}
