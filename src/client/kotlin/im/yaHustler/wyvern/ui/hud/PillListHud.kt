package im.yaHustler.wyvern.ui.hud

import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing

class PillCell(val width: Float, val draw: (x: Float, cy: Float, ty: Float) -> Unit)

abstract class PillListHud<T>(name: String, icon: Int, defaultX: Double, defaultY: Double) :
    HudElement(name, icon, defaultX, defaultY) {

    override val pills = true

    @Suppress("FunctionName")
    protected fun Cell(width: Float, draw: (x: Float, cy: Float, ty: Float) -> Unit) = PillCell(width, draw)

    protected abstract fun items(): List<T>

    protected abstract fun key(item: T): String

    protected abstract fun cells(r: Renderer2D, item: T): List<PillCell>

    private class Row<T>(val key: String, var item: T) {
        val anim = Animation(300, Easing.IN_OUT_CUBIC)
        var t = 0f
    }

    private var rows = LinkedHashMap<String, Row<T>>()

    protected var current: List<T> = emptyList()
        private set

    override fun hasContent() = items().isNotEmpty() || rows.isNotEmpty()

    private fun refresh() {
        val items = items()
        current = items
        val present = items.associateBy(::key)
        val old = rows.keys.toList()
        val order = present.keys.toMutableList()
        for ((index, key) in old.withIndex()) {
            if (key in present) continue
            val next = old.subList(index + 1, old.size).firstOrNull { it in order }
            order.add(if (next != null) order.indexOf(next) else order.size, key)
        }
        val updated = LinkedHashMap<String, Row<T>>()
        for (key in order) {
            val item = present[key]
            @Suppress("UNCHECKED_CAST")
            val row: Row<T> = rows[key] ?: Row(key, item as T)
            if (item != null) row.item = item
            row.t = row.anim.update(item != null)
            if (item != null || row.anim.value > 0f) updated[key] = row
        }
        rows = updated
    }

    private fun rowWidth(r: Renderer2D, item: T): Float {
        val cells = cells(r, item)
        return cells.sumOf { it.width.toDouble() }.toFloat() + PILL_GAP * (cells.size - 1).coerceAtLeast(0)
    }

    override fun measure(r: Renderer2D, editing: Boolean): Pair<Float, Float> {
        refresh()
        if (rows.isEmpty()) return titlePillWidth(r) to TITLE
        val width = rows.values.maxOf { rowWidth(r, it.item) }
        val height = rows.values.sumOf { ((ROW_GAP + PILL) * it.t).toDouble() }.toFloat()
        return maxOf(titlePillWidth(r), width) to TITLE + height
    }

    override fun draw(r: Renderer2D, x: Float, y: Float, w: Float, h: Float, editing: Boolean) {
        val right = rightSide
        titlePill(r, if (right) x + w - titlePillWidth(r) else x, y)
        var ry = y + TITLE
        for (row in rows.values) {
            val t = row.t
            val top = ry + ROW_GAP * t
            val cells = cells(r, row.item)
            val total = cells.sumOf { it.width.toDouble() }.toFloat() + PILL_GAP * (cells.size - 1).coerceAtLeast(0)
            val slide = (1f - t) * SLIDE * (if (right) 1f else -1f)
            var cx = (if (right) x + w - total else x) + slide
            r.withAlpha(t) {
                for (cell in cells) {
                    pill(r, cx, top, cell.width)
                    cell.draw(cx, top + PILL / 2f, top + PILL / 2f)
                    cx += cell.width + PILL_GAP
                }
            }
            ry += (ROW_GAP + PILL) * t
        }
    }

    private companion object {
        const val SLIDE = 10f
    }
}
