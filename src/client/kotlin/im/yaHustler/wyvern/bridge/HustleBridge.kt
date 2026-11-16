package im.yaHustler.wyvern.bridge

import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.ColorSetting
import im.yaHustler.wyvern.setting.KeybindSetting
import im.yaHustler.wyvern.setting.ModeSetting
import im.yaHustler.wyvern.setting.NumberSetting
import im.yaHustler.wyvern.setting.Setting
import im.yaHustler.wyvern.setting.TextSetting
import im.yaHustler.wyvern.ui.hud.element.Notifications
import im.yaHustler.wyvern.util.Keys

/** Maps the Java client's module categories onto the wyvern GUI categories. */
private fun mapCategory(category: im.yaHustler.module.Category): Category = when (category) {
    im.yaHustler.module.Category.COMBAT -> Category.COMBAT
    im.yaHustler.module.Category.MOVEMENT -> Category.MOVEMENT
    im.yaHustler.module.Category.RENDER -> Category.RENDER
    im.yaHustler.module.Category.PLAYER -> Category.PLAYER
    else -> Category.MISC
}

private fun toWyvernKey(javaKey: Int): Int = when {
    javaKey == -1 -> Keys.NONE
    javaKey in -107..-100 -> Keys.mouse(-100 - javaKey)
    else -> javaKey
}

private fun toJavaKey(wyvernKey: Int): Int = when {
    wyvernKey == Keys.NONE -> -1
    Keys.isMouse(wyvernKey) -> -100 - (wyvernKey - Keys.MOUSE_BASE)
    else -> wyvernKey
}

/** Wraps a Java module so it renders and toggles inside the wyvern ClickGUI. */
class JavaModuleAdapter(val java: im.yaHustler.module.Module) : Module(
    java.name(), java.description(), mapCategory(java.category()),
) {
    override val bridged get() = true

    override val keybind: KeybindSetting get() = moduleKeybind

    override var enabled: Boolean
        get() = java.enabled()
        set(value) {
            val was = java.enabled()
            java.setEnabled(value)
            if (was != value) Notifications.module(this, value)
        }

    private val moduleKeybind = object : KeybindSetting("Бинд", toWyvernKey(java.key()), "", { true }) {
        override var value: Int
            get() = toWyvernKey(java.key())
            set(v) = java.setKey(toJavaKey(v))
    }

    init {
        // Drop the base-class placeholder bind; the delegating one above talks to the Java module.
        settings.removeAll { it is KeybindSetting }
        register(moduleKeybind)
        for (setting in java.settings()) wrap(setting)?.let(::register)
    }

    private fun wrap(setting: im.yaHustler.setting.Setting<*>): Setting<*>? = when (setting) {
        is im.yaHustler.setting.BooleanSetting -> JavaBooleanSetting(setting)
        is im.yaHustler.setting.NumberSetting -> JavaNumberSetting(setting)
        is im.yaHustler.setting.ModeSetting -> JavaModeSetting(setting)
        is im.yaHustler.setting.ColorSetting -> JavaColorSetting(setting)
        is im.yaHustler.setting.KeySetting -> JavaKeybindSetting(setting)
        is im.yaHustler.setting.TextSetting -> JavaTextSetting(setting)
        is im.yaHustler.setting.ItemSetting -> JavaTextSetting(setting)
        else -> null
    }
}

class JavaBooleanSetting(private val j: im.yaHustler.setting.BooleanSetting) :
    BooleanSetting(j.name(), j.enabled(), "", { j.visible() }) {
    override var value: Boolean
        get() = j.enabled()
        set(v) = j.set(v)
}

class JavaNumberSetting(private val j: im.yaHustler.setting.NumberSetting) :
    NumberSetting(j.name(), j.get(), j.min()..j.max(), j.step(), "", { j.visible() }) {
    override var value: Double
        get() = j.get()
        set(v) = j.set(v)
}

class JavaModeSetting(private val j: im.yaHustler.setting.ModeSetting) :
    ModeSetting(j.name(), j.get(), j.modes(), "", { j.visible() }) {
    override var value: String
        get() = j.get()
        set(v) = j.set(v)
}

class JavaColorSetting(private val j: im.yaHustler.setting.ColorSetting) :
    ColorSetting(j.name(), Color(j.color()), "", { j.visible() }) {
    override var value: Color
        get() = Color(j.color())
        set(v) = j.set(v.argb)
}

class JavaKeybindSetting(private val j: im.yaHustler.setting.KeySetting) :
    KeybindSetting(j.name(), toWyvernKey(j.get()), "", { j.visible() }) {
    override var value: Int
        get() = toWyvernKey(j.get())
        set(v) = j.set(toJavaKey(v))
}

class JavaTextSetting(private val j: im.yaHustler.setting.Setting<String>) :
    TextSetting(j.name(), j.get(), "", { j.visible() }) {
    override var value: String
        get() = j.get()
        set(v) = j.set(v)
}

/** Entry point used by the Java client to expose its registry in the wyvern GUI. */
object HustleBridge {
    fun bridge(registry: im.yaHustler.module.ModuleRegistry): List<Module> =
        registry.all().filter { !registry.isHidden(it) }.map(::JavaModuleAdapter)
}
