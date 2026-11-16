package im.yaHustler.wyvern.ui.hud.element

import im.yaHustler.wyvern.event.Listenable
import im.yaHustler.wyvern.event.Listener
import im.yaHustler.wyvern.event.TickEvent
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.ui.hud.HudElement
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.world.phys.Vec3
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

object WatermarkHud : HudElement("Р’РѕРґСЏРЅРѕР№ Р·РЅР°Рє", Icons.LOGO, 0.5, 0.0), Listenable {
    override val listeners = mutableListOf<Listener<*>>()
    override val pills = true
    override val centered = true
    override val margin = 5f

    private class Segment(val icon: Int, val text: String?)

    private val segmentWidths = mutableListOf<Smooth>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private var blocksPerSecond = 0.0

    private var lastPos: Vec3? = null
    private val moves = ArrayDeque<Double>()

    private const val TEXT = 8f
    private const val ICON = 9f
    private const val ICON_GAP = 4f
    private const val SEPARATOR = 10f
    private const val PADDING = 8f
    private const val H = 18f
    private const val AVERAGE_TICKS = 10
    override val titled = false

    init {
        on<TickEvent.Post> {
            val player = mc.player ?: run {
                lastPos = null
                moves.clear()
                return@on
            }
            val pos = player.position()
            val last = lastPos
            lastPos = pos
            if (last == null) return@on
            val dx = pos.x - last.x
            val dy = pos.y - last.y
            val dz = pos.z - last.z
            val step = sqrt(dx * dx + dy * dy + dz * dz)
            moves.addLast(if (step > 10.0) 0.0 else step)
            while (moves.size > AVERAGE_TICKS) moves.removeFirst()
            blocksPerSecond = moves.sum() / moves.size * 20.0
        }
    }

    private fun segments(): List<Segment> {
        val player = mc.player
        val ping = player?.let { mc.connection?.getPlayerInfo(it.uuid)?.latency } ?: 0
        val pos = player?.blockPosition()
        return listOf(
            Segment(Icons.LOGO, null),
            Segment(Icons.PERSON, mc.user.name),
            Segment(Icons.FPS, "${mc.fps} FPS"),
            Segment(Icons.CLOUD, "$ping MS"),
            Segment(Icons.NAVIGATION, if (pos != null) "${pos.x}, ${pos.y}, ${pos.z}" else "-"),
            Segment(Icons.FLIGHT, "%.1f BPS".format(Locale.ROOT, blocksPerSecond)),
            Segment(Icons.SCHEDULE, LocalTime.now().format(clock)),
        )
    }

    private fun widths(r: Renderer2D, segments: List<Segment>): List<Float> {
        while (segmentWidths.size < segments.size) segmentWidths += Smooth(-1f, speed = 20f)
        return segments.mapIndexed { i, segment ->
            val target = ICON + if (segment.text != null) ICON_GAP + r.textWidth(segment.text, TEXT, Theme.titleFont) else 0f
            val smooth = segmentWidths[i]
            if (smooth.value < 0f) smooth.snap(target)
            smooth.update(target)
        }
    }

    override fun hasContent() = true

    override fun measure(r: Renderer2D, editing: Boolean): Pair<Float, Float> {
        val segments = segments()
        return PADDING * 2 + widths(r, segments).sum() + SEPARATOR * (segments.size - 1) to H
    }

    override fun draw(r: Renderer2D, x: Float, y: Float, w: Float, h: Float, editing: Boolean) {
        val segments = segments()
        val widths = segmentWidths.take(segments.size).map { it.value }
        val cy = y + H / 2f
        if (blurRows?.value == true) {
            r.blur(x, y, w, H, 6f, Theme.background.withAlpha(255))
        } else {
            r.shadow(x, y + 1f, w, H, 6f, 6f, Theme.shadow)
            r.roundedRect(x, y, w, H, 6f, Theme.background)
        }

        var cx = x + PADDING
        for ((index, segment) in segments.withIndex()) {
            if (index > 0) {
                r.circle(cx + SEPARATOR / 2f, cy, 1f, Theme.separator)
                cx += SEPARATOR
            }
            r.icon(segment.icon, cx + ICON / 2f, cy, ICON, Theme.accent)
            if (segment.text != null) {
                r.scissor(cx, y, widths[index], H) {
                    r.textCenteredY(segment.text, cx + ICON + ICON_GAP, cy, Theme.text, TEXT, Theme.titleFont)
                }
            }
            cx += widths[index]
        }
    }
}
