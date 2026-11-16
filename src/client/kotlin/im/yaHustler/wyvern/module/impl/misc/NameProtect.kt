package im.yaHustler.wyvern.module.impl.misc

/**
 * Stub of the original wyvern NameProtect module (only its text hook is used by the font renderer).
 * The Hustle client has its own NameProtect module, so the passthrough keeps text untouched.
 */
object NameProtect {
    @JvmStatic
    fun protect(text: String): String = text
}
