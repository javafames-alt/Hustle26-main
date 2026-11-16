package im.yaHustler.wyvern.render.gui

import kotlin.math.roundToInt

object ShapeFill {
    const val VERTEX_COLORS = 0
    const val HUE_SPECTRUM = 1
    const val CHECKERBOARD = 2

    private const val RING = 3
    private const val DASHED = 4
    private const val DOT_GRID = 5
    private const val MODE_BITS = 3

    fun ring(progress: Float) = pack(RING, (progress.coerceIn(0f, 1f) * 1000f).toInt())

    fun dashed(dash: Float) = pack(DASHED, (dash * 16f).toInt())

    fun dotGrid(spacing: Float) = pack(DOT_GRID, (spacing * 4f).roundToInt())

    private fun pack(mode: Int, parameter: Int) = mode or (parameter shl MODE_BITS)
}
