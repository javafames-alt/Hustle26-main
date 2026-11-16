package im.yaHustler.wyvern.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import im.yaHustler.wyvern.Wyvern
import im.yaHustler.wyvern.module.ModuleManager
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.setting.Setting
import im.yaHustler.wyvern.util.Keys
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val directory: Path = FabricLoader.getInstance().gameDir.resolve("wyvern")
    private val configs: Path = directory.resolve("configs")

    const val DEFAULT = "default"

    private val dirty = AtomicBoolean(false)
    private val lastChange = AtomicLong(0L)
    private val lastSave = AtomicLong(0L)
    private val saving = AtomicBoolean(false)
    private var enabledSnapshot = ""

    fun init() {
        configs.createDirectories()
        load(DEFAULT)
        for (module in ModuleManager.modules) {
            for (setting in module.settings) {
                @Suppress("UNCHECKED_CAST")
                (setting as Setting<Any?>).onChange { markDirty() }
            }
        }
        markClean()
        enabledSnapshot = snapshotEnabled()
        ClientLifecycleEvents.CLIENT_STOPPING.register { saveQuiet(DEFAULT) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> saveQuiet(DEFAULT) }
        Runtime.getRuntime().addShutdownHook(Thread { saveQuiet(DEFAULT) })
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register {
            val now = System.currentTimeMillis()
            if (snapshotEnabled() != enabledSnapshot) {
                enabledSnapshot = snapshotEnabled()
                markDirty()
            }
            if (!dirty.get()) {
                lastSave.set(now)
                return@register
            }
            if (now - lastChange.get() >= 3000L && now - lastSave.get() >= 10000L) {
                saveQuiet(DEFAULT)
            }
        }
    }

    fun markDirty() {
        dirty.set(true)
        lastChange.set(System.currentTimeMillis())
    }

    private fun snapshotEnabled(): String {
        return ModuleManager.modules.joinToString(";") { it.name + "=" + it.enabled }
    }

    private fun markClean() {
        dirty.set(false)
        lastSave.set(System.currentTimeMillis())
        enabledSnapshot = runCatching { snapshotEnabled() }.getOrDefault(enabledSnapshot)
    }

    fun list(): List<String> =
        if (configs.exists()) configs.listDirectoryEntries("*.json").map { it.nameWithoutExtension } else emptyList()

    private fun saveQuiet(name: String) {
        runCatching { save(name) }.onFailure { Wyvern.logger.error("Failed to save config $name", it) }
    }

    fun save(name: String) {
        if (!saving.compareAndSet(false, true)) return
        try {
            val root = JsonObject()
            for (module in ModuleManager.modules) {
                val json = JsonObject()
                json.addProperty("enabled", module.enabled)
                val settings = JsonObject()
                module.settings.forEach { settings.add(it.name, it.toJson()) }
                json.add("settings", settings)
                val binds = JsonObject()
                module.settings.filterIsInstance<BooleanSetting>().filter { it.bind != Keys.NONE }.forEach { binds.addProperty(it.name, it.bind) }
                if (binds.size() > 0) json.add("binds", binds)
                root.add(module.name, json)
            }
            configs.createDirectories()
            val target = configs.resolve("$name.json")
            val tmp = configs.resolve("$name.json.tmp")
            tmp.writeText(gson.toJson(root))
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            markClean()
        } finally {
            saving.set(false)
        }
    }

    fun load(name: String): Boolean {
        val file = configs.resolve("$name.json")
        if (!file.exists()) return false

        val root = runCatching { JsonParser.parseString(file.readText()).asJsonObject }
            .onFailure {
                Wyvern.logger.error("Failed to parse config $name", it)
                runCatching {
                    val bak = configs.resolve("$name.json.corrupt-${System.currentTimeMillis()}.bak")
                    Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            .getOrNull() ?: return false

        for (module in ModuleManager.modules) {
            val json = runCatching { root.getAsJsonObject(module.name) }.getOrNull() ?: continue
            json.getAsJsonObject("settings")?.let { settings ->
                for (setting in module.settings) {
                    val value = runCatching { settings.get(setting.name) }.getOrNull() ?: continue
                    runCatching { setting.fromJson(value) }
                        .onFailure { Wyvern.logger.warn("Bad value for ${module.name}.${setting.name}: $value") }
                }
            }
            val binds = runCatching { json.getAsJsonObject("binds") }.getOrNull()
            for (setting in module.settings.filterIsInstance<BooleanSetting>()) {
                setting.bind = binds?.get(setting.name)?.let { runCatching { it.asInt }.getOrNull() } ?: Keys.NONE
            }
            runCatching { json.get("enabled") }.getOrNull()?.let {
                runCatching { module.enabled = it.asBoolean }
            }
        }
        markClean()
        return true
    }
}
