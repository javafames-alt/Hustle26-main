package im.yaHustler.wyvern.ui.theme

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.font.Fonts
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing

data class Palette(
    val name: String,
    val background: Color,
    val card: Color,
    val accent: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val onAccent: Color,
)

object Theme {
    private val builtIn = listOf(
        dark("Midnight", Color(15, 16, 17), Color(20, 21, 23), Color(240, 107, 107), onAccent = Color(28, 14, 14)),
        dark("Ocean", Color(12, 16, 23), Color(19, 25, 35), Color(84, 156, 255)),
        dark("Violet", Color(16, 14, 23), Color(25, 22, 35), Color(160, 112, 255)),
        dark("Mint", Color(12, 18, 17), Color(19, 28, 26), Color(70, 210, 158)),
        dark("Amber", Color(19, 16, 12), Color(29, 25, 19), Color(255, 174, 66)),
        dark("Rose", Color(20, 13, 17), Color(31, 21, 27), Color(255, 104, 168)),
        dark("Graphite", Color(20, 20, 20), Color(30, 30, 30), Color(225, 225, 228), onAccent = Color(20, 20, 20)),
        Palette(
            "Snow",
            background = Color(242, 243, 246),
            card = Color(255, 255, 255),
            accent = Color(56, 116, 255),
            text = Color(24, 25, 29),
            textDim = Color(104, 107, 116),
            textFaint = Color(156, 159, 168),
            onAccent = Color(255, 255, 255),
        ),
    )

    private val customPlaceholder = dark("Custom", Color(15, 16, 18), Color(24, 25, 28), Color(240, 104, 104))

    val palettes get() = builtIn + ThemePresets.presets + customPlaceholder

    fun isBuiltIn(name: String) = builtIn.any { it.name == name } || name == CUSTOM

    const val CUSTOM = "Custom"

    fun fromColors(name: String, background: Color, card: Color, accent: Color, text: Color): Palette {
        val luminance = (0.299 * accent.red + 0.587 * accent.green + 0.114 * accent.blue) / 255.0
        return Palette(
            name, background.withAlpha(255), card.withAlpha(255), accent, text,
            textDim = text.mix(background, 0.45f),
            textFaint = text.mix(background, 0.65f),
            onAccent = if (luminance > 0.6) Color(20, 20, 22) else Color(255, 255, 255),
        )
    }

    fun toCode(p: Palette) =
        "wyvern-theme:${p.name}:" + listOf(p.background, p.card, p.accent, p.text).joinToString(",") { "#%08X".format(it.argb) }

    fun fromCode(code: String): Palette? {
        val colors = Regex("#?([0-9a-fA-F]{8}|[0-9a-fA-F]{6})\\b").findAll(code).map { m ->
            val hex = m.groupValues[1]
            Color(if (hex.length == 6) (0xFF000000 or hex.toLong(16)).toInt() else hex.toLong(16).toInt())
        }.toList()
        if (colors.size < 4) return null
        val name = code.removePrefix("wyvern-theme:").substringBefore(":", "").takeIf { code.startsWith("wyvern-theme:") && it.isNotBlank() } ?: "Imported"
        return fromColors(name, colors[0], colors[1], colors[2], colors[3])
    }

    var customColors: () -> List<Color> = { emptyList() }

    fun resolve(palette: Palette): Palette {
        if (palette.name != CUSTOM) return palette
        val colors = customColors()
        if (colors.size < 4) return palette
        val (background, card, accent, text) = colors
        return fromColors(CUSTOM, background, card, accent, text)
    }

    private fun dark(name: String, background: Color, card: Color, accent: Color, onAccent: Color = Color(255, 255, 255)) = Palette(
        name, background, card, accent,
        text = Color(244, 244, 246),
        textDim = Color(124, 126, 133),
        textFaint = Color(84, 86, 93),
        onAccent = onAccent,
    )

    var current: Palette = builtIn.first()
        private set
    private var previous: Palette = current
    private val transition = Animation(450, Easing.IN_OUT_CUBIC).also { it.snap(true) }

    fun select(name: String) {
        val next = palettes.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return
        if (next == current) return
        previous = snapshot()
        current = next
        transition.snap(false)
    }

    private fun snapshot() = Palette(
        current.name, background, card, accent, text, textDim, textFaint, onAccent,
    )

    private inline fun color(pick: (Palette) -> Color): Color {
        val t = transition.update(true)
        val target = pick(resolve(current))
        return if (t >= 1f) target else pick(previous).mix(target, t)
    }

    val background get() = color { it.background }
    val card get() = color { it.card }
    val accent get() = color { it.accent }
    val text get() = color { it.text }
    val textDim get() = color { it.textDim }
    val textFaint get() = color { it.textFaint }
    val onAccent get() = color { it.onAccent }

    val control get() = text.withAlpha(10)
    val controlHover get() = text.withAlpha(20)
    val divider get() = text.withAlpha(12)
    val cardBorder get() = text.withAlpha(13)
    val separator get() = text.withAlpha(36)

    val dim = Color(0, 0, 0, 120)
    val shadow = Color(0, 0, 0, 130)

    const val WINDOW_RADIUS = 12f
    const val CARD_RADIUS = 8f
    const val CONTROL_RADIUS = 5f

    val titleFont get() = Fonts.medium
    val bodyFont get() = Fonts.regular

    const val TITLE_SIZE = 8.5f
    const val NAME_SIZE = 7.5f
    const val DESCRIPTION_SIZE = 6.5f
}

object Mouse {
    const val LEFT = InputConstants.MOUSE_BUTTON_LEFT
    const val MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE
    const val RIGHT = InputConstants.MOUSE_BUTTON_RIGHT
}
