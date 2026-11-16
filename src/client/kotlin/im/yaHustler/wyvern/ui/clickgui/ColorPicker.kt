package im.yaHustler.wyvern.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.ColorSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import net.minecraft.client.Minecraft
import java.awt.Color as AwtColor

class ColorPicker {
    private var setting: ColorSetting? = null
    private var original = Color.WHITE
    private val anchor = Rect()
    private val anim = Animation(180, Easing.OUT_CUBIC)
    private var closing = false

    private var hue = 0f
    private var saturation = 0f
    private var brightness = 0f
    private var alpha = 1f

    private enum class Drag { NONE, SV, HUE, ALPHA }
    private var drag = Drag.NONE

    val bounds = Rect()
    private val sv = Rect()
    private val hueBar = Rect()
    private val alphaBar = Rect()
    private val previewOld = Rect()
    private val hexRect = Rect()
    private val pasteRect = Rect()
    private val presetRects = mutableListOf<Pair<Rect, Color>>()

    val isOpen get() = setting != null

    fun open(target: ColorSetting, from: Rect) {
        setting = target
        original = target.value
        anchor.set(from.x, from.y, from.w, from.h)
        val hsb = AwtColor.RGBtoHSB(original.red, original.green, original.blue, null)
        hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2]
        alpha = original.alphaF
        closing = false
        drag = Drag.NONE
        anim.snap(false)
    }

    fun close() {
        closing = true
        drag = Drag.NONE
    }

    private fun apply() {
        setting?.value = Color.hsb(hue, saturation, brightness, (alpha * 255).toInt())
    }

    fun render(r: Renderer2D, mouseX: Float, mouseY: Float) {
        val target = setting ?: return
        val t = anim.update(!closing)
        if (closing && t <= 0.001f) {
            setting = null
            return
        }
        updateDrag(mouseX, mouseY)

        val w = WIDTH
        val h = HEIGHT
        var x = anchor.x + anchor.w + 8f
        if (x + w > r.width - 6f) x = anchor.x - w - 8f
        x = x.coerceIn(6f, r.width - w - 6f)
        val y = (anchor.centerY - h / 2f).coerceIn(6f, r.height - h - 6f)
        bounds.set(x, y, w, h)

        r.nextLayer()
        r.withPose {
            r.scaleAround(anchor.centerX, anchor.centerY, 0.92f + 0.08f * t)
            r.withAlpha(t) { draw(r, target, x, y, w, h, mouseX, mouseY) }
        }
    }

    private fun draw(r: Renderer2D, target: ColorSetting, x: Float, y: Float, w: Float, h: Float, mx: Float, my: Float) {
        r.shadow(x, y + 3f, w, h, Theme.CARD_RADIUS, 14f, Theme.shadow)
        r.roundedRect(x, y, w, h, Theme.CARD_RADIUS, Theme.card)
        r.outline(x, y, w, h, Theme.CARD_RADIUS, 0.5f, Theme.cardBorder)

        val inner = w - PAD * 2
        var cy = y + PAD

        r.textCenteredY(target.name, x + PAD, cy + 6f, Theme.text, Theme.NAME_SIZE, Theme.titleFont)
        val current = target.value
        val hex = hexString(current)
        val hexW = r.textWidth(hex, Theme.DESCRIPTION_SIZE, Theme.bodyFont)
        hexRect.set(x + w - PAD - hexW - 3f, cy, hexW + 6f, 12f)
        pasteRect.set(hexRect.x - 14f, cy, 12f, 12f)
        val hexHot = hexRect.contains(mx, my)
        if (hexHot) r.roundedRect(hexRect.x, hexRect.y, hexRect.w, hexRect.h, 3f, Theme.controlHover)
        r.textCenteredY(hex, hexRect.x + 3f, cy + 6f, if (hexHot) Theme.text else Theme.textDim, Theme.DESCRIPTION_SIZE, Theme.bodyFont)
        val pasteHot = pasteRect.contains(mx, my)
        if (pasteHot) r.roundedRect(pasteRect.x, pasteRect.y, pasteRect.w, pasteRect.h, 3f, Theme.controlHover)
        r.icon(Icons.PASTE, pasteRect.centerX, pasteRect.centerY, 8f, if (pasteHot) Theme.text else Theme.textFaint)
        cy += 16f

        sv.set(x + PAD, cy, inner, 84f)
        val pure = Color.hsb(hue, 1f, 1f)
        r.gradientH(sv.x, sv.y, sv.w, sv.h, 5f, Color.WHITE, pure)
        r.gradientV(sv.x, sv.y, sv.w, sv.h, 5f, Color(0, 0, 0, 0), Color.BLACK)
        knob(r, sv.x + sv.w * saturation, sv.y + sv.h * (1f - brightness), Color.hsb(hue, saturation, brightness))
        cy += sv.h + 7f

        hueBar.set(x + PAD, cy, inner, 7f)
        r.hueSpectrum(hueBar.x, hueBar.y, hueBar.w, hueBar.h, hueBar.h / 2f)
        knob(r, hueBar.x + hueBar.w * hue, hueBar.centerY, pure)
        cy += hueBar.h + 7f

        alphaBar.set(x + PAD, cy, inner, 7f)
        val opaque = Color.hsb(hue, saturation, brightness)
        r.checkerboard(alphaBar.x, alphaBar.y, alphaBar.w, alphaBar.h, alphaBar.h / 2f)
        r.gradientH(alphaBar.x, alphaBar.y, alphaBar.w, alphaBar.h, alphaBar.h / 2f, opaque.withAlpha(0), opaque)
        knob(r, alphaBar.x + alphaBar.w * alpha, alphaBar.centerY, current)
        cy += alphaBar.h + 9f

        previewOld.set(x + PAD, cy, 16f, 12f)
        r.checkerboard(previewOld.x, cy, 33f, 12f, 3f)
        r.shape(previewOld.x, cy, 16.5f, 12f, 3f, original, original, original, original)
        r.shape(previewOld.x + 16.5f, cy, 16.5f, 12f, 3f, current, current, current, current)

        presetRects.clear()
        val presets = PRESETS
        var px = x + w - PAD - 6f
        for (preset in presets.asReversed()) {
            val rect = Rect(px - 6f, cy, 12f, 12f)
            presetRects += rect to preset
            val hover = rect.contains(mx, my)
            r.circle(rect.centerX, rect.centerY, if (hover) 5.5f else 4.8f, preset)
            px -= 13f
        }
    }

    private fun knob(r: Renderer2D, cx: Float, cy: Float, fill: Color) {
        r.shadow(cx - 4.5f, cy - 4.5f, 9f, 9f, 4.5f, 2f, Color(0, 0, 0, 110))
        r.circle(cx, cy, 4.5f, Color.WHITE)
        r.circle(cx, cy, 3.2f, fill.withAlpha(255))
    }

    private fun updateDrag(mx: Float, my: Float) {
        when (drag) {
            Drag.SV -> {
                saturation = ((mx - sv.x) / sv.w).coerceIn(0f, 1f)
                brightness = 1f - ((my - sv.y) / sv.h).coerceIn(0f, 1f)
            }
            Drag.HUE -> hue = ((mx - hueBar.x) / hueBar.w).coerceIn(0f, 0.999f)
            Drag.ALPHA -> alpha = ((mx - alphaBar.x) / alphaBar.w).coerceIn(0f, 1f)
            Drag.NONE -> return
        }
        apply()
    }

    fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (!isOpen || closing) return false
        if (!bounds.contains(mx, my)) {
            close()
            return true
        }
        if (button != Mouse.LEFT) return true
        drag = when {
            sv.contains(mx, my) -> Drag.SV
            Rect(hueBar.x, hueBar.y - 3f, hueBar.w, hueBar.h + 6f).contains(mx, my) -> Drag.HUE
            Rect(alphaBar.x, alphaBar.y - 3f, alphaBar.w, alphaBar.h + 6f).contains(mx, my) -> Drag.ALPHA
            else -> Drag.NONE
        }
        if (drag != Drag.NONE) {
            updateDrag(mx, my)
            return true
        }
        if (hexRect.contains(mx, my)) {
            mc.keyboardHandler.clipboard = hexString(setting?.value ?: return true)
            ClickGuiScreen.toast("Copied ${hexString(setting!!.value)}")
            return true
        }
        if (pasteRect.contains(mx, my)) {
            val match = Regex("#?([0-9a-fA-F]{8}|[0-9a-fA-F]{6})").find(mc.keyboardHandler.clipboard)
            if (match == null) {
                ClickGuiScreen.toast("Clipboard has no color")
            } else {
                val digits = match.groupValues[1]
                setFrom(Color(if (digits.length == 6) (0xFF000000 or digits.toLong(16)).toInt() else digits.toLong(16).toInt()))
                ClickGuiScreen.toast("Pasted ${hexString(setting!!.value)}")
            }
            return true
        }
        if (previewOld.contains(mx, my)) {
            setFrom(original)
            return true
        }
        presetRects.firstOrNull { it.first.contains(mx, my) }?.let { (_, preset) -> setFrom(preset.withAlpha((alpha * 255).toInt())) }
        return true
    }

    private val mc get() = Minecraft.getInstance()

    private fun hexString(color: Color) =
        if (color.alpha == 255) "#%06X".format(color.argb and 0xFFFFFF) else "#%08X".format(color.argb)

    private fun setFrom(color: Color) {
        val hsb = AwtColor.RGBtoHSB(color.red, color.green, color.blue, null)
        hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2]; alpha = color.alphaF
        apply()
    }

    fun mouseReleased() {
        drag = Drag.NONE
    }

    fun keyPressed(key: Int): Boolean {
        if (!isOpen || closing) return false
        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
            close()
            return true
        }
        return false
    }

    companion object {
        private const val WIDTH = 150f
        private const val PAD = 8f
        private const val HEIGHT = PAD + 16f + 84f + 7f + 7f + 7f + 7f + 9f + 12f + PAD

        private val PRESETS = listOf(
            Color(240, 104, 104), Color(255, 174, 66), Color(70, 210, 158),
            Color(84, 156, 255), Color(160, 112, 255), Color(255, 104, 168), Color(255, 255, 255),
        )
    }
}
