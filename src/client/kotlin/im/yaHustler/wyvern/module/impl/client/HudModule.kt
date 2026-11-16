package im.yaHustler.wyvern.module.impl.client

import im.yaHustler.wyvern.event.Render2DEvent
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.NumberSetting
import im.yaHustler.wyvern.ui.clickgui.ClickGuiScreen
import im.yaHustler.wyvern.ui.hud.HudManager

object HudModule : Module("HUD", "Водяной знак и панели", Category.CLIENT, hidden = true) {
    init {
        for (element in HudManager.elements) {
            element.toggle = register(BooleanSetting(element.name, true, "Показывать панель «${element.name}»", { true }))
            element.posX = register(NumberSetting("${element.name} X", element.defaultX, 0.0..1.0, 0.0, "", { false }))
            element.posY = register(NumberSetting("${element.name} Y", element.defaultY, 0.0..1.0, 0.0, "", { false }))
            if (element.blurable) {
                element.blurRows = register(BooleanSetting("${element.name}: размытие", false, "", { false }))
                if (element.titled) element.blurHeader = register(BooleanSetting("${element.name}: размытие шапки", false, "", { false }))
            }
        }
        on<Render2DEvent> { event ->
            if (mc.gui.screen() is ClickGuiScreen) return@on
            HudManager.render(event.renderer)
        }
        enabled = true
    }
}
