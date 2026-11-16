package im.yaHustler.wyvern.render.font

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import im.yaHustler.wyvern.Wyvern
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

class AtlasFont private constructor(
    val name: String,
    root: JsonObject,
    image: NativeImage,
    private val fallback: GlyphFont?,
) : GlyphFont {
    override val rasterSize: Float
    override val range: Double
    override val ascender: Float
    override val descender: Float

    private val atlas: FontAtlas
    private val glyphs = Int2ObjectOpenHashMap<MsdfFont.Glyph>()
    private val borrowed = Int2ObjectOpenHashMap<MsdfFont.Glyph>()

    init {
        val info = root["atlas"].asJsonObject
        val size = info["size"].asFloat
        val width = info["width"].asFloat
        val height = info["height"].asFloat
        rasterSize = size
        range = info["distanceRange"].asDouble
        atlas = FontAtlas.prebuilt("Wyvern font $name", image, range)
        val bottomOrigin = info["yOrigin"]?.asString != "top"

        val metrics = root["metrics"].asJsonObject
        ascender = metrics["ascender"].asFloat * size
        descender = -metrics["descender"].asFloat * size

        for (element in root["glyphs"].asJsonArray) {
            val glyph = element.asJsonObject
            val codepoint = glyph["unicode"]?.asInt ?: continue
            val advance = glyph["advance"].asFloat * size
            val plane = glyph["planeBounds"]?.asJsonObject
            val box = glyph["atlasBounds"]?.asJsonObject
            if (plane == null || box == null) {
                glyphs.put(codepoint, MsdfFont.Glyph(advance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, null))
                continue
            }
            val left = plane["left"].asFloat * size
            val right = plane["right"].asFloat * size
            val top = plane["top"].asFloat * size
            val bottom = plane["bottom"].asFloat * size
            val u0 = box["left"].asFloat / width
            val u1 = box["right"].asFloat / width
            val v0 = if (bottomOrigin) 1f - box["top"].asFloat / height else box["top"].asFloat / height
            val v1 = if (bottomOrigin) 1f - box["bottom"].asFloat / height else box["bottom"].asFloat / height
            glyphs.put(codepoint, MsdfFont.Glyph(advance, left, top, right - left, top - bottom, u0, v0, u1, v1, atlas))
        }
    }

    override fun glyph(codepoint: Int): MsdfFont.Glyph = glyphs[codepoint] ?: borrowed[codepoint] ?: borrow(codepoint)

    private fun borrow(codepoint: Int): MsdfFont.Glyph {
        val source = fallback ?: return glyphs['?'.code] ?: MsdfFont.Glyph(rasterSize * 0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, null)
        val glyph = source.glyph(codepoint)
        val k = rasterSize / source.rasterSize
        return MsdfFont.Glyph(
            glyph.advance * k, glyph.left * k, glyph.top * k, glyph.width * k, glyph.height * k,
            glyph.u0, glyph.v0, glyph.u1, glyph.v1, glyph.atlas,
        ).also { borrowed.put(codepoint, it) }
    }

    override fun kerning(left: Int, right: Int) = 0f

    override fun uploadPending() {
        fallback?.uploadPending()
    }

    companion object {
        fun load(base: String, fallback: GlyphFont?): AtlasFont? {
            val json = AtlasFont::class.java.getResourceAsStream("/assets/wyvern/fonts/$base.json")?.use { it.readBytes() } ?: return null
            val png = AtlasFont::class.java.getResourceAsStream("/assets/wyvern/fonts/$base.png")?.use { it.readBytes() } ?: return null
            return runCatching {
                AtlasFont(base, JsonParser.parseString(json.decodeToString()).asJsonObject, NativeImage.read(png), fallback)
            }.onFailure { Wyvern.logger.error("Failed to load font atlas $base", it) }.getOrNull()
        }
    }
}
