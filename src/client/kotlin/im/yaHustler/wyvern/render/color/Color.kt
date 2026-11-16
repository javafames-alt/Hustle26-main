package im.yaHustler.wyvern.render.color

import java.awt.Color as AwtColor

@JvmInline
value class Color(val argb: Int) {
    constructor(r: Int, g: Int, b: Int, a: Int = 255) :
        this((a and 255 shl 24) or (r and 255 shl 16) or (g and 255 shl 8) or (b and 255))

    val alpha get() = argb ushr 24 and 255
    val red get() = argb shr 16 and 255
    val green get() = argb shr 8 and 255
    val blue get() = argb and 255

    val alphaF get() = alpha / 255f
    val redF get() = red / 255f
    val greenF get() = green / 255f
    val blueF get() = blue / 255f

    fun withAlpha(alpha: Int) = Color(red, green, blue, alpha)
    fun withAlpha(alpha: Float) = withAlpha((alpha * 255).toInt())

    fun darker(factor: Float = 0.7f) =
        Color((red * factor).toInt(), (green * factor).toInt(), (blue * factor).toInt(), alpha)

    fun brighten(amount: Int) =
        Color((red + amount).coerceAtMost(255), (green + amount).coerceAtMost(255), (blue + amount).coerceAtMost(255), alpha)

    fun mix(other: Color, t: Float): Color {
        val k = t.coerceIn(0f, 1f)
        fun lerp(a: Int, b: Int) = (a + (b - a) * k).toInt()
        return Color(lerp(red, other.red), lerp(green, other.green), lerp(blue, other.blue), lerp(alpha, other.alpha))
    }

    companion object {
        val WHITE = Color(-1)
        val BLACK = Color(0, 0, 0)
        val TRANSPARENT = Color(0)

        fun hsb(hue: Float, saturation: Float, brightness: Float, alpha: Int = 255) =
            Color(AwtColor.HSBtoRGB(hue, saturation, brightness)).withAlpha(alpha)

        fun rainbow(speed: Float = 1f, offset: Long = 0, saturation: Float = 0.6f, brightness: Float = 1f): Color {
            val hue = ((System.currentTimeMillis() + offset) * speed % 4000L) / 4000f
            return hsb(hue, saturation, brightness)
        }
    }
}
