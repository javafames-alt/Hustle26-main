package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.render.color.Color

internal object Formatting {
    const val SECTION = '§'

    private val palette = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )

    fun apply(c: Char, base: Color): Color? {
        val index = "0123456789abcdef".indexOf(c.lowercaseChar())
        return when {
            index >= 0 -> Color(palette[index] or (base.alpha shl 24))
            c.lowercaseChar() == 'r' -> base
            else -> null
        }
    }

    fun strip(text: String): String = if (SECTION !in text) text else buildString {
        var i = 0
        while (i < text.length) {
            if (text[i] == SECTION && i + 1 < text.length) i += 2 else append(text[i++])
        }
    }
}
