package im.yaHustler.wyvern.ui.theme

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import im.yaHustler.wyvern.Wyvern
import im.yaHustler.wyvern.config.ConfigManager
import im.yaHustler.wyvern.render.color.Color
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ThemePresets {
    private val file get() = ConfigManager.directory.resolve("themes.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val presets = mutableListOf<Palette>()

    fun load() {
        presets.clear()
        val path = file
        if (!path.exists()) return
        runCatching {
            for (element in JsonParser.parseString(path.readText()).asJsonArray) {
                val o = element.asJsonObject
                presets += Theme.fromColors(
                    o["name"].asString,
                    Color(o["background"].asInt), Color(o["card"].asInt), Color(o["accent"].asInt), Color(o["text"].asInt),
                )
            }
        }.onFailure { Wyvern.logger.error("Failed to read theme presets", it) }
    }

    private fun save() {
        val array = JsonArray()
        for (p in presets) {
            array.add(JsonObject().apply {
                addProperty("name", p.name)
                addProperty("background", p.background.argb)
                addProperty("card", p.card.argb)
                addProperty("accent", p.accent.argb)
                addProperty("text", p.text.argb)
            })
        }
        ConfigManager.directory.createDirectories()
        file.writeText(gson.toJson(array))
    }

    fun add(palette: Palette): Palette {
        var name = palette.name.takeIf { !Theme.isBuiltIn(it) && presets.none { p -> p.name == it } && it != "Imported" }
        var n = 1
        while (name == null) {
            val candidate = "Preset $n"
            if (presets.none { it.name == candidate }) name = candidate
            n++
        }
        val stored = palette.copy(name = name)
        presets += stored
        save()
        return stored
    }

    fun remove(name: String) {
        if (presets.removeIf { it.name == name }) save()
    }
}
