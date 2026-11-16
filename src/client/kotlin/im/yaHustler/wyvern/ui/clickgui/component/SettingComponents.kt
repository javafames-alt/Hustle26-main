package im.yaHustler.wyvern.ui.clickgui.component

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.font.TextRenderer
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.ButtonSetting
import im.yaHustler.wyvern.setting.ColorSetting
import im.yaHustler.wyvern.setting.KeybindSetting
import im.yaHustler.wyvern.setting.ModeSetting
import im.yaHustler.wyvern.setting.MultiSetting
import im.yaHustler.wyvern.setting.NumberSetting
import im.yaHustler.wyvern.setting.Setting
import im.yaHustler.wyvern.setting.TextSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.clickgui.ClickGuiScreen
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.Keys
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.client.Minecraft
import java.util.Locale

sealed class SettingComponent(open val setting: Setting<*>) {
    protected var width = 120f
        private set

    protected open fun reserved(): Float = 0f

    private val textWidth get() = (width - reserved()).coerceAtLeast(20f)

    private var wrapKey = ""
    private var wrapped: List<String> = emptyList()

    protected fun descriptionLines(): List<String> {
        val text = setting.description
        if (text.isBlank()) return emptyList()
        val key = "$text|${textWidth}"
        if (key != wrapKey) {
            wrapKey = key
            wrapped = wrap(text, Theme.DESCRIPTION_SIZE, textWidth)
        }
        return wrapped
    }

    protected val labelHeight get() = NAME_LINE + descriptionLines().size * DESCRIPTION_LINE

    abstract val height: Float

    fun render(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        this.width = width
        draw(r, x, y, width, mouseX, mouseY)
    }

    protected abstract fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float)
    open fun mouseClicked(mouseX: Float, mouseY: Float, button: Int) = false
    open fun mouseReleased(button: Int) {}

    open fun keyPressed(key: Int) = false

    open fun charTyped(codepoint: Int) = false

    open fun mouseBind(button: Int) = false
    open fun stopListening() {}

    protected fun Renderer2D.labels(x: Float, y: Float) {
        text(ellipsize(setting.name, Theme.NAME_SIZE, textWidth), x, y, Theme.text, Theme.NAME_SIZE, Theme.bodyFont)
        var ly = y + NAME_LINE
        for (line in descriptionLines()) {
            text(line, x, ly, Theme.textFaint, Theme.DESCRIPTION_SIZE, Theme.bodyFont)
            ly += DESCRIPTION_LINE
        }
    }

    companion object {
        const val NAME_LINE = 9.5f
        const val DESCRIPTION_LINE = 8.5f
        const val GAP = 7f
        const val CONTROL_GAP = 6f

        fun wrap(text: String, size: Float, maxWidth: Float): List<String> {
            val font = Theme.bodyFont
            val lines = mutableListOf<String>()
            var line = ""
            for (word in text.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && font.width(candidate, size) > maxWidth) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }

        fun ellipsize(text: String, size: Float, maxWidth: Float, font: TextRenderer = Theme.bodyFont): String {
            if (font.width(text, size) <= maxWidth) return text
            var end = text.length
            while (end > 1 && font.width(text.substring(0, end) + "…", size) > maxWidth) end--
            return text.substring(0, end) + "…"
        }
    }
}

class BooleanComponent(override val setting: BooleanSetting) : SettingComponent(setting) {
    private val anim = Animation(240, Easing.IN_OUT_CUBIC).also { it.snap(setting.value) }
    private val hoverAnim = Animation(110)
    private val listenAnim = Animation(150)
    private val pill = Rect()

    val row = Rect()

    var listening = false
        private set

    private fun bindText() = when {
        listening -> "..."
        setting.bind != Keys.NONE -> Keys.name(setting.bind).uppercase()
        else -> null
    }

    private fun chipWidth() = bindText()?.let { Theme.bodyFont.width(it, 6f) + 8f } ?: 0f

    override fun reserved() = PILL_W + CONTROL_GAP + chipWidth().let { if (it > 0f) it + 4f else 0f }

    override val height get() = labelHeight + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        row.set(x, y, width, labelHeight)
        val t = anim.update(setting.value)
        pill.set(x + width - PILL_W, y + (labelHeight - PILL_H) / 2f - 0.5f, PILL_W, PILL_H)
        val bind = bindText()
        val listen = listenAnim.update(listening)
        if (bind != null) {
            val cw = chipWidth()
            val cx = pill.x - 4f - cw
            r.roundedRect(cx, pill.y, cw, PILL_H, RADIUS, Theme.control.mix(Theme.accent.withAlpha(90), listen))
            r.textCenteredY(bind, cx + 4f, pill.centerY, Theme.textDim.mix(Theme.text, listen), 6f, Theme.bodyFont)
        }
        val hover = hoverAnim.update(pill.contains(mouseX, mouseY))
        r.roundedRect(pill.x, pill.y, pill.w, pill.h, RADIUS, Theme.control.mix(Theme.controlHover, hover).mix(Theme.accent, t))
        val knob = KNOB + 0.4f * hover
        val knobX = pill.x + 6f + (pill.w - 12f) * t
        val drift = 2f * t
        r.withAlpha(1f - t) {
            r.textCenteredY("Off", pill.x + pill.w - 5f - r.textWidth("Off", 6.5f, Theme.bodyFont) + drift, pill.centerY, Theme.textFaint, 6.5f, Theme.bodyFont)
        }
        r.withAlpha(t) {
            r.textCenteredY("On", pill.x + 5f - 2f + drift, pill.centerY, Theme.onAccent, 6.5f, Theme.bodyFont)
        }
        r.circle(knobX, pill.centerY, knob, Theme.text.mix(Theme.onAccent, t))
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button == Mouse.MIDDLE && row.contains(mouseX, mouseY)) {
            listening = true
            return true
        }
        if (button != Mouse.LEFT || !pill.contains(mouseX, mouseY)) return false
        setting.toggle()
        return true
    }

    override fun mouseBind(button: Int): Boolean {
        if (!listening) return false
        setting.bind = Keys.mouse(button)
        listening = false
        return true
    }

    override fun keyPressed(key: Int): Boolean {
        if (!listening) return false
        setting.bind = when (key) {
            InputConstants.KEY_ESCAPE, InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> Keys.NONE
            else -> key
        }
        listening = false
        return true
    }

    override fun stopListening() {
        listening = false
    }

    private companion object {
        const val PILL_W = 27f
        const val PILL_H = 12f
        const val KNOB = 3.6f
        const val RADIUS = 3.5f
    }
}

class SliderComponent(override val setting: NumberSetting) : SettingComponent(setting) {
    private var dragging = false
    private val fill = Smooth(-1f, speed = 26f)
    private val grab = Animation(180, Easing.IN_OUT_CUBIC)
    private val track = Rect()

    private fun valueText() = if (setting.step >= 1.0) "%.0f".format(Locale.ROOT, setting.value) else "%.2f".format(Locale.ROOT, setting.value)
    private fun boxWidth() = maxOf(Theme.bodyFont.width(valueText(), 6.5f) + 8f, 14f)

    override fun reserved() = boxWidth() + CONTROL_GAP

    override val height get() = labelHeight + 7f + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        track.set(x - 2f, y + labelHeight, width + 4f, 9f)
        if (dragging) {
            val t = ((mouseX - x) / width).coerceIn(0f, 1f)
            setting.value = setting.min + (setting.max - setting.min) * t
        }

        val text = valueText()
        val bw = boxWidth()
        r.roundedRect(x + width - bw, y - 0.5f, bw, 11f, 3.5f, Theme.control)
        r.textCenteredY(text, x + width - bw / 2f - r.textWidth(text, 6.5f, Theme.bodyFont) / 2f, y + 5f, Theme.textDim, 6.5f, Theme.bodyFont)

        val target = ((setting.value - setting.min) / (setting.max - setting.min)).toFloat()
        val t = if (fill.value < 0f) target.also(fill::snap) else fill.update(target)
        val g = grab.update(dragging || track.contains(mouseX, mouseY))
        val barY = y + labelHeight + 3.5f
        r.roundedRect(x, barY, width, 2f, 1f, Theme.control)
        r.roundedRect(x, barY, width * t, 2f, 1f, Theme.accent)
        r.circle(x + width * t, barY + 1f, 2.8f + 0.6f * g, Theme.text)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != Mouse.LEFT || !track.contains(mouseX, mouseY)) return false
        dragging = true
        return true
    }

    override fun mouseReleased(button: Int) {
        if (button == Mouse.LEFT) dragging = false
    }
}

sealed class DropdownComponent(setting: Setting<*>) : SettingComponent(setting) {
    protected abstract val options: List<String>
    protected abstract fun summary(): String
    protected abstract fun isSelected(option: String): Boolean
    protected abstract fun pick(option: String): Boolean

    private val fieldRect = Rect()
    private val fieldHover = Animation(110)
    private val open = Animation(190)
    private var expanded = false
    private val rows = mutableListOf<Pair<Rect, String>>()
    private val rowHover = mutableMapOf<String, Animation>()
    private val rowSelected = mutableMapOf<String, Animation>()

    private val listHeight get() = options.size * ROW + 4f

    override val height get() = labelHeight + 4f + FIELD + (listHeight + 3f) * open.update(expanded) + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        val t = open.update(expanded)
        fieldRect.set(x, y + labelHeight + 4f, width, FIELD)
        val hover = fieldHover.update(fieldRect.contains(mouseX, mouseY))
        r.roundedRect(fieldRect.x, fieldRect.y, fieldRect.w, fieldRect.h, Theme.CONTROL_RADIUS,
            Theme.control.mix(Theme.controlHover, maxOf(hover, t * 0.6f)))
        r.scissor(fieldRect.x, fieldRect.y, fieldRect.w - 18f, fieldRect.h) {
            r.textCenteredY(summary(), fieldRect.x + 7f, fieldRect.centerY, Theme.text, 7f, Theme.bodyFont)
        }
        r.withPose {
            r.translate(fieldRect.x + fieldRect.w - 9f, fieldRect.centerY)
            r.graphics.pose().rotate((Math.PI / 2 * t).toFloat())
            r.icon(Icons.CHEVRON_RIGHT, 0f, 0f, 8f, Theme.textFaint.mix(Theme.text, t))
        }

        rows.clear()
        if (t <= 0.01f) return
        val listY = fieldRect.y + FIELD + 3f
        val visible = listHeight * t
        r.withAlpha(t) {
            r.scissor(x, listY, width, visible) {
                r.roundedRect(x, listY, width, listHeight, Theme.CONTROL_RADIUS, Theme.control)
                var ry = listY + 2f
                for (option in options) {
                    val rect = Rect(x + 2f, ry, width - 4f, ROW)
                    rows += rect to option
                    val h = rowHover.getOrPut(option) { Animation(100) }.update(rect.contains(mouseX, mouseY) && ry < listY + visible)
                    val sel = rowSelected.getOrPut(option) { Animation(150).also { a -> a.snap(isSelected(option)) } }
                        .update(isSelected(option))
                    if (h > 0.01f) r.roundedRect(rect.x, rect.y, rect.w, rect.h, 4f, Theme.controlHover.withAlpha((Theme.controlHover.alpha * h).toInt()))
                    renderOption(r, option, rect, sel)
                    ry += ROW
                }
            }
        }
    }

    val fieldArea: Rect get() = fieldRect
    fun optionArea(option: String): Rect? = rows.firstOrNull { it.second == option }?.first

    protected open fun renderOption(r: Renderer2D, option: String, rect: Rect, selected: Float) {
        r.textCenteredY(option, rect.x + 6f, rect.centerY, Theme.textDim.mix(Theme.text, selected), 7f, Theme.bodyFont)
        if (selected > 0.01f) {
            r.icon(Icons.CHECK, rect.x + rect.w - 9f, rect.centerY, 8f, Theme.accent.withAlpha((255 * selected).toInt()))
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (fieldRect.contains(mouseX, mouseY)) {
            if (button == Mouse.LEFT || button == Mouse.RIGHT) expanded = !expanded
            return true
        }
        if (!expanded || button != Mouse.LEFT) return false
        val row = rows.firstOrNull { it.first.contains(mouseX, mouseY) } ?: return false
        if (pick(row.second)) expanded = false
        return true
    }

    companion object {
        const val FIELD = 15f
        const val ROW = 13f
    }
}

class ModeComponent(override val setting: ModeSetting) : DropdownComponent(setting) {
    override val options get() = setting.modes
    override fun summary() = setting.value
    override fun isSelected(option: String) = setting.value == option
    override fun pick(option: String): Boolean {
        setting.value = option
        return true
    }
}

class MultiComponent(override val setting: MultiSetting) : DropdownComponent(setting) {
    override val options get() = setting.options

    override fun summary(): String {
        val picked = setting.options.filter { it in setting }
        return when {
            picked.isEmpty() -> "None"
            picked.size == setting.options.size -> "All"
            else -> picked.joinToString(", ")
        }
    }

    override fun isSelected(option: String) = option in setting

    override fun pick(option: String): Boolean {
        setting.toggle(option)
        return false
    }

    override fun renderOption(r: Renderer2D, option: String, rect: Rect, selected: Float) {
        val box = 8.5f
        val bx = rect.x + 5f
        val by = rect.centerY - box / 2f
        r.roundedRect(bx, by, box, box, 2.5f, Theme.controlHover)
        if (selected > 0.01f) {
            r.withAlpha(selected) { r.roundedRect(bx, by, box, box, 2.5f, Theme.accent) }
            r.icon(Icons.CHECK, bx + box / 2f, by + box / 2f, 7f, Theme.onAccent.withAlpha((255 * selected).toInt()))
        }
        r.textCenteredY(option, bx + box + 6f, rect.centerY, Theme.textDim.mix(Theme.text, selected), 7f, Theme.bodyFont)
    }
}

class ColorComponent(override val setting: ColorSetting) : SettingComponent(setting) {
    private val chip = Rect()
    private val hoverAnim = Animation(110)

    private fun hex() = "#%06X".format(setting.value.argb and 0xFFFFFF)
    private fun chipWidth() = 3f + 8f + 4f + Theme.bodyFont.width(hex(), 6.5f) + 5f

    override fun reserved() = chipWidth() + CONTROL_GAP

    override val height get() = labelHeight + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        val cw = chipWidth()
        chip.set(x + width - cw, y + (labelHeight - 12f) / 2f - 0.5f, cw, 12f)
        val hover = hoverAnim.update(chip.contains(mouseX, mouseY))
        r.roundedRect(chip.x, chip.y, chip.w, chip.h, 3.5f, Theme.control.mix(Theme.controlHover, hover))
        r.roundedRect(chip.x + 3f, chip.y + 2.5f, 8f, 7f, 2f, setting.value.withAlpha(255))
        r.textCenteredY(hex(), chip.x + 3f + 8f + 4f, chip.centerY, Theme.textDim, 6.5f, Theme.bodyFont)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != Mouse.LEFT || !chip.contains(mouseX, mouseY)) return false
        ClickGuiScreen.picker.open(setting, chip)
        return true
    }
}

class KeybindComponent(override val setting: KeybindSetting) : SettingComponent(setting) {
    private val field = Rect()
    private val hoverAnim = Animation(110)
    private val listenAnim = Animation(150)
    var listening = false
        private set
    private fun bindText() = if (listening) "..." else Keys.name(setting.value).uppercase()
    override val height get() = labelHeight + 4f + 15f + GAP
    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        val text = bindText()
        val tw = r.textWidth(text, 7f, Theme.bodyFont)
        val chipW = minOf(width, 24f + tw)
        field.set(x, y + labelHeight + 4f, chipW, 15f)
        val hover = hoverAnim.update(field.contains(mouseX, mouseY))
        val listen = listenAnim.update(listening)
        r.roundedRect(field.x, field.y, field.w, field.h, 4f, Theme.control.mix(Theme.controlHover, hover).mix(Theme.accent.withAlpha(90), listen))
        r.icon(Icons.KEYBOARD, field.x + 8.5f, field.centerY, 7.5f, Theme.textFaint.mix(Theme.text, listen))
        r.textCenteredY(text, field.x + 16.5f, field.centerY, Theme.textDim.mix(Theme.text, listen), 7f, Theme.bodyFont)
    }
    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!field.contains(mouseX, mouseY)) return false
        when (button) {
            Mouse.LEFT -> listening = !listening
            Mouse.RIGHT -> {
                setting.value = Keys.NONE
                listening = false
            }
        }
        return true
    }
    override fun mouseBind(button: Int): Boolean {
        if (!listening) return false
        setting.value = Keys.mouse(button)
        listening = false
        return true
    }
    override fun keyPressed(key: Int): Boolean {
        if (!listening) return false
        setting.value = when (key) {
            InputConstants.KEY_ESCAPE, InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> Keys.NONE
            else -> key
        }
        listening = false
        return true
    }
    override fun stopListening() {
        listening = false
    }
}

class TextComponent(override val setting: TextSetting) : SettingComponent(setting) {
    private val field = Rect()
    private val hoverAnim = Animation(110)
    private val focusAnim = Animation(150)

    var focused = false
        private set(value) {
            if (field == value) return
            field = value
            val input = Minecraft.getInstance().textInputManager()
            if (value) input.startTextInput(this) else input.stopTextInput(this)
        }

    override val height get() = labelHeight + 4f + FIELD + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        r.labels(x, y)
        field.set(x, y + labelHeight + 4f, width, FIELD)
        val hover = hoverAnim.update(field.contains(mouseX, mouseY))
        val focus = focusAnim.update(focused)
        r.roundedRect(field.x, field.y, field.w, field.h, Theme.CONTROL_RADIUS,
            Theme.control.mix(Theme.controlHover, hover).mix(Theme.accent.withAlpha(90), focus))

        val text = setting.value
        val room = field.w - PADDING * 2
        val textW = r.textWidth(text, SIZE, Theme.bodyFont)
        val shift = if (focused) maxOf(0f, textW - room + 2f) else 0f
        r.scissor(field.x + 2f, field.y, field.w - 4f, field.h) {
            if (text.isEmpty() && !focused) {
                r.textCenteredY("Пусто", field.x + PADDING, field.centerY, Theme.textFaint, SIZE, Theme.bodyFont)
            } else {
                r.textCenteredY(text, field.x + PADDING - shift, field.centerY, Theme.text, SIZE, Theme.bodyFont)
            }
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                r.rect(field.x + PADDING - shift + textW + 0.5f, field.centerY - 4f, 0.75f, 8f, Theme.text)
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!field.contains(mouseX, mouseY)) return false
        when (button) {
            Mouse.LEFT -> focused = true
            Mouse.RIGHT -> setting.value = ""
        }
        return true
    }

    override fun charTyped(codepoint: Int): Boolean {
        if (!focused) return false
        if (setting.value.length < MAX && !Character.isISOControl(codepoint)) setting.value += String(Character.toChars(codepoint))
        return true
    }

    override fun keyPressed(key: Int): Boolean {
        if (!focused) return false
        val mc = Minecraft.getInstance()
        when {
            key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER -> focused = false
            key == InputConstants.KEY_BACKSPACE -> setting.value = if (mc.hasControlDown()) "" else setting.value.dropLast(1)
            key == InputConstants.KEY_V && mc.hasControlDown() ->
                setting.value = (setting.value + mc.keyboardHandler.clipboard.filterNot(Char::isISOControl)).take(MAX)
        }
        return true
    }

    override fun stopListening() {
        focused = false
    }

    private companion object {
        const val FIELD = 15f
        const val PADDING = 7f
        const val SIZE = 7f
        const val MAX = 64
    }
}

class ButtonComponent(override val setting: ButtonSetting) : SettingComponent(setting) {
    private val button = Rect()
    private val hoverAnim = Animation(110)
    private val pressAnim = Animation(160)
    private var pressedAt = 0L

    override val height get() = FIELD + (if (setting.description.isBlank()) 0f else descriptionLines().size * DESCRIPTION_LINE + 3f) + GAP

    override fun draw(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        button.set(x, y, width, FIELD)
        val hover = hoverAnim.update(button.contains(mouseX, mouseY))
        val press = pressAnim.update(System.currentTimeMillis() - pressedAt < 120)
        r.withPose {
            r.scaleAround(button.centerX, button.centerY, 1f - 0.03f * press)
            r.roundedRect(button.x, button.y, button.w, button.h, Theme.CONTROL_RADIUS,
                Theme.accent.withAlpha((70 + 60 * hover).toInt()))
            r.outline(button.x, button.y, button.w, button.h, Theme.CONTROL_RADIUS, 0.5f, Theme.accent.withAlpha(150))
            val tw = r.textWidth(setting.name, 7.5f, Theme.bodyFont)
            val left = button.centerX - (9f + 5f + tw) / 2f
            r.icon(Icons.EYE, left + 4.5f, button.centerY, 9f, Theme.text)
            r.textCenteredY(setting.name, left + 9f + 5f, button.centerY, Theme.text, 7.5f, Theme.bodyFont)
        }
        var ly = y + FIELD + 3f
        for (line in descriptionLines()) {
            r.text(line, x, ly, Theme.textFaint, Theme.DESCRIPTION_SIZE, Theme.bodyFont)
            ly += DESCRIPTION_LINE
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != Mouse.LEFT || !this.button.contains(mouseX, mouseY)) return false
        pressedAt = System.currentTimeMillis()
        setting.press()
        return true
    }

    private companion object {
        const val FIELD = 17f
    }
}
