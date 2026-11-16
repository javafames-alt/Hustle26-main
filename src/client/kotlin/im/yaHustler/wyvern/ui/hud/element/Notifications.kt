package im.yaHustler.wyvern.ui.hud.element

import im.yaHustler.wyvern.event.Listenable
import im.yaHustler.wyvern.event.Listener
import im.yaHustler.wyvern.event.TickEvent
import im.yaHustler.wyvern.event.WorldLeaveEvent
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.ui.hud.HudElement
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import kotlin.math.ceil

object Notifications : HudElement("Уведомления", Icons.NOTIFICATIONS, 0.5, 0.2), Listenable {
    override val listeners = mutableListOf<Listener<*>>()
    override val pills = true
    override val centered = true
    override val titled = false

    enum class Kind(val color: Color) {
        SUCCESS(Color(52, 199, 89)),
        ERROR(Color(255, 69, 58)),
        WARNING(Color(255, 196, 0)),
    }

    private class Note(var kind: Kind, var text: String, val key: String) {
        var born = System.currentTimeMillis()
        val anim = Animation(340, Easing.OUT_CUBIC)
        var t = 0f

        val age get() = System.currentTimeMillis() - born
        val alive get() = age < DURATION_MS
    }

    private val notes = ArrayList<Note>()

    private var lowHealthSaid = false

    private val endingSaid = HashSet<String>()

    private var lastEffects = emptyMap<String, Pair<String, Int>>()

    init {
        on<TickEvent.Post> { watch() }
        on<WorldLeaveEvent> {
            notes.clear()
            endingSaid.clear()
            lastEffects = emptyMap()
            lowHealthSaid = false
        }
    }

    fun push(kind: Kind, text: String, key: String = text) {
        notes.add(0, Note(kind, text, key))
        notes.filter { it.alive }.drop(MAX_SHOWN).forEach { it.born = 0L }
    }

    fun module(module: Module, enabled: Boolean) {
        if (mc.player == null || module.hidden || module.category == Category.CLIENT) return
        push(if (enabled) Kind.SUCCESS else Kind.ERROR, "${module.name} ${if (enabled) "enabled" else "disabled"}", module.name)
    }

    fun setting(module: Module, setting: BooleanSetting) {
        val name = "${module.name} ${setting.name}"
        push(if (setting.value) Kind.SUCCESS else Kind.ERROR, "$name ${if (setting.value) "on" else "off"}", name)
    }

    private fun watch() {
        val player = mc.player ?: return
        val hp = player.health + player.absorptionAmount
        if (player.isAlive && hp <= LOW_HEALTH && !lowHealthSaid) {
            lowHealthSaid = true
            push(Kind.WARNING, "Low health: ${ceil(hp).toInt()} HP", "low-health")
        } else if (hp > LOW_HEALTH + 4f) {
            lowHealthSaid = false
        }

        val effects = player.activeEffects
            .filter { it.effect.value().category == MobEffectCategory.BENEFICIAL && !it.isInfiniteDuration }
            .associate { "${it.descriptionId}#${it.amplifier}" to (effectName(it) to it.duration) }
        for ((id, effect) in effects) {
            val (name, duration) = effect
            if (duration in 1..ENDING_TICKS && id !in endingSaid) {
                endingSaid += id
                push(Kind.WARNING, "$name ends in ${ceil(duration / 20.0).toInt()}s", id)
            } else if (duration > ENDING_TICKS + 20) {
                endingSaid -= id
            }
        }
        for ((id, effect) in lastEffects) {
            if (id !in effects && effect.second <= 40) push(Kind.ERROR, "${effect.first} ended", id)
        }
        endingSaid.retainAll(effects.keys)
        lastEffects = effects
    }

    private fun effectName(effect: MobEffectInstance): String {
        val level = effect.amplifier + 1
        val name = effect.effect.value().displayName.string
        return if (level > 1) "$name ${roman(level)}" else name
    }

    private fun roman(n: Int) = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n - 1) { n.toString() }

    override fun hasContent(): Boolean {
        notes.removeIf { !it.alive && it.anim.value <= 0f }
        return notes.isNotEmpty()
    }

    private fun pillWidth(r: Renderer2D, note: Note) = PAD + BADGE * 2f + 5f + r.textWidth(note.text, TEXT, Theme.bodyFont) + PAD + 2f

    override fun measure(r: Renderer2D, editing: Boolean): Pair<Float, Float> {
        for (note in notes) note.t = note.anim.update(note.alive)
        if (notes.isEmpty()) return r.textWidth(SAMPLE, TEXT, Theme.bodyFont) + PAD * 2f + BADGE * 2f + 8f to H
        val width = notes.maxOf { pillWidth(r, it) }
        val height = notes.sumOf { ((H + GAP) * it.t).toDouble() }.toFloat() - GAP
        return width to height.coerceAtLeast(H)
    }

    override fun draw(r: Renderer2D, x: Float, y: Float, w: Float, h: Float, editing: Boolean) {
        if (notes.isEmpty()) {
            drawPill(r, x + w / 2f, y, w, Kind.SUCCESS, SAMPLE, 1f, 1f)
            return
        }
        var ny = y
        for (note in notes) {
            val t = note.t
            if (t <= 0.001f) continue
            val left = (1f - note.age.toFloat() / DURATION_MS).coerceIn(0f, 1f)
            drawPill(r, x + w / 2f, ny, pillWidth(r, note), note.kind, note.text, t, left)
            ny += (H + GAP) * t
        }
    }

    private fun drawPill(r: Renderer2D, cx: Float, y: Float, w: Float, kind: Kind, text: String, t: Float, left: Float) {
        val x = cx - w / 2f
        val cy = y + H / 2f
        r.withAlpha(t) {
            r.withPose {
                r.translate(0f, -8f * (1f - t))
                r.scaleAround(cx, cy, 0.86f + 0.14f * Easing.OUT_BACK.ease(t))
                pill(r, x, y, w, H, PILL_RADIUS)

                val bx = x + PAD + BADGE
                val by = cy - 0.5f
                r.circle(bx, by, BADGE, kind.color)
                when (kind) {
                    Kind.SUCCESS -> r.icon(Icons.CHECK, bx, by, 6.5f, SIGN)
                    Kind.ERROR -> r.icon(Icons.CLOSE, bx, by, 6.5f, SIGN)
                    Kind.WARNING -> r.textCenteredY("i", bx - r.textWidth("i", 7f, Theme.titleFont) / 2f, by - 0.5f, SIGN, 7f, Theme.titleFont)
                }
                r.textCenteredY(text, bx + BADGE + 4.5f, by, Theme.text, TEXT, Theme.bodyFont)

                val barX = x + PAD
                val barW = w - PAD * 2f
                val barY = y + H - 3.2f
                r.roundedRect(barX, barY, barW, 1.6f, 0.8f, kind.color.withAlpha(55))
                if (left > 0f) r.roundedRect(barX, barY, barW * left, 1.6f, 0.8f, kind.color)
            }
        }
    }

    private const val DURATION_MS = 2600L
    private const val MAX_SHOWN = 5
    private const val LOW_HEALTH = 8f
    private const val ENDING_TICKS = 100
    private const val H = 18f
    private const val GAP = 3f
    private const val PAD = 6f
    private const val BADGE = 4f
    private const val TEXT = 7.5f
    private const val SAMPLE = "AttackAura enabled"
    private val SIGN = Color(12, 12, 14)
}
