package im.yaHustler.wyvern.ui.hud.element

import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.ui.hud.HudElement
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

object TargetHud : HudElement("Цель", Icons.FAVORITE, 0.42, 0.62) {
    override val pills = true

    private const val H = 30f
    private const val PAD = 6f
    private const val HEAD = 19f
    private const val RING = 8.5f
    private const val NAME_SIZE = 8.5f
    private val GEAR = listOf(
        EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND,
    )

    override val titled = false

    private var last: LivingEntity? = null
    private val health = Smooth(-1f, speed = 20f)
    private val trail = Smooth(-1f, speed = 3f)

    private fun target(): LivingEntity? {
        // Follow the Java client's AttackAura target; wyvern's own aura modules are not ported.
        val aura = im.yaHustler.Hustle26.MODULES.get(im.yaHustler.module.impl.combat.aura.AttackAura::class.java)
        aura?.getCurrentTarget()?.let { if (aura.enabled() && it.isAlive) return it }
        val player = mc.player ?: return null
        val hurt = player.lastHurtMob ?: return null
        return hurt.takeIf { it.isAlive && player.tickCount - player.lastHurtMobTimestamp < 60 }
    }

    private fun shown(editing: Boolean) = target() ?: (if (editing) mc.player else null) ?: last

    override fun hasContent() = target() != null

    override fun measure(r: Renderer2D, editing: Boolean): Pair<Float, Float> {
        val entity = shown(editing)
        val name = entity?.name?.string ?: ""
        val armor = entity?.let { e -> GEAR.count { !e.getItemBySlot(it).isEmpty } } ?: 0
        val column = maxOf(r.textWidth(name, NAME_SIZE, Theme.bodyFont), armor * 8.5f, 30f)
        return PAD + HEAD + 7f + column + 9f + RING * 2 + PAD to H
    }

    override fun draw(r: Renderer2D, x: Float, y: Float, w: Float, h: Float, editing: Boolean) {
        val entity = shown(editing) ?: return
        if (entity !== last) {
            last = entity
            health.snap(fraction(entity))
            trail.snap(fraction(entity))
        }
        pill(r, x, y, w, h, 8f)
        val cy = y + h / 2f

        val hx = x + PAD
        val hy = cy - HEAD / 2f
        if (entity is AbstractClientPlayer) {
            r.playerHead(entity.skin, hx, hy, HEAD)
        } else {
            r.roundedRect(hx, hy, HEAD, HEAD, 4f, Theme.control)
            r.icon(Icons.PERSON, hx + HEAD / 2f, hy + HEAD / 2f, 12f, Theme.accent)
        }
        val tx = hx + HEAD + 7f
        val ringX = x + w - PAD - RING
        val armored = GEAR.any { !entity.getItemBySlot(it).isEmpty }
        r.scissor(tx, y, ringX - RING - 6f - tx, h) {
            r.textCenteredY(entity.name.string, tx, if (armored) cy - 4.5f else cy - TEXT_LIFT, Theme.text, NAME_SIZE, Theme.bodyFont)
        }
        var ix = tx
        for (slot in GEAR) {
            val stack = entity.getItemBySlot(slot)
            if (stack.isEmpty) continue
            item(r, stack, ix, cy + 1.5f, 7f)
            ix += 8.5f
        }

        val frac = health.update(fraction(entity))
        val lag = trail.update(fraction(entity)).coerceAtLeast(frac)
        r.ring(ringX, cy, RING, 1.8f, 1f, Theme.accent.withAlpha(40))
        r.ring(ringX, cy, RING, 1.8f, lag, Theme.text.withAlpha(90))
        r.ring(ringX, cy, RING, 1.8f, frac, Theme.accent)
        val hp = ceil(entity.health + entity.absorptionAmount).toInt().toString()
        r.textCenteredY(hp, ringX - r.textWidth(hp, 7f, Theme.titleFont) / 2f, cy - TEXT_LIFT + 0.5f, Theme.text, 7f, Theme.titleFont)
    }

    private fun item(r: Renderer2D, stack: ItemStack, x: Float, y: Float, size: Float) {
        if (stack.isEmpty) return
        r.withPose {
            r.translate(x, y)
            r.scale(size / 16f)
            r.graphics.item(stack, 0, 0)
        }
    }

    private fun fraction(entity: LivingEntity) = (entity.health / entity.maxHealth).coerceIn(0f, 1f)
}
