package im.yaHustler.wyvern.setting

import im.yaHustler.wyvern.render.color.Color

abstract class Configurable {
    val settings = mutableListOf<Setting<*>>()

    protected fun <S : Setting<*>> register(setting: S): S = setting.also(settings::add)

    protected fun boolean(
        name: String,
        default: Boolean,
        description: String = "",
        visible: () -> Boolean = { true },
    ) = register(BooleanSetting(name, default, description, visible))

    protected fun number(
        name: String,
        default: Double,
        range: ClosedFloatingPointRange<Double>,
        step: Double = 0.1,
        description: String = "",
        visible: () -> Boolean = { true },
    ) = register(NumberSetting(name, default, range, step, description, visible))

    protected fun mode(
        name: String,
        default: String,
        vararg modes: String,
        description: String = "",
        visible: () -> Boolean = { true },
    ) = register(ModeSetting(name, default, modes.toList(), description, visible))

    protected fun text(
        name: String,
        default: String,
        description: String = "",
        visible: () -> Boolean = { false },
    ) = register(TextSetting(name, default, description, visible))

    protected fun button(
        name: String,
        description: String = "",
        visible: () -> Boolean = { true },
        action: () -> Unit,
    ) = register(ButtonSetting(name, description, visible, action))

    protected fun multi(
        name: String,
        vararg options: String,
        default: Set<String> = emptySet(),
        description: String = "",
        visible: () -> Boolean = { true },
    ) = register(MultiSetting(name, options.toList(), default, description, visible))

    protected fun color(
        name: String,
        default: Color,
        description: String = "",
        visible: () -> Boolean = { true },
    ) = register(ColorSetting(name, default, description, visible))

    fun setting(name: String) = settings.firstOrNull { it.name.equals(name, ignoreCase = true) }

    protected fun <T, S : Setting<T>> S.locked(fixed: T): S = apply { lock(fixed) }
}
