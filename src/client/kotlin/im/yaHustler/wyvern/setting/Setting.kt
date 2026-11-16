package im.yaHustler.wyvern.setting

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.util.Keys
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(
    val name: String,
    val default: T,
    val description: String,
    private val visibility: () -> Boolean,
) : ReadWriteProperty<Any?, T> {

    private var hidden = false
    private var locked = false

    open var value: T = default
        set(new) {
            if (locked) return
            val old = field
            field = new
            if (old != new) changeListeners.forEach { it(new) }
        }

    private val changeListeners = mutableListOf<(T) -> Unit>()

    val isVisible get() = !hidden && visibility()

    fun hide() {
        hidden = true
    }

    fun lock(fixed: T) {
        value = fixed
        locked = true
    }

    fun onChange(block: (T) -> Unit) = apply { changeListeners += block }

    fun reset() {
        value = default
    }

    abstract fun toJson(): JsonElement
    abstract fun fromJson(json: JsonElement)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

open class BooleanSetting(name: String, default: Boolean, description: String, visible: () -> Boolean) :
    Setting<Boolean>(name, default, description, visible) {
    var bind = Keys.NONE

    fun toggle() {
        value = !value
    }

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asBoolean
    }
}

open class NumberSetting(
    name: String,
    default: Double,
    val range: ClosedFloatingPointRange<Double>,
    val step: Double,
    description: String,
    visible: () -> Boolean,
) : Setting<Double>(name, default, description, visible) {

    override var value: Double
        get() = super.value
        set(new) {
            val stepped = if (step > 0) Math.round(new / step) * step else new
            super.value = stepped.coerceIn(range)
        }

    val min get() = range.start
    val max get() = range.endInclusive

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asDouble
    }
}

open class ModeSetting(
    name: String,
    default: String,
    val modes: List<String>,
    description: String,
    visible: () -> Boolean,
) : Setting<String>(name, default, description, visible) {

    init {
        require(default in modes) { "Default mode '$default' is not in $modes" }
    }

    fun `is`(mode: String) = value.equals(mode, ignoreCase = true)

    fun cycle(forward: Boolean = true) {
        val index = modes.indexOf(value)
        val next = if (forward) index + 1 else index - 1 + modes.size
        value = modes[next % modes.size]
    }

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        val raw = json.asString
        value = modes.firstOrNull { it.equals(raw, ignoreCase = true) } ?: default
    }
}

class ButtonSetting(name: String, description: String, visible: () -> Boolean, private val action: () -> Unit) :
    Setting<Unit>(name, Unit, description, visible) {

    fun press() = action()

    override fun toJson(): JsonElement = JsonNull.INSTANCE
    override fun fromJson(json: JsonElement) {}
}

open class TextSetting(name: String, default: String, description: String, visible: () -> Boolean) :
    Setting<String>(name, default, description, visible) {

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asString
    }
}

class MultiSetting(
    name: String,
    val options: List<String>,
    default: Set<String>,
    description: String,
    visible: () -> Boolean,
) : Setting<Set<String>>(name, default, description, visible) {

    operator fun contains(option: String) = value.any { it.equals(option, ignoreCase = true) }

    fun toggle(option: String) {
        value = if (option in this) value.filterNot { it.equals(option, ignoreCase = true) }.toSet() else value + option
    }

    override fun toJson() = com.google.gson.JsonArray().apply { value.forEach(::add) }
    override fun fromJson(json: JsonElement) {
        value = json.asJsonArray.mapNotNull { e -> options.firstOrNull { it.equals(e.asString, ignoreCase = true) } }.toSet()
    }
}

open class ColorSetting(name: String, default: Color, description: String, visible: () -> Boolean) :
    Setting<Color>(name, default, description, visible) {

    override fun toJson() = JsonPrimitive(value.argb)
    override fun fromJson(json: JsonElement) {
        value = Color(json.asInt)
    }
}

open class KeybindSetting(name: String, default: Int, description: String, visible: () -> Boolean) :
    Setting<Int>(name, default, description, visible) {

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asInt
    }
}
