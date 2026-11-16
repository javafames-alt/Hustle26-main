package im.yaHustler.wyvern.module.impl.render

import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.MultiSetting
import im.yaHustler.wyvern.setting.NumberSetting
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity

/**
 * Stub of the original wyvern NameTags module. The Hustle client has its own Tags module,
 * so only the surface used by [EspPreview]/[GlowRenderer] is kept: the preview panel renders
 * the model without a nametag overlay and glow ESP stays disabled.
 */
object NameTags {
    val enabled = false

    val elements = MultiSetting("Элементы", listOf("Имя", "Хп", "Дистанция", "Снаряжение"), emptySet(), "", { false })
    val scaleSetting = NumberSetting("Размер", 1.0, 0.5..1.5, 0.05, "", { false })
    val spinModel = BooleanSetting("Вращать модель", true, "", { false })
    val spinSpeed = NumberSetting("Скорость вращения", 1.0, 0.1..5.0, 0.1, "", { false })
    val glowTargets = MultiSetting("Свечение", listOf("Игроки", "Друзья", "Мобы", "Предметы"), emptySet(), "", { false })
    val glowFill = NumberSetting("Заливка свечения", 0.12, 0.0..0.6, 0.02, "", { false })

    fun drawPreview(renderer: Renderer2D, entity: LivingEntity, x: Float, y: Float, size: Float) {}

    fun drawDropPreview(renderer: Renderer2D, entity: ItemEntity, x: Float, y: Float, size: Float) {}
}
