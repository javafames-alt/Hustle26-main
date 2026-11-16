package morfeov.coliseum.font;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class AtlasFont {
    private final boolean crisp;
    private final boolean textFace;
    public static final float BASE = 11f;
    private static final int TABLE_SIZE = 0x3000, WIDTH_CACHE_LIMIT = 2048;
    private static final long EMPTY = -1L;
    private final Identifier texture;
    private final int atlasWidth, atlasHeight;
    private final float capHeight, ascender;
    private final Glyph[] table = new Glyph[TABLE_SIZE];
    private final Map<Integer, Glyph> extra = new HashMap<>();
    // Keep frequently drawn HUD/GUI labels when changing metrics fill the cache.
    // Clearing everything at once forced all visible labels to be measured again.
    private final Map<String, Float> widths = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };
    private final Glyph fallback;
    private long[] kernKeys = {EMPTY};
    private float[] kernValues = {0f};
    private int kernMask;

    public AtlasFont(String face) {
        this(face, false);
    }
    public AtlasFont(String face, boolean crisp) {
        this.crisp = crisp;
        this.textFace = crisp && face.startsWith("coliseum_");
        texture = Identifier.fromNamespaceAndPath("coliseum", "textures/font/" + face + ".png");
        String path = "/assets/coliseum/textures/font/" + face + ".json";
        try (var stream = AtlasFont.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing font metrics: " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            JsonObject metrics = root.getAsJsonObject("metrics");
            ascender = metrics != null && metrics.has("ascender") ? metrics.get("ascender").getAsFloat() : 1f;
            atlasWidth = atlas.get("width").getAsInt();
            atlasHeight = atlas.get("height").getAsInt();
            boolean bottom = !atlas.has("yOrigin") || "bottom".equals(atlas.get("yOrigin").getAsString());
            float pad = atlas.get("distanceRange").getAsFloat() * .5f / atlas.get("size").getAsFloat();
            for (var element : root.getAsJsonArray("glyphs")) {
                JsonObject g = element.getAsJsonObject();
                int cp = g.get("unicode").getAsInt();
                float advance = g.get("advance").getAsFloat();
                if (!g.has("planeBounds") || !g.has("atlasBounds")) {
                    put(cp, Glyph.blank(advance));
                    continue;
                }
                JsonObject p = g.getAsJsonObject("planeBounds"), a = g.getAsJsonObject("atlasBounds");
                float top = a.get("top").getAsFloat(), bot = a.get("bottom").getAsFloat();
                float v0 = bottom ? atlasHeight - top : top, v1 = bottom ? atlasHeight - bot : bot;
                put(cp, Glyph.of(advance, p.get("left").getAsFloat(), p.get("top").getAsFloat(),
                    p.get("right").getAsFloat(), p.get("bottom").getAsFloat(),
                    a.get("left").getAsFloat(), Math.min(v0, v1), a.get("right").getAsFloat(), Math.max(v0, v1)));
            }
            Map<Long, Float> kerning = new HashMap<>();
            if (root.has("kerning")) for (var element : root.getAsJsonArray("kerning")) {
                JsonObject k = element.getAsJsonObject();
                kerning.put(pair(k.get("unicode1").getAsInt(), k.get("unicode2").getAsInt()), k.get("advance").getAsFloat());
            }
            if (!face.startsWith("coliseum_")) kerning.put(pair('K', 'e'), -.085f);
            buildKerning(kerning);
            Glyph question = find('?');
            // UI faces must not turn unsupported punctuation or music metadata into question marks.
            fallback = textFace ? Glyph.blank(find(' ') != null ? find(' ').advance : .25f)
                : question != null ? question : Glyph.blank(.5f);
            Glyph h = find('H');
            capHeight = h != null && h.visible ? h.top - pad : .7f;
        } catch (Exception e) { throw new IllegalStateException("Could not load font " + face, e); }
    }
    private void put(int cp, Glyph glyph) {
        if (cp >= 0 && cp < TABLE_SIZE) table[cp] = glyph;
        else extra.put(cp, glyph);
    }
    private Glyph find(int cp) {
        return cp >= 0 && cp < TABLE_SIZE ? table[cp] : extra.get(cp);
    }
    private void buildKerning(Map<Long, Float> kerning) {
        int capacity = Integer.highestOneBit(Math.max(4, kerning.size() * 2 - 1)) << 1;
        kernKeys = new long[capacity];
        kernValues = new float[capacity];
        kernMask = capacity - 1;
        Arrays.fill(kernKeys, EMPTY);
        kerning.forEach((key, value) -> {
            int slot = slot(key);
            while (kernKeys[slot] != EMPTY) slot = slot + 1 & kernMask;
            kernKeys[slot] = key;
            kernValues[slot] = value;
        });
    }
    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ h >>> 32) & kernMask;
    }
    private float kerning(int previous, int cp) {
        if (previous < 0) return 0f;
        long key = pair(previous, cp);
        for (int slot = slot(key);; slot = slot + 1 & kernMask) {
            long stored = kernKeys[slot];
            if (stored == key) return kernValues[slot];
            if (stored == EMPTY) return 0f;
        }
    }
    private static long pair(int a, int b) { return ((long) a << 32) | (b & 0xffffffffL); }
    private int supportedCodePoint(int cp) {
        if (find(cp) != null || !textFace) return cp;
        return switch (cp) {
            case 0x00ab, 0x00bb, 0x201c, 0x201d, 0x201e -> '"';
            case 0x2018, 0x2019, 0x201a, 0x2032 -> '\'';
            case 0x00b7, 0x2022, 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2212 -> '-';
            case 0x2026 -> '.';
            case 0x00d7 -> 'x';
            case 0x2192 -> '>';
            case 0x2190 -> '<';
            case 0x00a0, 0x2007, 0x2009, 0x202f -> ' ';
            default -> -1;
        };
    }
    private Glyph glyph(int cp) {
        Glyph glyph = find(supportedCodePoint(cp));
        return glyph != null ? glyph : fallback;
    }
    public boolean hasGlyph(int cp) { return find(cp) != null; }
    public float width(String text) {
        text = morfeov.coliseum.module.impl.misc.NameProtect.protect(text);
        Float cached = widths.get(text);
        if (cached != null) return cached;
        float current = 0, maximum = 0;
        int previous = -1;
        for (int index = 0; index < text.length();) {
            int cp = text.codePointAt(index); index += Character.charCount(cp);
            if (cp == '\n') { maximum = Math.max(maximum, current); current = 0; previous = -1; continue; }
            cp = supportedCodePoint(cp);
            current += kerning(previous, cp) + glyph(cp).advance;
            previous = cp;
        }
        float width = Math.max(maximum, current) * BASE;
        widths.put(text, width);
        return width;
    }
    public float cap() { return capHeight; }
    public float widthAt(String text, float size) { return width(text) * size / BASE; }
    public void drawAt(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
        draw(g, text, x, y + (ascender - capHeight) * size, color, size / BASE);
    }
    public void draw(GuiGraphicsExtractor g, String text, float x, float y, int color, float scale) {
        text = morfeov.coliseum.module.impl.misc.NameProtect.protect(text);
        color = morfeov.coliseum.ui.GuiOpacity.color(color);
        if (text.isEmpty() || (color >>> 24) == 0) return;
        float size = BASE * scale, cursor = 0, lineY = 0;
        int previous = -1;
        var pose = g.pose();
        var pipeline = textFace ? MsdfPipeline.text() : crisp ? MsdfPipeline.crisp() : MsdfPipeline.get();
        // Keep fractional origins during scale/slide animations. Rounding the
        // run every frame made it jump between neighbouring framebuffer pixels.
        var image = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(texture);
        for (int index = 0; index < text.length();) {
            int cp = text.codePointAt(index); index += Character.charCount(cp);
            if (cp == '\n') { cursor = 0; lineY += 1.2f; previous = -1; continue; }
            cp = supportedCodePoint(cp);
            Glyph glyph = glyph(cp);
            cursor += kerning(previous, cp);
            if (glyph.visible) {
                pose.pushMatrix();
                pose.translate(x + (cursor + glyph.left) * size, y + (lineY + capHeight - glyph.top) * size);
                pose.scale(glyph.scaleX * size, glyph.scaleY * size);
                morfeov.coliseum.ui.GuiTextures.blit(g, pipeline, image, 0, 0, glyph.u0, glyph.v0, glyph.tw, glyph.th, glyph.tw, glyph.th,
                    atlasWidth, atlasHeight, color);
                pose.popMatrix();
            }
            cursor += glyph.advance;
            previous = cp;
        }
    }
    public void worldGlyph(com.mojang.blaze3d.vertex.PoseStack pose, int cp, int color) {
        Glyph glyph=glyph(supportedCodePoint(cp));
        if(!glyph.visible)return;
        float x=glyph.left*BASE,y=(capHeight-glyph.top)*BASE;
        float w=glyph.scaleX*glyph.tw*BASE,h=glyph.scaleY*glyph.th*BASE;
        float[] xyz=new float[12];
        float[] corners={x,y,x,y+h,x+w,y+h,x+w,y};
        var matrix=pose.last().pose();
        for(int i=0;i<4;i++) {
            var p=matrix.transformPosition(new org.joml.Vector3f(corners[i*2],corners[i*2+1],0));
            xyz[i*3]=p.x;xyz[i*3+1]=p.y;xyz[i*3+2]=p.z;
        }
        float u=glyph.u0/atlasWidth,v=glyph.v0/atlasHeight,ur=(glyph.u0+glyph.tw)/atlasWidth,vb=(glyph.v0+glyph.th)/atlasHeight;
        morfeov.coliseum.render.coliseimvisual.EspEffects.quad(texture,xyz,new float[]{u,v,u,vb,ur,vb,ur,v},new int[]{color,color,color,color});
    }
    private record Glyph(float advance, float left, float top, float u0, float v0, int tw, int th,
                         float scaleX, float scaleY, boolean visible) {
        static Glyph blank(float advance) { return new Glyph(advance, 0, 0, 0, 0, 0, 0, 0, 0, false); }
        static Glyph of(float advance, float left, float top, float right, float bottom,
                        float u0, float v0, float u1, float v1) {
            int tw = Math.max(1, Math.round(u1 - u0)), th = Math.max(1, Math.round(v1 - v0));
            return new Glyph(advance, left, top, u0, v0, tw, th, (right - left) / tw, (top - bottom) / th, true);
        }
    }
}
