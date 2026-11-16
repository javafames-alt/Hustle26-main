package im.yaHustler.wyvern.module.impl.client

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.ui.clickgui.ClickGuiScreen
import im.yaHustler.wyvern.ui.theme.Theme

object ClickGuiModule : Module("ClickGUI", "Открывает меню модулей", Category.CLIENT, im.yaHustler.wyvern.util.Keys.NONE, hidden = true) {

    @JvmStatic
    fun customMainMenu() = false

    val theme = text("Тема", Theme.palettes.first().name).onChange(Theme::select)

    val customBackground = color("Фон", Color(15, 16, 18), "Окно и водяной знак", visible = { false })
    val customCard = color("Карточки", Color(24, 25, 28), "Карточки модулей", visible = { false })
    val customAccent = color("Акцент", Color(120, 200, 255), "Галочки, ползунки, иконки", visible = { false })
    val customText = color("Текст", Color(244, 244, 246), "Подписи и заголовки", visible = { false })
    val customColors get() = listOf(customBackground, customCard, customAccent, customText)

    init {
        Theme.customColors = { customColors.map { it.value } }
    }

    override val consumesKeybind get() = true

    override fun onEnable() {
        mc.gui.setScreen(ClickGuiScreen())
        enabled = false
    }
}
