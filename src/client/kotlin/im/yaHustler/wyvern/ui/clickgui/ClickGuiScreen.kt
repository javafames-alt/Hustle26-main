package im.yaHustler.wyvern.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.module.ModuleManager
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.clickgui.component.ModuleCard
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs

sealed class Tab(val name: String, val icon: Int) {
    class Modules(val category: Category) : Tab(category.displayName, category.icon)
    data object Themes : Tab("Themes", Icons.PALETTE)
}

class ClickGuiScreen : Screen(Component.literal("Wyvern")) {
    private val openAnimation = Animation(260, Easing.OUT_QUINT)
    private var closing = false
    private var closed = false

    private val window = Rect()
    private val content = Rect()
    private val tabRects = mutableMapOf<Tab, Rect>()
    private val tabHover = mutableMapOf<Tab, Animation>()
    private val tabPillX = Smooth(-1f, speed = 24f)
    private val tabPillW = Smooth(-1f, speed = 24f)

    private val pageAnimation = Animation(200, Easing.OUT_CUBIC).also { it.snap(true) }

    private val scroll = Smooth(0f, speed = 20f)
    private var scrollTarget = 0f
    private var contentHeight = 0f

    private val visibleCards get() = (selected as? Tab.Modules)?.let { tab -> cards.filter { it.module.category == tab.category } }.orEmpty()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val progress = openAnimation.update(!closing)
        if (closing && progress <= 0.001f) {
            closed = true
            return
        }

        val r = Renderer2D(graphics)
        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()

        r.withAlpha(progress) { r.rect(0f, 0f, r.width, r.height, Theme.dim) }

        val ww = minOf(r.width - 40f, 560f)
        val wh = minOf(r.height - 30f, 360f)
        val docked = (EspPreview.WIDTH + EspPreview.GAP) * preview.dock
        window.set((r.width - ww - docked) / 2f, (r.height - wh) / 2f, ww, wh)

        r.withPose {
            r.translate(0f, 8f * (1f - progress))
            r.scaleAround(window.centerX, window.centerY, 0.95f + 0.05f * progress)
            r.withAlpha(progress) {
                r.shadow(window.x, window.y + 6f, ww, wh, Theme.WINDOW_RADIUS, 34f, Theme.shadow)
                r.shadow(window.x, window.y + 2f, ww, wh, Theme.WINDOW_RADIUS, 12f, Theme.shadow)
                r.roundedRect(window.x, window.y, ww, wh, Theme.WINDOW_RADIUS, Theme.background)
                r.outline(window.x, window.y, ww, wh, Theme.WINDOW_RADIUS, 0.5f, Theme.cardBorder)

                renderPage(r, mx, my)
                renderTabs(r, mx, my)
            }
        }
        r.withAlpha(progress) {
            preview.render(r, window.x + window.w + EspPreview.GAP, window.y, window.h, mx, my)
            picker.render(r, mx, my)
            renderToast(r)
        }
    }

    private fun renderPage(r: Renderer2D, mx: Float, my: Float) {
        content.set(window.x + 14f, window.y + 14f, window.w - 28f, window.h - 14f - BAR_AREA)
        val maxScroll = (contentHeight - content.h).coerceAtLeast(0f)
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll)
        val offset = scroll.update(scrollTarget)

        val page = pageAnimation.update(true)
        val mouseInside = content.contains(mx, my)
        val px = if (mouseInside) mx else -1f
        val py = if (mouseInside) my else -1f

        r.scissor(content.x, content.y, content.w, content.h) {
            r.withPose {
                r.translate(0f, 10f * (1f - page))
                r.withAlpha(page) {
                    val top = content.y - offset
                    contentHeight = when (selected) {
                        is Tab.Themes -> themesPage.render(r, content.x, top, content.w, px, py)
                        is Tab.Modules -> renderCards(r, top, px, py)
                    }
                }
            }
        }
        if (selected is Tab.Modules && visibleCards.isEmpty()) {
            r.withAlpha(page) {
                r.centeredText("No modules here yet", content.centerX, content.centerY - 4f, Theme.textFaint, Theme.NAME_SIZE, Theme.bodyFont)
            }
        }
    }

    private fun renderCards(r: Renderer2D, top: Float, mx: Float, my: Float): Float {
        val columns = if (content.w >= 420f) 3 else 2
        val gap = 10f
        val colW = (content.w - gap * (columns - 1)) / columns
        val columnHeights = FloatArray(columns)
        for (card in visibleCards) {
            val column = columnHeights.indices.minBy { columnHeights[it] }
            card.render(r, content.x + column * (colW + gap), top + columnHeights[column], colW, mx, my)
            columnHeights[column] += card.height + gap
        }
        return ((columnHeights.maxOrNull() ?: 0f) - gap).coerceAtLeast(0f)
    }

    private fun renderTabs(r: Renderer2D, mx: Float, my: Float) {
        val size = 20f
        val pad = 4f
        val iconTab = 32f
        val iconSize = 10.5f
        val labelSize = 8f

        val main = tabs.filter { it is Tab.Modules && it.category != Category.CLIENT }
        val extra = tabs - main.toSet()
        fun tabWidth(tab: Tab) =
            if (tab == selected) 9f + iconSize + 7f + r.textWidth(tab.name, labelSize, Theme.titleFont) + 11f else iconTab

        val separator = 14f
        val barW = pad + tabs.sumOf { tabWidth(it).toDouble() }.toFloat() + separator + pad
        val barX = window.centerX - barW / 2f
        val barY = window.y + window.h - 12f - size - pad * 2
        r.roundedRect(barX, barY, barW, size + pad * 2, 9f, Theme.card)
        r.outline(barX, barY, barW, size + pad * 2, 9f, 0.5f, Theme.cardBorder)

        var x = barX + pad
        val y = barY + pad
        for (tab in main + extra) {
            if (tab == extra.first()) {
                r.rect(x + separator / 2f - 0.25f, y + 5f, 0.5f, size - 10f, Theme.divider)
                x += separator
            }
            val w = tabWidth(tab)
            tabRects.getOrPut(tab, ::Rect).set(x, y, w, size)
            x += w
        }

        val active = tabRects.getValue(selected)
        if (tabPillX.value < 0f) {
            tabPillX.snap(active.x)
            tabPillW.snap(active.w)
        }
        val pillX = tabPillX.update(active.x)
        val pillW = tabPillW.update(active.w)
        r.roundedRect(pillX, y, pillW, size, 5.5f, Theme.accent)

        for (tab in main + extra) {
            val rect = tabRects.getValue(tab)
            if (tab == selected) {
                val arrived = (1f - abs(pillX - rect.x) / 24f).coerceIn(0f, 1f)
                r.icon(tab.icon, rect.x + 9f + iconSize / 2f, rect.centerY, iconSize, Theme.onAccent)
                r.withAlpha(arrived) {
                    r.scissor(pillX, y, pillW, size) {
                        r.textCenteredY(tab.name, rect.x + 9f + iconSize + 7f, rect.centerY, Theme.onAccent, labelSize, Theme.titleFont)
                    }
                }
            } else {
                val hover = tabHover.getOrPut(tab) { Animation(110) }.update(rect.contains(mx, my))
                r.icon(tab.icon, rect.centerX, rect.centerY, iconSize, Theme.textDim.mix(Theme.text, hover))
            }
        }
    }

    private fun renderToast(r: Renderer2D) {
        val text = toastText ?: return
        val age = System.currentTimeMillis() - toastTime
        val t = when {
            age < 200 -> Easing.OUT_CUBIC.ease(age / 200f)
            age < 1600 -> 1f
            age < 1900 -> 1f - Easing.IN_OUT_CUBIC.ease((age - 1600) / 300f)
            else -> {
                toastText = null
                return
            }
        }
        r.nextLayer()
        val tw = r.textWidth(text, 7.5f, Theme.bodyFont)
        val w = tw + 8f + 9f + 5f + 10f
        val h = 18f
        val x = window.centerX - w / 2f
        val y = window.y + window.h - 12f - 28f - h - 8f + 6f * (1f - t)
        r.withAlpha(t) {
            r.shadow(x, y + 1f, w, h, h / 2f, 8f, Theme.shadow)
            r.roundedRect(x, y, w, h, h / 2f, Theme.card)
            r.outline(x, y, w, h, h / 2f, 0.5f, Theme.cardBorder)
            r.icon(Icons.CHECK, x + 8f + 4.5f, y + h / 2f, 9f, Theme.accent)
            r.textCenteredY(text, x + 8f + 9f + 5f, y + h / 2f, Theme.text, 7.5f, Theme.bodyFont)
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        if (!closing) graphics.blurBeforeThisStratum()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (closing) return true
        val mx = event.x.toFloat()
        val my = event.y.toFloat()
        val button = event.button()
        if (picker.mouseClicked(mx, my, button)) return true
        if (preview.mouseClicked(mx, my, button)) return true

        for ((tab, rect) in tabRects) {
            if (button == Mouse.LEFT && rect.contains(mx, my)) {
                if (tab != selected) {
                    selected = tab
                    scrollTarget = 0f
                    scroll.snap(0f)
                    pageAnimation.snap(false)
                }
                cards.forEach(ModuleCard::stopListening)
                return true
            }
        }
        if (content.contains(mx, my)) {
            when (selected) {
                is Tab.Themes -> if (themesPage.mouseClicked(mx, my, button)) return true
                is Tab.Modules -> for (card in visibleCards) {
                    if (card.mouseClicked(mx, my, button)) return true
                }
            }
        } else {
            cards.forEach(ModuleCard::stopListening)
        }
        return window.contains(mx, my) || super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        cards.forEach { it.mouseReleased(event.button()) }
        picker.mouseReleased()
        preview.mouseReleased(event.button())
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        if (preview.mouseScrolled(x.toFloat(), y.toFloat(), scrollY.toFloat())) return true
        scrollTarget -= scrollY.toFloat() * 28f
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (picker.keyPressed(event.key())) return true
        if (cards.any { it.keyPressed(event.key()) }) return true
        if (event.key() == InputConstants.KEY_RSHIFT || event.key() == InputConstants.KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent) = cards.any { it.charTyped(event.codepoint()) }

    override fun onClose() {
        closing = true
        picker.close()
        cards.forEach(ModuleCard::stopListening)
    }

    override fun tick() {
        if (closed) minecraft.gui.setScreen(null)
    }

    override fun isPauseScreen() = false

    fun tabArea(category: Category): Rect? = tabRects.entries.firstOrNull { (it.key as? Tab.Modules)?.category == category }?.value
    fun themesTabArea(): Rect? = tabRects[Tab.Themes]
    fun themeTileArea(name: String): Rect? = themesPage.tileArea(name)
    fun customColorChipArea(index: Int): Rect? = themesPage.customChipArea(index)
    fun themeButtonArea(label: String): Rect? = themesPage.buttonArea(label)
    fun card(module: Module): ModuleCard? = cards.firstOrNull { it.module == module }
    fun contentArea(): Rect = content

    companion object {
        private const val BAR_AREA = 50f

        private val tabs: List<Tab> = Category.entries.map(Tab::Modules) + Tab.Themes

        private var selected: Tab = tabs.first { it is Tab.Modules && ModuleManager.byCategory(it.category).isNotEmpty() }
        private val cards by lazy { ModuleManager.modules.map(::ModuleCard) }
        private val themesPage by lazy { ThemesPage() }

        val picker = ColorPicker()
        val preview = EspPreview()

        private var toastText: String? = null
        private var toastTime = 0L

        fun toast(text: String) {
            toastText = text
            toastTime = System.currentTimeMillis()
        }
    }
}
