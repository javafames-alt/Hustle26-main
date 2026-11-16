package im.yaHustler.wyvern.ui.theme

import im.yaHustler.wyvern.render.color.Color
import java.awt.Color as AwtColor

object ThemeColors {
    private const val FIRST_SATURATION = 0.478f
    private const val FIRST_BRIGHTNESS = 1f
    private const val SECOND_SATURATION = 0.694f
    private const val SECOND_BRIGHTNESS = 0.627f
    private const val GREY_SATURATION = 0.05f

    private var cachedAccent = 0
    private var cached = false
    private var cachedFirst = Color.WHITE
    private var cachedSecond = Color.WHITE
    private val hsb = FloatArray(3)

    val first: Color
        get() {
            refresh()
            return cachedFirst
        }

    val second: Color
        get() {
            refresh()
            return cachedSecond
        }

    fun wave(offset: Int, speed: Int = 4): Color {
        refresh()
        val deg = Math.floorMod(System.currentTimeMillis() / speed + offset, 360L)
        val mirrored = if (deg >= 180) 360 - deg else deg
        return cachedFirst.mix(cachedSecond, mirrored / 180f)
    }

    fun rainbow(seed: Int): Color = Color.hsb(Math.floorMod(System.currentTimeMillis() + seed, 360L) / 360f, 1f, 1f)

    fun lighten(color: Color, amount: Float) = Color(
        (color.red + (255 - color.red) * amount).toInt(),
        (color.green + (255 - color.green) * amount).toInt(),
        (color.blue + (255 - color.blue) * amount).toInt(),
        color.alpha,
    )

    private fun refresh() {
        val accent = Theme.accent
        if (cached && accent.argb == cachedAccent) return
        AwtColor.RGBtoHSB(accent.red, accent.green, accent.blue, hsb)
        val grey = hsb[1] < GREY_SATURATION
        cachedFirst = Color.hsb(hsb[0], if (grey) 0f else FIRST_SATURATION, FIRST_BRIGHTNESS)
        cachedSecond = Color.hsb(hsb[0], if (grey) 0f else SECOND_SATURATION, SECOND_BRIGHTNESS)
        cachedAccent = accent.argb
        cached = true
    }
}
