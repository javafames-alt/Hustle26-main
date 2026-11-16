package im.yaHustler.wyvern.ui.hud.element

import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.module.ModuleManager
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.BooleanSetting
import im.yaHustler.wyvern.ui.hud.HudElement.Companion.PILL_PAD
import im.yaHustler.wyvern.ui.hud.PillCell
import im.yaHustler.wyvern.ui.hud.PillListHud
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.Keys
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import java.util.Locale
import kotlin.math.sin

private const val TEXT = 7.5f

private fun Renderer2D.width(text: String, size: Float = TEXT) = textWidth(text, size, Theme.bodyFont)

private fun textCell(r: Renderer2D, text: String, color: Color = Theme.text) =
    PillCell(PILL_PAD + r.width(text) + PILL_PAD) { x, _, ty -> r.textCenteredY(text, x + PILL_PAD, ty, color, TEXT, Theme.bodyFont) }

object KeybindsHud : PillListHud<KeybindsHud.Bound>("Клавиши", Icons.DASHBOARD, 0.01, 0.32) {
    class Bound(val name: String, val key: Int)

    override fun items(): List<Bound> = ModuleManager.modules.filter { !it.hidden }.flatMap { module ->
        val own = if (module.enabled && module.keybind.value != Keys.NONE) listOf(Bound(module.name, module.keybind.value)) else emptyList()
        own + module.settings.filterIsInstance<BooleanSetting>()
            .filter { it.value && it.bind != Keys.NONE }
            .map { Bound("${module.name} ${it.name}", it.bind) }
    }

    override fun key(item: Bound) = item.name

    private fun bind(item: Bound) = Keys.name(item.key).uppercase()

    private fun bindWidth(r: Renderer2D, item: Bound) = PILL_PAD + r.width(bind(item)) + PILL_PAD

    override fun cells(r: Renderer2D, item: Bound): List<PillCell> {
        val binds = maxOf(current.maxOfOrNull { bindWidth(r, it) } ?: 0f, bindWidth(r, item))
        return listOf(
            Cell(PILL_PAD + r.width(item.name) + PILL_PAD) { x, _, ty ->
                r.textCenteredY(item.name, x + PILL_PAD, ty, Theme.text, TEXT, Theme.bodyFont)
            },
            Cell(binds) { x, _, ty ->
                val key = bind(item)
                r.textCenteredY(key, x + (binds - r.width(key)) / 2f, ty, Theme.text, TEXT, Theme.bodyFont)
            },
        )
    }
}

object PotionsHud : PillListHud<MobEffectInstance>("Зелья", Icons.SCIENCE, 0.01, 0.56) {

    private val longest = mutableMapOf<String, Int>()

    override fun items(): List<MobEffectInstance> {
        val effects = mc.player?.activeEffects?.filter { it.isVisible }?.sortedByDescending { it.duration } ?: emptyList()
        longest.keys.retainAll(effects.map { it.descriptionId }.toSet())
        return effects
    }

    override fun key(item: MobEffectInstance) = item.descriptionId

    private fun name(effect: MobEffectInstance) = effect.effect.value().displayName.string

    private fun level(effect: MobEffectInstance) = roman(effect.amplifier + 1)

    override fun cells(r: Renderer2D, item: MobEffectInstance): List<PillCell> {
        val name = name(item)
        val level = level(item)
        val time = duration(item)
        val left = if (item.isInfiniteDuration) 1f else {
            val max = longest.merge(item.descriptionId, item.duration, ::maxOf)!!
            item.duration.toFloat() / max.coerceAtLeast(1)
        }
        val ending = !item.isInfiniteDuration && item.duration < 200
        val pulse = if (ending) (0.5f + 0.5f * sin(System.currentTimeMillis() / 160.0).toFloat()) else 0f
        return listOf(
            Cell(PILL_PAD + r.width(name) + 3.5f + r.width(level) + 2f + r.width("lvl", 6f) + PILL_PAD) { x, _, ty ->
                var tx = x + PILL_PAD
                r.textCenteredY(name, tx, ty, Theme.text, TEXT, Theme.bodyFont)
                tx += r.width(name) + 3.5f
                r.textCenteredY(level, tx, ty, Theme.text, TEXT, Theme.bodyFont)
                tx += r.width(level) + 2f
                r.text("lvl", tx, ty + r.textHeight(TEXT, Theme.bodyFont) / 2f - r.textHeight(6f, Theme.bodyFont), Theme.textDim, 6f, Theme.bodyFont)
            },
            Cell(PILL_PAD + 7.5f + 4f + r.width(time) + PILL_PAD) { x, cy, ty ->
                val ringX = x + PILL_PAD + 3.75f
                r.ring(ringX, cy, 3.75f, 1.5f, 1f, Theme.accent.withAlpha(45))
                r.ring(ringX, cy, 3.75f, 1.5f, left, Theme.accent.mix(Theme.text, pulse * 0.6f))
                r.textCenteredY(time, ringX + 3.75f + 4f, ty, Theme.text.mix(Theme.accent, pulse), TEXT, Theme.bodyFont)
            },
        )
    }

    private fun duration(effect: MobEffectInstance): String {
        if (effect.isInfiniteDuration) return "∞"
        val seconds = effect.duration / 20
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun roman(n: Int) = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n - 1) { n.toString() }
}

object CooldownsHud : PillListHud<CooldownsHud.Active>("Кулдауны", Icons.TIMER, 0.99, 0.56) {
    class Active(val group: Identifier, val stack: ItemStack, val percent: Float, val seconds: Float)

    private class Tracked(val stack: ItemStack, val durationTicks: Int)

    private val tracked = linkedMapOf<Identifier, Tracked>()

    @JvmStatic
    fun onCooldown(group: Identifier, ticks: Int) {
        val item = BuiltInRegistries.ITEM.getValue(group)
        tracked[group] = Tracked(ItemStack(item), ticks)
    }

    override fun items(): List<Active> {
        val cooldowns = mc.player?.cooldowns ?: return emptyList()
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        tracked.entries.removeIf { cooldowns.getCooldownPercent(it.value.stack, delta) <= 0f }
        return tracked.map { (group, entry) ->
            val percent = cooldowns.getCooldownPercent(entry.stack, delta)
            Active(group, entry.stack, percent, percent * entry.durationTicks / 20f)
        }
    }

    override fun key(item: Active) = item.group.toString()

    override fun cells(r: Renderer2D, item: Active): List<PillCell> {
        val name = item.stack.hoverName.string
        val time = "%.1fs".format(Locale.ROOT, item.seconds)
        return listOf(
            Cell(PILL_PAD + 9f + 4f + r.width(name) + PILL_PAD) { x, cy, ty ->
                r.withPose {
                    r.translate(x + PILL_PAD, cy - 4.5f)
                    r.scale(9f / 16f)
                    r.graphics.item(item.stack, 0, 0)
                }
                r.textCenteredY(name, x + PILL_PAD + 13f, ty, Theme.text, TEXT, Theme.bodyFont)
            },
            Cell(PILL_PAD + 7.5f + 4f + r.width("00.0s") + PILL_PAD) { x, cy, ty ->
                val ringX = x + PILL_PAD + 3.75f
                r.ring(ringX, cy, 3.75f, 1.5f, 1f, Theme.accent.withAlpha(45))
                r.ring(ringX, cy, 3.75f, 1.5f, item.percent, Theme.accent)
                r.textCenteredY(time, ringX + 3.75f + 4f, ty, Theme.text, TEXT, Theme.bodyFont)
            },
        )
    }
}

object StaffListHud : PillListHud<StaffListHud.Staff>("Стафф", Icons.SHIELD, 0.99, 0.32) {
    class Staff(val name: String, val rank: String, val spectator: Boolean)

    private val ranks = mapOf(
        "owner" to "Owner",
        "admin" to "Admin",
        "mod" to "Mod",
        "moderator" to "Moderator",
        "helper" to "Helper",
        "staff" to "Staff",
        "dev" to "Dev",
        "developer" to "Developer",
        "builder" to "Builder",
        "curator" to "Curator",
        "support" to "Support",
        "manager" to "Manager",
        "админ" to "Админ",
        "администратор" to "Администратор",
        "модер" to "Модер",
        "модератор" to "Модератор",
        "хелпер" to "Хелпер",
        "куратор" to "Куратор",
        "владелец" to "Владелец",
        "стафф" to "Стафф",
        "саппорт" to "Саппорт",
        "поддержка" to "Поддержка",
        "менеджер" to "Менеджер",
        "билдер" to "Билдер",
        "разработчик" to "Разработчик",
        "гл. модер" to "Гл. Модер",
        "гл. модератор" to "Гл. Модератор",
        "гл.модер" to "Гл. Модер",
        "гл.модератор" to "Гл. Модератор",
        "гл модер" to "Гл. Модер",
        "гл модератор" to "Гл. Модератор",
        "главный модер" to "Гл. Модер",
        "главный модератор" to "Гл. Модератор",
        "мл. модер" to "Мл. Модер",
        "мл. модератор" to "Мл. Модератор",
        "мл.модер" to "Мл. Модер",
        "мл.модератор" to "Мл. Модератор",
        "мл модер" to "Мл. Модер",
        "мл модератор" to "Мл. Модератор",
        "младший модер" to "Мл. Модер",
        "младший модератор" to "Мл. Модератор",
        "ст. модер" to "Ст. Модер",
        "ст. модератор" to "Ст. Модератор",
        "ст.модер" to "Ст. Модер",
        "ст.модератор" to "Ст. Модератор",
        "ст модер" to "Ст. Модер",
        "ст модератор" to "Ст. Модератор",
        "старший модер" to "Ст. Модер",
        "старший модератор" to "Ст. Модератор",
    )

    private val formatting = Regex("§.")
    private val spaces = Regex("\\s+")
    private val rankSuffix = Regex("\\s*[|•·—–▪●»-]+\\s*$")

    private val spectatorColor = Color(255, 196, 64)

    override fun items(): List<Staff> {
        val connection = mc.connection ?: return emptyList()
        return connection.onlinePlayers.mapNotNull(::classify).sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun classify(info: PlayerInfo): Staff? {
        val name = info.profile.name
        val prefix = info.team?.playerPrefix?.string.orEmpty()
        val display = info.tabListDisplayName?.string.orEmpty()
        val rank = rankOf(prefix) ?: rankFromDisplay(display, name)
        val spectator = info.gameMode == GameType.SPECTATOR
        if (rank == null && !spectator) return null
        return Staff(name, rank.orEmpty(), spectator)
    }

    private fun rankFromDisplay(raw: String, player: String): String? {
        val display = clean(raw)
        if (display.isEmpty()) return null
        val name = player.lowercase(Locale.ROOT)
        val prefix = when {
            display == name -> return null
            display.endsWith(" $name") -> display.removeSuffix(" $name")
            display.endsWith("|$name") -> display.removeSuffix("|$name")
            else -> display
        }
        return rankOf(prefix, trimSuffix = true)
    }

    private fun rankOf(raw: String, trimSuffix: Boolean = false): String? {
        var rank = clean(raw)
        if (trimSuffix) rank = rank.replace(rankSuffix, "")
        rank = rank.trim { it in "[](){}<>:,.! " }
        return ranks[rank]
    }

    private fun clean(raw: String) = raw
        .replace(formatting, "")
        .lowercase(Locale.ROOT)
        .replace(spaces, " ")
        .trim()

    override fun key(item: Staff) = item.name

    override fun cells(r: Renderer2D, item: Staff): List<PillCell> {
        val rankWidth = if (item.rank.isBlank()) 0f else r.textWidth(item.rank, TEXT, Theme.titleFont) + 3.5f
        val cells = mutableListOf(
            Cell(PILL_PAD + 3.6f + 4f + rankWidth + r.width(item.name) + PILL_PAD) { x, cy, ty ->
                r.circle(x + PILL_PAD + 1.8f, cy, 1.8f, if (item.spectator) spectatorColor else Color(80, 210, 120))
                var tx = x + PILL_PAD + 7.6f
                if (item.rank.isNotBlank()) {
                    r.textCenteredY(item.rank, tx, ty, Theme.accent, TEXT, Theme.titleFont)
                    tx += rankWidth
                }
                r.textCenteredY(item.name, tx, ty, Theme.text, TEXT, Theme.bodyFont)
            },
        )
        if (item.spectator) cells += textCell(r, "SPEC", spectatorColor)
        return cells
    }
}
