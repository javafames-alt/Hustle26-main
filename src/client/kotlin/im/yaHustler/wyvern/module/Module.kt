package im.yaHustler.wyvern.module

import im.yaHustler.wyvern.event.EventBus
import im.yaHustler.wyvern.event.Listenable
import im.yaHustler.wyvern.event.Listener
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.setting.Configurable
import im.yaHustler.wyvern.setting.KeybindSetting
import im.yaHustler.wyvern.ui.hud.element.Notifications
import im.yaHustler.wyvern.util.Keys
import net.minecraft.client.Minecraft

enum class Category(val displayName: String, val icon: Int) {
    COMBAT("Бой", Icons.CROSSHAIR),
    MOVEMENT("Движение", Icons.BOLT),
    PLAYER("Игрок", Icons.GAMEPAD),
    RENDER("Графика", Icons.EYE),
    MISC("Разное", Icons.WIDGETS),
    CLIENT("Клиент", Icons.EDIT),
}

abstract class Module(
    val name: String,
    val description: String,
    val category: Category,
    defaultKey: Int = Keys.NONE,
    val hidden: Boolean = false,
) : Configurable(), Listenable {

    override val listeners = mutableListOf<Listener<*>>()

    protected val mc: Minecraft get() = Minecraft.getInstance()

    open val keybind = register(KeybindSetting("Бинд", defaultKey, "Клавиша включения модуля", { true }))

    /** Bridged modules keep their state in the Java registry; wyvern-side key handling skips them. */
    open val bridged: Boolean get() = false

    open var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                EventBus.subscribe(this)
                onEnable()
            } else {
                EventBus.unsubscribe(this)
                onDisable()
            }
            Notifications.module(this, value)
        }
    open val consumesKeybind: Boolean get() = false
    open val suffix: String? get() = null

    fun toggle() {
        enabled = !enabled
    }

    protected open fun onEnable() {}
    protected open fun onDisable() {}
    protected val inGame get() = mc.player != null && mc.level != null
}
