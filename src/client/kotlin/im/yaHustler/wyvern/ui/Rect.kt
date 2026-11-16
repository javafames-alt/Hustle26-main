package im.yaHustler.wyvern.ui

class Rect(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f) {
    fun set(x: Float, y: Float, w: Float, h: Float) = apply {
        this.x = x; this.y = y; this.w = w; this.h = h
    }

    operator fun contains(point: Pair<Float, Float>) = contains(point.first, point.second)

    fun contains(mx: Float, my: Float) = w > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h

    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
}
