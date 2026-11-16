package im.yaHustler.wyvern.ui.hud

import im.yaHustler.wyvern.event.EventBus
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.ui.hud.element.CooldownsHud
import im.yaHustler.wyvern.ui.hud.element.KeybindsHud
import im.yaHustler.wyvern.ui.hud.element.Notifications
import im.yaHustler.wyvern.ui.hud.element.PotionsHud
import im.yaHustler.wyvern.ui.hud.element.StaffListHud
import im.yaHustler.wyvern.ui.hud.element.TargetHud
import im.yaHustler.wyvern.ui.hud.element.WatermarkHud
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen

object HudManager {
    val elements: List<HudElement> = listOf(WatermarkHud, KeybindsHud, PotionsHud, TargetHud, CooldownsHud, StaffListHud, Notifications)

    var dragging: HudElement? = null
        private set
    private var grabX = 0f
    private var grabY = 0f

    private val mc get() = Minecraft.getInstance()

    val editing get() = mc.gui.screen() is ChatScreen

    fun init() {
        EventBus.subscribe(Notifications)
        EventBus.subscribe(WatermarkHud)
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                val mx = event.x.toFloat()
                val my = event.y.toFloat()
                !(menu.click(mx, my, event.button()) || startDrag(mx, my, event.button()))
            }
            ScreenMouseEvents.afterMouseRelease(screen).register { _, _, consumed ->
                dragging = null
                consumed
            }
            ScreenEvents.remove(screen).register {
                dragging = null
                menu.close()
            }
        }
    }

    fun render(r: Renderer2D) {
        val editing = editing
        dragging?.let { element ->
            val window = mc.window
            val mx = mc.mouseHandler.getScaledXPos(window).toFloat()
            val my = mc.mouseHandler.getScaledYPos(window).toFloat()
            element.dragTo(mx - grabX, my - grabY, r.width, r.height)
        }
        for (element in elements) element.render(r, editing)
        if (editing) menu.render(r) else menu.close()
    }

    private val menu = object {
        private var element: HudElement? = null
        private var x = 0f
        private var y = 0f
        private val anim = Animation(160, Easing.OUT_CUBIC)
        private val labels = listOf("Blur background", "Blur header")
        private val switches = labels.map { Animation(200, Easing.IN_OUT_CUBIC) }

        private val rows get() = if (element?.titled == false) labels.take(1) else labels
        private val height get() = PAD * 2f + ROW * rows.size

        private fun setting(element: HudElement, row: Int) = if (row == 0) element.blurRows else element.blurHeader

        fun close() {
            element = null
        }

        fun click(mx: Float, my: Float, button: Int): Boolean {
            val open = element
            if (open != null) {
                val row = rowAt(mx, my)
                if (row != null && button == Mouse.LEFT) setting(open, row)?.toggle()
                else if (row == null) element = null
                return row != null || button == Mouse.RIGHT && openAt(mx, my)
            }
            return button == Mouse.RIGHT && openAt(mx, my)
        }

        private fun openAt(mx: Float, my: Float): Boolean {
            val target = elements.lastOrNull { it.toggle.value && it.blurable && it.bounds.contains(mx, my) } ?: return false
            element = target
            x = mx
            y = my
            return true
        }

        private fun rowAt(mx: Float, my: Float): Int? {
            if (mx < x || mx > x + W) return null
            val i = ((my - y - PAD) / ROW).toInt()
            return if (my >= y + PAD && i in rows.indices) i else null
        }

        fun render(r: Renderer2D) {
            val open = element
            val t = anim.update(open != null)
            if (t <= 0.001f || open == null) return
            x = x.coerceAtMost(r.width - W - 4f)
            y = y.coerceAtMost(r.height - height - 4f)
            r.withAlpha(t) {
                r.withPose {
                    r.scaleAround(x, y, 0.94f + 0.06f * t)
                    r.shadow(x, y + 2f, W, height, 6f, 9f, Theme.shadow)
                    r.roundedRect(x, y, W, height, 6f, Theme.background)
                    r.outline(x, y, W, height, 6f, 0.5f, Theme.cardBorder)
                    for ((i, label) in rows.withIndex()) {
                        val on = switches[i].update(setting(open, i)?.value == true)
                        val ry = y + PAD + i * ROW
                        val cy = ry + ROW / 2f
                        r.textCenteredY(label, x + 8f, cy, Theme.text, 7f, Theme.bodyFont)
                        val sw = 18f
                        val sh = 9f
                        val sx = x + W - 8f - sw
                        r.roundedRect(sx, cy - sh / 2f, sw, sh, sh / 2f, Theme.control.mix(Theme.accent, on))
                        r.circle(sx + sh / 2f + (sw - sh) * on, cy, 2.8f, Theme.text.mix(Theme.onAccent, on))
                    }
                }
            }
        }
    }

    private const val W = 104f
    private const val ROW = 16f
    private const val PAD = 4f

    private fun startDrag(mx: Float, my: Float, button: Int): Boolean {
        if (button != Mouse.LEFT) return false
        val element = elements.lastOrNull { it.toggle.value && it.bounds.contains(mx, my) } ?: return false
        dragging = element
        grabX = mx - element.bounds.x
        grabY = my - element.bounds.y
        return true
    }
}
