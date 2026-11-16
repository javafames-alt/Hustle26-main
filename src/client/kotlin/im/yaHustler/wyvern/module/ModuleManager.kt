package im.yaHustler.wyvern.module

import im.yaHustler.wyvern.bridge.HustleBridge
import im.yaHustler.wyvern.event.EventBus
import im.yaHustler.wyvern.event.KeyEvent
import im.yaHustler.wyvern.event.Listenable
import im.yaHustler.wyvern.event.Listener
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.module.impl.client.ClickGuiModule
import im.yaHustler.wyvern.module.impl.client.HudModule
import im.yaHustler.wyvern.module.impl.render.Particles
import im.yaHustler.wyvern.module.impl.render.SwingAnimation
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.util.Keys
import net.minecraft.client.Minecraft

object ModuleManager : Listenable {
    override val listeners = mutableListOf<Listener<*>>()

    val modules = mutableListOf<Module>()

    fun init(registry: im.yaHustler.module.ModuleRegistry) {
        register(
            HudModule, ClickGuiModule,
            Particles, SwingAnimation,
        )
        register(*HustleBridge.bridge(registry).toTypedArray())

        on<KeyEvent> { event ->
            if (!event.isPress || Minecraft.getInstance().gui.screen() != null) return@on
            if (event.key == Keys.NONE) return@on
            // Bridged Java modules handle their own keybinds through ModuleRegistry.handleKeys.
            val bound = modules.filter { !it.bridged && it.keybind.value == event.key }
            bound.forEach(Module::toggle)
            for (module in modules) {
                if (module.bridged) continue
                for (setting in module.settings) {
                    if (setting !is BooleanSetting || setting.bind != event.key) continue
                    setting.toggle()
                    im.yaHustler.wyvern.ui.hud.element.Notifications.setting(module, setting)
                }
            }
            if (bound.any { it.consumesKeybind }) event.cancel()
        }
        EventBus.subscribe(this)
    }

    fun register(vararg modules: Module) {
        this.modules += modules
    }

    operator fun get(name: String) = modules.firstOrNull { it.name.equals(name, ignoreCase = true) }

    inline fun <reified T : Module> get(): T = modules.first { it is T } as T

    fun byCategory(category: Category) = modules.filter { it.category == category }

    val enabledModules get() = modules.filter { it.enabled }
}
