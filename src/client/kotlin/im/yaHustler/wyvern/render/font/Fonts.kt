package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.Wyvern
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

object Fonts {
    enum class Weight(val file: String, val windows: String, val linux: String, val systemWeight: Float) {
        REGULAR("regular", "segoeui.ttf", "DejaVuSans.ttf", 0f),
        MEDIUM("medium", "seguisb.ttf", "DejaVuSans.ttf", -0.7f),
        SEMIBOLD("semibold", "seguisb.ttf", "DejaVuSans-Bold.ttf", 0f),
        BOLD("bold", "segoeuib.ttf", "DejaVuSans-Bold.ttf", 0f),
    }

    private const val FAMILY = "sf_pro"

    private const val BOLDNESS = 0.35f

    val regular: TextRenderer by lazy { create(Weight.REGULAR) }
    val medium: TextRenderer by lazy { create(Weight.MEDIUM) }
    val semibold: TextRenderer by lazy { create(Weight.SEMIBOLD) }
    val bold: TextRenderer by lazy { create(Weight.BOLD) }

    operator fun get(weight: Weight) = when (weight) {
        Weight.REGULAR -> regular
        Weight.MEDIUM -> medium
        Weight.SEMIBOLD -> semibold
        Weight.BOLD -> bold
    }

    private fun create(weight: Weight): TextRenderer {
        AtlasFont.load("${FAMILY}_${weight.file}", fallback = fileFont(weight)?.first)?.let { atlas ->
            Wyvern.logger.info("Font ${weight.name.lowercase()}: ${atlas.name} (atlas)")
            return MsdfTextRenderer(atlas, BOLDNESS)
        }
        return fileFont(weight)?.let { (font, offset) ->
            prewarm(font)
            Wyvern.logger.info("Font ${weight.name.lowercase()}: ${font.name}")
            MsdfTextRenderer(font, offset + BOLDNESS)
        } ?: run {
            Wyvern.logger.warn("No font found for ${weight.name.lowercase()}, using vanilla font. Put ${FAMILY}_${weight.file}.otf into assets/wyvern/fonts/")
            VanillaTextRenderer
        }
    }

    private fun fileFont(weight: Weight): Pair<MsdfFont, Float>? {
        var offset = 0f
        val font = bundled(weight)
            ?: Weight.entries.sortedBy { abs(it.ordinal - weight.ordinal) }.firstNotNullOfOrNull { other ->
                bundled(other)?.also { offset = (weight.ordinal - other.ordinal) * 0.7f }
            }
            ?: system(weight)?.also { offset = weight.systemWeight }
        return font?.let { it to offset }
    }

    private fun bundled(weight: Weight): MsdfFont? {
        for (extension in listOf("otf", "ttf")) {
            val name = "${FAMILY}_${weight.file}.$extension"
            val data = Fonts::class.java.getResourceAsStream("/assets/wyvern/fonts/$name")?.use { it.readBytes() } ?: continue
            return MsdfFont.load(name, data)
        }
        return null
    }

    private fun system(weight: Weight): MsdfFont? {
        val candidates = listOf(
            Path.of(System.getenv("WINDIR") ?: "C:/Windows", "Fonts", weight.windows),
            Path.of("/System/Library/Fonts/SFNS.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu", weight.linux),
            Path.of("/usr/share/fonts/TTF", weight.linux),
        )
        val path = candidates.firstOrNull(Files::isRegularFile) ?: return null
        return MsdfFont.load(path.fileName.toString(), Files.readAllBytes(path))
    }

    private fun prewarm(font: MsdfFont) {
        val codepoints = (32..126) + (0x410..0x44F) + 'Ё'.code + 'ё'.code
        font.preload(codepoints.toIntArray())
        font.uploadPending()
    }
}
