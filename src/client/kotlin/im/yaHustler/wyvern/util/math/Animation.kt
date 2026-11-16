package im.yaHustler.wyvern.util.math

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

fun interface Easing {
    fun ease(t: Float): Float

    companion object {
        val LINEAR = Easing { it }
        val OUT_CUBIC = Easing { 1f - (1f - it).pow(3) }
        val OUT_QUINT = Easing { 1f - (1f - it).pow(5) }
        val IN_OUT_CUBIC = Easing { if (it < 0.5f) 4f * it * it * it else 1f - (-2f * it + 2f).pow(3) / 2f }
        val OUT_BACK = Easing {
            val c1 = 1.20158f
            val c3 = c1 + 1f
            1f + c3 * (it - 1f).pow(3) + c1 * (it - 1f).pow(2)
        }
    }
}

class Animation(private val durationMs: Long, private val easing: Easing = Easing.OUT_CUBIC) {
    private var progress = 0f
    private var lastUpdate = System.nanoTime()

    fun update(target: Boolean): Float {
        val now = System.nanoTime()
        val delta = ((now - lastUpdate) / 1_000_000f).coerceAtMost(60f) / durationMs
        lastUpdate = now
        progress = if (target) (progress + delta).coerceAtMost(1f) else (progress - delta).coerceAtLeast(0f)
        return easing.ease(progress)
    }

    val value get() = progress

    fun snap(target: Boolean) {
        progress = if (target) 1f else 0f
        lastUpdate = System.nanoTime()
    }
}

class Smooth(initial: Float = 0f, private val speed: Float = 18f) {
    var value = initial
        private set
    private var lastUpdate = System.nanoTime()

    fun update(target: Float): Float {
        val now = System.nanoTime()
        val dt = ((now - lastUpdate) / 1_000_000_000f).coerceAtMost(0.1f)
        lastUpdate = now
        value += (target - value) * (1f - exp(-speed * dt))
        if (abs(target - value) < 0.005f) value = target
        return value
    }

    fun snap(target: Float) {
        value = target
        lastUpdate = System.nanoTime()
    }
}
