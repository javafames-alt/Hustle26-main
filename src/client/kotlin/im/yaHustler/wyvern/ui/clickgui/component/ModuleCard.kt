package im.yaHustler.wyvern.ui.clickgui.component

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.ButtonSetting
import im.yaHustler.wyvern.setting.ColorSetting
import im.yaHustler.wyvern.setting.KeybindSetting
import im.yaHustler.wyvern.setting.ModeSetting
import im.yaHustler.wyvern.setting.MultiSetting
import im.yaHustler.wyvern.setting.NumberSetting
import im.yaHustler.wyvern.setting.TextSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.Keys
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing

class ModuleCard(val module: Module) {
    private val components: List<SettingComponent> = module.settings.mapNotNull {
        when (it) {
            is BooleanSetting -> BooleanComponent(it)
            is NumberSetting -> SliderComponent(it)
            is ModeSetting -> ModeComponent(it)
            is ColorSetting -> ColorComponent(it)
            is MultiSetting -> MultiComponent(it)
            is KeybindSetting -> if (it == module.keybind) null else KeybindComponent(it)
            is TextSetting -> TextComponent(it)
            is ButtonSetting -> ButtonComponent(it)
        }
    }

    private val appear = components.associateWith { c -> Animation(190).also { it.snap(c.setting.isVisible) } }

    private fun shown(component: SettingComponent) = appear.getValue(component).update(component.setting.isVisible)

    private val visibleComponents get() = components.filter { shown(it) > 0.001f }

    private val toggleAnim = Animation(170).also { it.snap(module.enabled) }
    private val listenAnim = Animation(150)
    private val chipHoverAnim = Animation(110)
    private val boxHoverAnim = Animation(110)

    var listening = false
        private set

    val bounds = Rect()
    val header = Rect()
    val checkbox = Rect()
    val bindChip = Rect()

    val height: Float
        get() {
            val settings = visibleComponents
            if (settings.isEmpty()) return HEADER
            return HEADER + 1f + PADDING + settings.sumOf { (it.height * shown(it)).toDouble() }.toFloat() + PADDING - SettingComponent.GAP
        }

    fun render(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float) {
        val h = height
        bounds.set(x, y, width, h)
        header.set(x, y, width, HEADER)

        r.roundedRect(x, y, width, h, Theme.CARD_RADIUS, Theme.card)
        r.outline(x, y, width, h, Theme.CARD_RADIUS, 0.5f, Theme.cardBorder)

        val on = toggleAnim.update(module.enabled)
        val box = 17f
        checkbox.set(x + width - 7f - box, y + (HEADER - box) / 2f, box, box)
        val boxHover = boxHoverAnim.update(checkbox.contains(mouseX, mouseY))
        r.roundedRect(checkbox.x, checkbox.y, box, box, 5f, Theme.control.mix(Theme.controlHover, boxHover))
        r.outline(checkbox.x, checkbox.y, box, box, 5f, 0.5f, Theme.cardBorder.withAlpha(((1f - on) * Theme.cardBorder.alpha).toInt()))
        if (on > 0.01f) {
            r.withAlpha(on) { r.roundedRect(checkbox.x, checkbox.y, box, box, 5f, Theme.accent) }
        }
        r.withPose {
            r.scaleAround(checkbox.centerX, checkbox.centerY, 0.8f + 0.2f * Easing.OUT_BACK.ease(on))
            r.icon(Icons.CHECK, checkbox.centerX, checkbox.centerY, 9f, Theme.textFaint.mix(Theme.onAccent, on))
        }

        val listen = listenAnim.update(listening)
        val bindText = if (listening) "..." else Keys.name(module.keybind.value).uppercase()
        val tw = r.textWidth(bindText, 6.5f, Theme.bodyFont)
        val chipH = 14f
        val cw = 5f + 8f + 3f + tw + 6f
        bindChip.set(checkbox.x - 5f - cw, y + (HEADER - chipH) / 2f, cw, chipH)
        val chipHover = chipHoverAnim.update(bindChip.contains(mouseX, mouseY))
        r.roundedRect(bindChip.x, bindChip.y, cw, chipH, 3.5f,
            Theme.control.mix(Theme.controlHover, chipHover).mix(Theme.accent.withAlpha(90), listen))
        r.icon(Icons.KEYBOARD, bindChip.x + 5f + 4f, bindChip.centerY, 7.5f, Theme.textFaint.mix(Theme.text, listen))
        r.textCenteredY(bindText, bindChip.x + 5f + 8f + 3f, bindChip.centerY, Theme.textDim.mix(Theme.text, listen), 6.5f, Theme.bodyFont)

        val nameRoom = bindChip.x - 6f - (x + PADDING_X)
        r.textCenteredY(SettingComponent.ellipsize(module.name, Theme.TITLE_SIZE, nameRoom, Theme.titleFont), x + PADDING_X, y + HEADER / 2f,
            Theme.text, Theme.TITLE_SIZE, Theme.titleFont)

        val settings = visibleComponents
        if (settings.isEmpty()) return
        r.rect(x, y + HEADER, width, 0.5f, Theme.divider)
        var cy = y + HEADER + 1f + PADDING
        for (component in settings) {
            val t = shown(component)
            val h = component.height * t
            if (t >= 0.999f) {
                component.render(r, x + PADDING_X, cy, width - PADDING_X * 2, mouseX, mouseY)
            } else {
                r.scissor(x, cy, width, h) {
                    r.withAlpha(t) { component.render(r, x + PADDING_X, cy, width - PADDING_X * 2, -1f, -1f) }
                }
            }
            cy += h
        }
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != Mouse.LEFT && button != Mouse.RIGHT) {
            if (listening) {
                module.keybind.value = Keys.mouse(button)
                listening = false
                return true
            }
            if (components.any { it.mouseBind(button) }) return true
        }
        components.forEach(SettingComponent::stopListening)
        if (!bounds.contains(mouseX, mouseY)) {
            listening = false
            return false
        }
        if (bindChip.contains(mouseX, mouseY)) {
            when (button) {
                Mouse.LEFT -> listening = !listening
                Mouse.RIGHT -> {
                    module.keybind.value = Keys.NONE
                    listening = false
                }
            }
            return true
        }
        listening = false
        if (header.contains(mouseX, mouseY)) {
            if (button == Mouse.LEFT) module.toggle()
            return true
        }
        for (component in visibleComponents) {
            if (shown(component) < 0.999f) continue
            if (component.mouseClicked(mouseX, mouseY, button)) return true
        }
        return true
    }

    fun mouseReleased(button: Int) = components.forEach { it.mouseReleased(button) }

    fun keyPressed(key: Int): Boolean {
        if (components.any { it.keyPressed(key) }) return true
        if (!listening) return false
        module.keybind.value = when (key) {
            InputConstants.KEY_ESCAPE, InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> Keys.NONE
            else -> key
        }
        listening = false
        return true
    }

    fun charTyped(codepoint: Int) = components.any { it.charTyped(codepoint) }

    fun component(settingName: String): SettingComponent? = components.firstOrNull { it.setting.name == settingName }

    fun stopListening() {
        listening = false
        components.forEach(SettingComponent::stopListening)
    }

    companion object {
        const val HEADER = 29f
        const val PADDING = 8f
        const val PADDING_X = 10f
    }
}
