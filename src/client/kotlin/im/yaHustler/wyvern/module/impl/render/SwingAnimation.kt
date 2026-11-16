package im.yaHustler.wyvern.module.impl.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object SwingAnimation : Module("SwingAnimation", "Свои анимации взмаха от первого лица", Category.RENDER) {
    private val mode = mode(
        "Режим", "Мод 1", "Мод 1", "Мод 2", "Мод 3", "Мод 4", "Мод 5", "Мод 6", "Мод 7", "Мод 8", "Мод 9", "Мод 10",
        description = "Вариант анимации взмаха",
    )
    private val angle by number("Угол", 100.0, 0.0..360.0, 1.0, "Угол поворота руки") { mode.`is`("Мод 2") || mode.`is`("Мод 4") }
    private val strength by number("Сила", 8.0, 1.0..10.0, 1.0, "Амплитуда взмаха") {
        mode.`is`("Мод 1") || mode.`is`("Мод 2") || mode.`is`("Мод 3") || mode.`is`("Мод 4") || mode.`is`("Мод 6") ||
            mode.`is`("Мод 10")
    }
    private val speed by number("Скорость", 11.0, 1.0..20.0, 1.0, "Скорость анимации")
    private val smoothness by number("Плавность", 0.5, 0.0..1.0, 0.05, "Сглаживает движение руки: выше — мягче")
    private val onlyWithAura by boolean("Только с аурой", false, "Играть анимацию, только пока работает аура")

    override val suffix get() = mode.value

    @JvmStatic
    fun holdsHeight() = enabled && isActive()

    @JvmStatic
    fun duration(vanilla: Int): Int {
        if (!enabled || !isActive()) return vanilla
        return speed.toInt().coerceAtLeast(1)
    }

    @JvmStatic
    fun apply(pose: PoseStack, t: Float, invert: Int): Boolean {
        if (!enabled || !isActive()) return false
        val player = mc.player ?: return false
        val mainSide = if (player.mainArm == HumanoidArm.LEFT) -1 else 1
        if (invert != mainSide) return false
        if (player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND) return false
        applyTransform(pose, smooth(t.coerceIn(0f, 1f)))
        return true
    }

    private var shownProgress = 1f
    private var lastFrame = 0L

    // Eases the swing curve (sine in-out, softer at the ends than cubic), then lets the arm chase it over a few frames,
    // so even short swings glide. Vanilla's swing ends before the chasing arm arrives: [tail] carries it the rest of
    // the way to the end of the curve, which is the rest pose, instead of snapping there.
    private fun smooth(progress: Float): Float {
        val k = smoothness.toFloat()
        val eased = progress + (sineInOut(progress) - progress) * k
        val dt = frameTime()
        if (k <= 0f || eased < shownProgress - 0.5f || dt >= 0.1f) {
            shownProgress = eased
            return eased
        }
        shownProgress += (eased - shownProgress) * (1f - exp(-dt * rate(k)))
        return shownProgress
    }

    private fun tail(): Float? {
        val k = smoothness.toFloat()
        if (k <= 0f || shownProgress >= 0.999f) return null
        val dt = frameTime()
        shownProgress = if (dt >= 0.1f) 1f else shownProgress + (1f - shownProgress) * (1f - exp(-dt * rate(k)))
        if (shownProgress > 0.995f) shownProgress = 1f
        return shownProgress
    }

    private fun frameTime(): Float {
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1.0E9f).coerceIn(0f, 0.1f)
        lastFrame = now
        return dt
    }

    private fun rate(k: Float) = 60f - 50f * k

    private fun sineInOut(t: Float) = (0.5 - 0.5 * cos(t * PI)).toFloat()

    @JvmStatic
    fun applyRest(pose: PoseStack, arm: HumanoidArm): Boolean {
        if (!enabled || !isActive()) return false
        val player = mc.player ?: return false
        if (player.mainArm != arm) return false
        if (player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND) return false
        val swing = player.currentSwing
        if (swing != null && swing.hand() == InteractionHand.MAIN_HAND) return false
        applyTransform(pose, tail() ?: 0f)
        return true
    }

    private fun applyTransform(pose: PoseStack, p: Float) {
        val left = mc.player?.mainArm == HumanoidArm.LEFT
        val s = if (left) -1f else 1f
        val f = sin(p * PI).toFloat()
        val st = strength.toFloat()
        val an = angle.toFloat()
        when {
            mode.`is`("Мод 1") -> {
                pose.rotateDegrees(Axis.YP, s * (45f + f / 4f * -20f))
                pose.rotateDegrees(Axis.ZP, s * f * -20f)
                pose.rotateDegrees(Axis.XP, f * -st * 10f)
                pose.rotateDegrees(Axis.YP, -s * 45f)
            }
            mode.`is`("Мод 2") -> {
                pose.translate(0f, 0.1f, -0.35f)
                pose.rotateDegrees(Axis.YP, 90f)
                pose.rotateDegrees(Axis.ZP, -60f)
                pose.rotateDegrees(Axis.XP, -an - st * 10f * f)
            }
            mode.`is`("Мод 3") -> {
                pose.translate(0f, 0.05f, -0.25f)
                pose.translate(0f, 0f, f * st / 50f)
                pose.rotateDegrees(Axis.XN, -20f)
                pose.rotateDegrees(Axis.XP, -120f)
                pose.rotateDegrees(Axis.YP, -f * 1f + 25f)
                pose.rotateDegrees(Axis.ZP, -f * 1f + 10f)
                pose.rotateDegrees(Axis.XP, -f * 1f - 20f)
                pose.rotateDegrees(Axis.ZP, 50f)
                pose.rotateDegrees(Axis.ZN, -15f)
                pose.rotateDegrees(Axis.YN, -25f)
            }
            mode.`is`("Мод 4") -> {
                val f3 = sin(sqrt(p) * PI).toFloat()
                pose.translate(0f, 0.15f, -0.3f)
                pose.rotateDegrees(Axis.YP, if (left) -70f else 70f)
                pose.rotateDegrees(Axis.ZP, if (left) 30f else -30f)
                pose.rotateDegrees(Axis.XP, -an - st * 10f * f3)
            }
            mode.`is`("Мод 5") -> {
                pose.translate(0f, 0f - f * st / 100f, 0f)
            }
            mode.`is`("Мод 6") -> {
                val f5 = sin(p * p * PI).toFloat()
                val f6 = sin(sqrt(p) * PI).toFloat()
                pose.rotateDegrees(Axis.YP, s * (45f + f5 * -20f))
                pose.rotateDegrees(Axis.ZP, s * f6 * -20f)
                pose.rotateDegrees(Axis.XP, f6 * -st * 10f)
                pose.rotateDegrees(Axis.YP, s * -45f)
            }
            mode.`is`("Мод 7") -> {
                val f7 = sin(sqrt(p) * PI).toFloat()
                val f8 = sin(p * PI).toFloat()
                pose.translate(s * (0.4f - f7 * 0.2f), -0.2f + f7 * 0.3f, -0.5f - f8 * 0.2f)
                pose.rotateDegrees(Axis.YP, if (left) -91f else 91f)
                pose.rotateDegrees(Axis.ZP, s * (-40f + f7 * -100f))
                pose.rotateDegrees(Axis.XP, -60f)
            }
            mode.`is`("Мод 8") -> {
                pose.rotateDegrees(Axis.YP, s * -90f)
                pose.rotateDegrees(Axis.ZP, s * 60f)
                pose.rotateDegrees(Axis.XP, 30f)
                pose.rotateDegrees(Axis.ZP, s * 25f * f)
            }
            mode.`is`("Мод 10") -> {
                val f6 = sin(sqrt(p) * PI).toFloat()
                pose.translate(0f, f6 * -0.52f * (st / 8f) + 0.1f, 0f)
                pose.rotateDegrees(Axis.YP, s * -20f)
            }
            mode.`is`("Мод 9") -> {
                val f12 = sin(sqrt(p) * PI).toFloat()
                pose.rotateDegrees(Axis.YP, s * 45f)
                pose.rotateDegrees(Axis.XP, f12 * -20f)
                pose.rotateDegrees(Axis.ZP, s * f12 * -20f)
                pose.rotateDegrees(Axis.XP, f12 * -80f)
                pose.translate(s * 0.4f, 0.2f, 0.2f)
                pose.translate(s * -0.5f, 0.08f, 0f)
                pose.rotateDegrees(Axis.YP, s * 20f)
                pose.rotateDegrees(Axis.XP, -80f)
                pose.rotateDegrees(Axis.YP, s * 20f)
            }
        }
    }

    private fun isActive(): Boolean {
        if (!onlyWithAura) return true
        // Only-with-aura follows the Java client's AttackAura target instead of the original wyvern module.
        val aura = im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.combat.aura.AttackAura::class.java) ?: return false
        return aura.currentTarget != null
    }
}
