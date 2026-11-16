package im.yaHustler.wyvern

import im.yaHustler.wyvern.config.ConfigManager
import im.yaHustler.wyvern.event.EventBus
import im.yaHustler.wyvern.event.Render2DEvent
import im.yaHustler.wyvern.event.Render3DEvent
import im.yaHustler.wyvern.event.TickEvent
import im.yaHustler.wyvern.event.WorldJoinEvent
import im.yaHustler.wyvern.event.WorldLeaveEvent
import im.yaHustler.wyvern.module.ModuleManager
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.render.particle.ParticleManager
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines
import im.yaHustler.wyvern.render.world.Renderer3D
import im.yaHustler.wyvern.ui.hud.HudManager
import im.yaHustler.wyvern.ui.theme.ThemePresets
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Ported wyvern UI core: ClickGUI, HUD, Particles and SwingAnimation.
 * Initialized from [im.yaHustler.Hustle26] after the Java modules are registered;
 * Java modules are exposed in the GUI through [im.yaHustler.wyvern.bridge.HustleBridge].
 */
object Wyvern {
    const val MOD_ID = "wyvern"
    const val NAME = "Hustle UI"
    const val VERSION = "0.1.0"

    val logger: Logger = LoggerFactory.getLogger(NAME)

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    fun init(registry: im.yaHustler.module.ModuleRegistry) {
        logger.info("Initializing $NAME $VERSION")

        WyvernPipelines.init()
        ThemePresets.load()
        ModuleManager.init(registry)
        ParticleManager.init()
        HudManager.init()
        ConfigManager.init()

        registerHooks()
    }

    private fun registerHooks() {
        ClientTickEvents.START_CLIENT_TICK.register { EventBus.post(TickEvent.Pre) }
        ClientTickEvents.END_CLIENT_TICK.register { EventBus.post(TickEvent.Post) }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> EventBus.post(WorldJoinEvent()) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> EventBus.post(WorldLeaveEvent()) }

        HudElementRegistry.addLast(id("overlay")) { graphics, deltaTracker ->
            EventBus.post(Render2DEvent(Renderer2D(graphics), deltaTracker.getGameTimeDeltaPartialTick(true)))
        }

        LevelRenderEvents.END_MAIN.register { context ->
            val tickDelta = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)
            Renderer3D.begin(context.levelState().cameraRenderState, tickDelta)
            EventBus.post(Render3DEvent(tickDelta))
            Renderer3D.flush()
            ParticleManager.flush()
        }
    }
}
