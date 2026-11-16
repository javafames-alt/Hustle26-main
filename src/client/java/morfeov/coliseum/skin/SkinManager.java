package morfeov.coliseum.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public final class SkinManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CHECK_TICKS = 40;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("labymod-neo");
    private static final Path SKINS = ROOT.resolve("skins");
    private static final Path SETTINGS = ROOT.resolve("settings.json");

    private static final List<SkinEntry> entries = new ArrayList<>();
    private static final List<SkinEntry> view = Collections.unmodifiableList(entries);
    private static String selected = "";
    private static boolean layers3d = true;
    private static boolean self = true;
    private static boolean previewing;
    private static SkinEntry preview;
    private static String signature = "";
    private static int timer;
    private static int counter;
    private static PlayerSkin cachedOriginal, cachedSkin, lastOriginal;
    private static SkinEntry cachedEntry;
    private record SkinFile(Path path, long stamp) {}
    private record FolderScan(List<SkinFile> files, String signature) {}
    private static java.util.concurrent.CompletableFuture<FolderScan> pendingScan;

    private SkinManager() {}

    public static void initialize() {
        try {
            Files.createDirectories(SKINS);
        } catch (IOException ignored) {}
        loadSettings();
    }

    public static Path folder() { return SKINS; }
    public static List<SkinEntry> entries() { return view; }
    public static boolean layers3d() { return layers3d; }
    public static boolean self() { return self; }

    public static void setSelf(boolean value) {
        self = value;
        cachedOriginal = null;
        saveSettings();
    }
    public static String selectedName() { return selected; }

    public static void setLayers3d(boolean value) {
        layers3d = value;
        saveSettings();
    }

    public static void tick(Minecraft client) {
        if (pendingScan != null && pendingScan.isDone()) {
            FolderScan result = pendingScan.getNow(null);
            pendingScan = null;
            if (result != null) applyScan(result, false);
        }
        if (++timer < CHECK_TICKS) return;
        timer = 0;
        // Directory enumeration and file metadata can block on antivirus or a
        // slow drive. Only the resulting texture updates belong on this thread.
        if (pendingScan == null) pendingScan = java.util.concurrent.CompletableFuture
            .supplyAsync(SkinManager::scanFolder).exceptionally(error -> null);
    }

    public static void refresh(boolean force) {
        // A manual refresh supersedes an older background snapshot.
        pendingScan = null;
        FolderScan result = scanFolder();
        if (result != null) applyScan(result, force);
    }

    private static FolderScan scanFolder() {
        List<Path> files = new ArrayList<>();
        List<SkinFile> snapshots = new ArrayList<>();
        StringBuilder sign = new StringBuilder();
        try (Stream<Path> stream = Files.list(SKINS)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted()
                .forEach(files::add);
            for (Path file : files) {
                var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
                long modified = attributes.lastModifiedTime().toMillis(), size = attributes.size();
                snapshots.add(new SkinFile(file, modified ^ size));
                sign.append(file.getFileName()).append(':').append(modified).append(':').append(size).append(';');
            }
        } catch (IOException exception) {
            return null;
        }
        return new FolderScan(List.copyOf(snapshots), sign.toString());
    }

    private static void applyScan(FolderScan scan, boolean force) {
        String current = scan.signature();
        if (!force && current.equals(signature)) return;
        signature = current;
        Map<String, SkinEntry> old = new HashMap<>();
        for (SkinEntry entry : entries) old.put(entry.file(), entry);
        List<SkinEntry> fresh = new ArrayList<>();
        for (SkinFile snapshot : scan.files()) {
            Path file = snapshot.path();
            String name = file.getFileName().toString();
            long stamp = snapshot.stamp();
            SkinEntry existing = old.get(name);
            if (existing != null && existing.stamp() == stamp && !force) {
                fresh.add(existing);
                old.remove(name);
                continue;
            }
            SkinEntry loaded = load(file, name, stamp);
            if (loaded != null) fresh.add(loaded);
        }
        for (SkinEntry entry : old.values())
            if (!fresh.contains(entry)) Minecraft.getInstance().getTextureManager().release(entry.texture());
        entries.clear();
        entries.addAll(fresh);
        if (preview != null && !entries.contains(preview)) preview = null;
        cachedOriginal = null;
    }

    private static SkinEntry load(Path file, String name, long stamp) {
        NativeImage image;
        try {
            image = NativeImage.read(Files.readAllBytes(file));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
        int w = image.getWidth(), h = image.getHeight();
        if (w < 64 || w % 64 != 0 || (h != w && h != w / 2)) {
            image.close();
            return null;
        }
        if (h == w / 2) image = legacy(image);
        int[] pixels = new int[w * w];
        for (int y = 0; y < w; y++)
            for (int x = 0; x < w; x++) pixels[y * w + x] = image.getPixel(x, y);
        int scale = w / 64;
        String lower = name.toLowerCase(Locale.ROOT);
        boolean slim = lower.contains("slim") || lower.contains("alex") || (pixels[20 * scale * w + 54 * scale] >>> 24) == 0;
        Identifier id = Identifier.fromNamespaceAndPath("coliseum", "skins/" + (++counter) + "_" + Integer.toHexString(name.hashCode() & 0x7fffffff));
        String label = "coliseum skin " + name;
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
        String display = name.substring(0, name.length() - 4);
        return new SkinEntry(name, display, id, pixels, w, slim, stamp);
    }

    private static NativeImage legacy(NativeImage source) {
        int w = source.getWidth(), s = w / 64;
        NativeImage image = new NativeImage(w, w, true);
        for (int y = 0; y < w / 2; y++)
            for (int x = 0; x < w; x++) image.setPixel(x, y, source.getPixel(x, y));
        copyMirrored(source, image, 0, 16, 16, 48, s);
        copyMirrored(source, image, 40, 16, 32, 48, s);
        source.close();
        return image;
    }

    private static void copyMirrored(NativeImage from, NativeImage to, int sx, int sy, int dx, int dy, int s) {
        for (int y = 0; y < 16 * s; y++)
            for (int x = 0; x < 16 * s; x++) to.setPixel(dx * s + x, dy * s + y, from.getPixel(sx * s + x, sy * s + y));
    }

    public static SkinEntry selectedEntry() {
        if (selected.isEmpty()) return null;
        for (SkinEntry entry : entries) if (entry.file().equals(selected)) return entry;
        return null;
    }

    public static void select(SkinEntry entry) {
        selected = entry == null ? "" : entry.file();
        cachedOriginal = null;
        saveSettings();
    }

    public static void preview(boolean active, SkinEntry entry) {
        previewing = active;
        preview = entry;
    }

    public static SkinEntry active() {
        if (previewing) return preview;
        return self ? selectedEntry() : null;
    }

    public static PlayerSkin apply(AbstractClientPlayer player, PlayerSkin original) {
        if (player != Minecraft.getInstance().player) return original;
        lastOriginal = original;
        SkinEntry entry = active();
        if (entry == null) return original;
        if (original == cachedOriginal && entry == cachedEntry && cachedSkin != null) return cachedSkin;
        ClientAsset.ResourceTexture body = new ClientAsset.ResourceTexture(entry.texture(), entry.texture());
        cachedSkin = new PlayerSkin(body, original.cape(), original.elytra(),
            entry.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, original.secure());
        cachedOriginal = original;
        cachedEntry = entry;
        return cachedSkin;
    }

    public static boolean isListening() {
        return previewing || self && !selected.isEmpty();
    }

    public static PlayerSkin originalSkin() {
        if (isListening() || Minecraft.getInstance().player == null) return lastOriginal;
        return Minecraft.getInstance().player.getSkin();
    }

    public static SkinEntry layersFor(AbstractClientPlayer player) {
        if (!layers3d || player != Minecraft.getInstance().player) return null;
        return active();
    }

    private static void loadSettings() {
        if (!Files.isRegularFile(SETTINGS)) return;
        try (Reader reader = Files.newBufferedReader(SETTINGS, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("selected")) selected = root.get("selected").getAsString();
            if (root.has("layers3d")) layers3d = root.get("layers3d").getAsBoolean();
            if (root.has("self")) self = root.get("self").getAsBoolean();
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void saveSettings() {
        JsonObject root = new JsonObject();
        root.addProperty("selected", selected);
        root.addProperty("layers3d", layers3d);
        root.addProperty("self", self);
        try {
            Files.createDirectories(ROOT);
            try (Writer writer = Files.newBufferedWriter(SETTINGS, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {}
    }
}
