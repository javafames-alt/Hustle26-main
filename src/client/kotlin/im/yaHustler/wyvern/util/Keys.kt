package im.yaHustler.wyvern.util

import com.mojang.blaze3d.platform.InputConstants

object Keys {
    const val NONE = 0

    const val MOUSE_BASE = -1000
    private const val MOUSE_BUTTONS = 16

    @JvmStatic
    fun mouse(button: Int) = MOUSE_BASE + button

    fun isMouse(key: Int) = key in MOUSE_BASE until MOUSE_BASE + MOUSE_BUTTONS

    private val byName: Map<String, Int> by lazy {
        InputConstants::class.java.fields
            .filter { it.name.startsWith("KEY_") && it.type == Int::class.javaPrimitiveType }
            .associate { it.name.removePrefix("KEY_") to it.getInt(null) }
    }

    private val aliases = mapOf(
        "NONE" to NONE,
        "UNBOUND" to NONE,
        "ENTER" to InputConstants.KEY_RETURN,
        "ESC" to InputConstants.KEY_ESCAPE,
        "RIGHT_SHIFT" to InputConstants.KEY_RSHIFT,
        "LEFT_SHIFT" to InputConstants.KEY_LSHIFT,
        "RCTRL" to InputConstants.KEY_RCONTROL,
        "LCTRL" to InputConstants.KEY_LCONTROL,
    )

    fun fromName(name: String): Int? {
        val key = name.uppercase().replace('-', '_')
        if (key == "MMB" || key == "WHEEL") return mouse(2)
        key.removePrefix("MOUSE").takeIf { it != key }?.toIntOrNull()?.let { if (it in 1..MOUSE_BUTTONS) return mouse(it - 1) }
        return aliases[key] ?: byName[key]
    }

    fun name(key: Int): String {
        if (key == NONE) return "None"
        if (isMouse(key)) return "MOUSE${key - MOUSE_BASE + 1}"
        return byName.entries.firstOrNull { it.value == key }?.key ?: "Key $key"
    }
}
