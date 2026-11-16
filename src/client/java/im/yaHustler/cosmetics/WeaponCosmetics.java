package im.yaHustler.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import im.yaHustler.ui.CosmeticArtwork;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imported weapon artwork uses its own item definitions, without overriding vanilla items. */
public final class WeaponCosmetics {
    private record Skin(int index, String name, String type, Identifier model, Identifier icon) { }
    private static final Map<Integer, Skin> SKINS = load();

    private WeaponCosmetics() { }

    private static Map<Integer, Skin> load() {
        Map<Integer, Skin> result = new LinkedHashMap<>();
        try (InputStream input = WeaponCosmetics.class.getResourceAsStream("/assets/hustle263/cosmetics/weapons/catalog.json")) {
            if (input == null) return result;
            for (var element : JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                int index = object.get("index").getAsInt();
                result.put(index, new Skin(index, object.get("name").getAsString(), object.get("type").getAsString(),
                        Identifier.fromNamespaceAndPath("hustle263", object.get("model").getAsString()),
                        object.get("icon").isJsonNull() ? null : Identifier.fromNamespaceAndPath("hustle263", object.get("icon").getAsString())));
            }
        } catch (Exception exception) {
            LoggerFactory.getLogger("Hustle-Cosmetics").error("Failed to load weapon cosmetics", exception);
        }
        return result;
    }

    public static List<LocalCosmetics.Entry> entries() {
        List<LocalCosmetics.Entry> entries = new ArrayList<>();
        for (Skin skin : SKINS.values()) entries.add(new LocalCosmetics.Entry(skin.index, skin.name, skin.type));
        return entries;
    }

    public static boolean isWeaponType(String type) {
        return "sword".equals(type) || "mace".equals(type);
    }

    public static boolean matchesCategory(String category, String type) {
        return category.isEmpty() || category.equals(type) || ("weapon".equals(category) && isWeaponType(type));
    }

    public static boolean isWeapon(int index) {
        return isWeaponType(LocalCosmetics.type(index));
    }

    public static Identifier icon(int index) {
        Skin skin = SKINS.get(LocalCosmetics.resourceIndex(index));
        return skin == null ? null : skin.icon;
    }

    public static ItemStack preview(int index) {
        Skin skin = SKINS.get(LocalCosmetics.resourceIndex(index));
        if (skin == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack("mace".equals(skin.type) ? Items.MACE : Items.NETHERITE_SWORD);
        stack.set(DataComponents.ITEM_MODEL, skin.model);
        return stack;
    }

    public static void drawIcon(GuiGraphicsExtractor context, int index, float x, float y, float size, int color) {
        Identifier icon = icon(index);
        if (icon != null) {
            CosmeticArtwork.draw(context, icon, x, y, size, size, color);
            return;
        }
        drawPreview(context, index, x, y, size);
    }

    public static void drawPreview(GuiGraphicsExtractor context, int index, float x, float y, float size) {
        // Sword thumbnails use the actual baked model, including animated textures and layers.
        var matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(size / 16, size / 16);
            context.item(preview(index), 0, 0);
        } finally {
            matrices.popMatrix();
        }
    }

    public static ItemStack renderStack(ItemStack stack, ItemOwner context) {
        var player = Minecraft.getInstance().player;
        if (player == null || context == null || context.asLivingEntity() != player || stack.isEmpty()
                || stack.has(DataComponents.CUSTOM_MODEL_DATA)) return stack;
        String type = stack.is(Items.MACE) ? "mace"
                : stack.is(ItemTags.SWORDS) || stack.is(Items.COPPER_SWORD) ? "sword" : "";
        if (type.isEmpty()) return stack;
        Integer selected = LocalCosmetics.selectedForType(type);
        if (selected == null) return stack;
        Skin skin = SKINS.get(LocalCosmetics.resourceIndex(selected));
        // Honor models supplied by a server/resource pack through the item-model component.
        Identifier current = stack.get(DataComponents.ITEM_MODEL);
        Identifier original = stack.getItem().components().get(DataComponents.ITEM_MODEL);
        if (skin == null || (current != null && !current.equals(original))) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponents.ITEM_MODEL, skin.model);
        return copy;
    }
}
