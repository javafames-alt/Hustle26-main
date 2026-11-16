package im.yaHustler.wyvern.ui.hud

import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.NumberSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.client.Minecraft

abstract class HudElement(val name: String, val icon: Int, val defaultX: Double, val defaultY: Double) {
    protected val mc: Minecraft get() = Minecraft.getInstance()

    lateinit var toggle: BooleanSetting
    lateinit var posX: NumberSetting
    lateinit var posY: NumberSetting

    var blurRows: BooleanSetting? = null
    var blurHeader: BooleanSetting? = null

    val blurable get() = pills

    open val titled get() = true

    val bounds = Rect()
    private val visibility = Animation(260, Easing.IN_OUT_CUBIC)
    private val animatedWidth = Smooth(-1f, speed = 22f)
    private val animatedHeight = Smooth(-1f, speed = 22f)

    protected open val pills = false

    val rightSide get() = pills && !centered && posX.value >= 0.5

    protected open val margin: Float get() = if (pills) 7f else 2f

    protected open val centered = false

    protected abstract fun hasContent(): Boolean

    protected abstract fun measure(r: Renderer2D, editing: Boolean): Pair<Float, Float>

    protected abstract fun draw(r: Renderer2D, x: Float, y: Float, w: Float, h: Float, editing: Boolean)

    fun render(r: Renderer2D, editing: Boolean) {
        val show = toggle.value && (hasContent() || editing)
        val t = visibility.update(show)
        if (t <= 0.001f) return

        val (tw, th) = measure(r, editing)
        if (animatedWidth.value < 0f) {
            animatedWidth.snap(tw)
            animatedHeight.snap(th)
        }
        val w = animatedWidth.update(tw)
        val h = animatedHeight.update(th)
        val anchor = posX.value.toFloat() * r.width
        val left = when {
            centered -> anchor - w / 2f
            rightSide -> anchor - w
            else -> anchor
        }
        val x = left.coerceIn(margin, (r.width - w - margin).coerceAtLeast(margin))
        val y = (posY.value.toFloat() * r.height).coerceIn(margin, (r.height - h - margin).coerceAtLeast(margin))
        bounds.set(x, y, w, h)

        r.withPose {
            r.scaleAround(x + w / 2f, y + h / 2f, 0.92f + 0.08f * t)
            r.withAlpha(t) {
                if (pills) {
                    if (editing) {
                        val grabbed = HudManager.dragging === this
                        r.dashedOutline(x - 5f, y - 5f, w + 10f, h + 10f, 10f, 1f, 3.5f, EDIT_OUTLINE.withAlpha(if (grabbed) 235 else 150))
                    }
                    draw(r, x, y, w, h, editing)
                } else {
                    r.shadow(x, y + 1.5f, w, h, RADIUS, 7f, Theme.shadow)
                    r.roundedRect(x, y, w, h, RADIUS, Theme.background)
                    if (editing) r.outline(x, y, w, h, RADIUS, 0.75f, Theme.accent.withAlpha(if (HudManager.dragging === this) 220 else 90))
                    r.scissor(x, y, w, h) { draw(r, x, y, w, h, editing) }
                }
            }
        }
    }

    fun dragTo(left: Float, top: Float, screenW: Float, screenH: Float) {
        val w = bounds.w
        if (centered) {
            posX.value = ((left + w / 2f) / screenW).toDouble().coerceIn(0.0, 1.0)
            posY.value = (top / screenH).toDouble().coerceIn(0.0, 1.0)
            return
        }
        val right = pills && left + w / 2f >= screenW / 2f
        posX.value = ((if (right) left + w else left) / screenW).toDouble().coerceIn(0.0, 1.0)
        if (right && posX.value < 0.5) posX.value = 0.5
        posY.value = (top / screenH).toDouble().coerceIn(0.0, 1.0)
    }

    protected fun pill(r: Renderer2D, x: Float, y: Float, w: Float, h: Float = PILL, radius: Float = ROW_RADIUS, header: Boolean = false) {
        val glass = (if (header) blurHeader else blurRows)?.value == true
        if (glass) {
            r.blur(x, y, w, h, radius, Theme.background.withAlpha(255))
        } else {
            r.shadow(x, y + 1f, w, h, radius, 5f, Theme.shadow)
            r.roundedRect(x, y, w, h, radius, Theme.background)
        }
    }

    protected fun titlePillWidth(r: Renderer2D) = PILL_PAD + 9f + 3.5f + r.textWidth(name, 8f, Theme.titleFont) + PILL_PAD

    protected fun titlePill(r: Renderer2D, x: Float, y: Float) {
        pill(r, x, y, titlePillWidth(r), TITLE, PILL_RADIUS, header = true)
        val cy = y + TITLE / 2f
        r.icon(icon, x + PILL_PAD + 4.5f, cy, 9f, Theme.accent)
        r.textCenteredY(name, x + PILL_PAD + 12.5f, cy, Theme.text, 8f, Theme.titleFont)
    }

    protected fun header(r: Renderer2D, x: Float, y: Float, w: Float, badge: String? = null) {
        val cy = y + HEADER / 2f
        r.icon(icon, x + 10f, cy, 9f, Theme.accent)
        r.textCenteredY(name, x + 19f, cy, Theme.text, 8f, Theme.titleFont)
        if (badge != null) {
            val bw = r.textWidth(badge, 7f, Theme.bodyFont)
            r.textCenteredY(badge, x + w - 8f - bw, cy, Theme.textDim, 7f, Theme.bodyFont)
        }
        r.rect(x + 6f, y + HEADER, w - 12f, 0.5f, Theme.divider)
    }

    protected fun placeholder(r: Renderer2D, text: String, x: Float, y: Float) =
        r.textCenteredY(text, x + 9f, y + ROW / 2f, Theme.textFaint, 7.5f, Theme.bodyFont)

    protected fun chip(r: Renderer2D, text: String, right: Float, cy: Float, color: Color = Theme.textDim): Float {
        val tw = r.textWidth(text, 6.5f, Theme.bodyFont)
        val w = tw + 8f
        r.roundedRect(right - w, cy - 5f, w, 10f, 3f, Theme.control)
        r.textCenteredY(text, right - w + 4f, cy, color, 6.5f, Theme.bodyFont)
        return w
    }

    companion object {
        const val RADIUS = 7f
        const val HEADER = 18f
        const val ROW = 13f
        const val PAD = 4f

        const val PILL = 14f
        const val TITLE = 18f

        const val TEXT_LIFT = 0.5f
        const val PILL_GAP = 3f
        const val ROW_GAP = 2f
        const val PILL_PAD = 5f
        const val PILL_RADIUS = 5.5f

        const val ROW_RADIUS = PILL_RADIUS * PILL / TITLE

        val EDIT_OUTLINE = Color(196, 184, 140)
    }
}
