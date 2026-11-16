package im.yaHustler.wyvern.ui.clickgui

import im.yaHustler.wyvern.module.impl.client.ClickGuiModule
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.ColorSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Palette
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.ui.theme.ThemePresets
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import net.minecraft.client.Minecraft

class ThemesPage {
    private class Tile(val name: String) {
        val rect = Rect()
        val copy = Rect()
        val delete = Rect()
        val hover = Animation(120)
        val selected = Animation(180)
        val appear = Animation(220)
    }

    private class Row(val setting: ColorSetting) {
        val chip = Rect()
        val hover = Animation(110)
    }

    private class Button(val label: String, val icon: Int, val action: () -> Unit) {
        val rect = Rect()
        val hover = Animation(110)
    }

    private val tiles = mutableMapOf<String, Tile>()
    private val rows by lazy { ClickGuiModule.customColors.map(::Row) }
    private val buttons = listOf(
        Button("Save preset", Icons.SAVE) { savePreset() },
        Button("Copy", Icons.COPY) { copy(Theme.resolve(Theme.palettes.last())) },
        Button("Paste", Icons.PASTE) { paste() },
    )

    private val mc get() = Minecraft.getInstance()

    fun render(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float): Float {
        val palettes = Theme.palettes
        tiles.keys.retainAll(palettes.map { it.name }.toSet())
        val columns = if (width >= 420f) 4 else 3
        val gap = 8f
        val tileW = (width - gap * (columns - 1)) / columns
        val tileH = 74f

        for ((index, palette) in palettes.withIndex()) {
            val tile = tiles.getOrPut(palette.name) { Tile(palette.name) }
            val tx = x + (index % columns) * (tileW + gap)
            val ty = y + (index / columns) * (tileH + gap)
            tile.rect.set(tx, ty, tileW, tileH)
            renderTile(r, tile, Theme.resolve(palette), mouseX, mouseY)
        }
        val rowCount = (palettes.size + columns - 1) / columns
        val tilesHeight = rowCount * tileH + (rowCount - 1) * gap
        return tilesHeight + gap + renderEditor(r, x, y + tilesHeight + gap, width, mouseX, mouseY)
    }

    private fun renderTile(r: Renderer2D, tile: Tile, p: Palette, mouseX: Float, mouseY: Float) {
        val rect = tile.rect
        val appear = tile.appear.update(true)
        val hover = tile.hover.update(rect.contains(mouseX, mouseY))
        val selected = tile.selected.update(Theme.current.name == p.name)

        r.withPose {
            r.scaleAround(rect.centerX, rect.centerY, 0.9f + 0.1f * appear)
            r.withAlpha(appear) {
                r.roundedRect(rect.x, rect.y, rect.w, rect.h, Theme.CARD_RADIUS, Theme.card)
                r.outline(rect.x, rect.y, rect.w, rect.h, Theme.CARD_RADIUS, 0.5f + 0.75f * selected,
                    Theme.cardBorder.mix(Theme.accent, selected).mix(Theme.text.withAlpha(40), hover * (1f - selected)))

                val px = rect.x + 6f
                val py = rect.y + 6f
                val pw = rect.w - 12f
                val ph = 44f
                r.roundedRect(px, py, pw, ph, 6f, p.background)
                r.roundedRect(px + 6f, py + 5f, pw * 0.42f, 5f, 2.5f, p.background.mix(p.text, 0.08f))
                r.circle(px + 8.5f, py + 7.5f, 1.4f, p.accent)
                val cx = px + 6f
                val cy = py + 14f
                val cw = pw - 12f
                r.roundedRect(cx, cy, cw, ph - 20f, 4f, p.card)
                r.roundedRect(cx + 5f, cy + 5f, cw * 0.4f, 3f, 1.5f, p.text.withAlpha(200))
                r.roundedRect(cx + 5f, cy + 10f, cw * 0.28f, 2f, 1f, p.textDim.withAlpha(160))
                r.roundedRect(cx + cw - 11f, cy + 4f, 7f, 7f, 2f, p.accent)
                r.roundedRect(cx + 5f, cy + 17f, cw - 10f, 1.5f, 0.75f, p.text.withAlpha(30))
                r.roundedRect(cx + 5f, cy + 17f, (cw - 10f) * 0.6f, 1.5f, 0.75f, p.accent)

                r.scissor(rect.x, rect.y, rect.w - 22f, rect.h) {
                    r.textCenteredY(p.name, rect.x + 8f, rect.y + rect.h - 12f, Theme.text, Theme.NAME_SIZE, Theme.bodyFont)
                }
                if (selected > 0.01f) {
                    r.withAlpha(selected) {
                        val bx = rect.x + rect.w - 8f - 11f
                        val by = rect.y + rect.h - 12f - 5.5f
                        r.roundedRect(bx, by, 11f, 11f, 3.5f, Theme.accent)
                        r.icon(Icons.CHECK, bx + 5.5f, by + 5.5f, 8f, Theme.onAccent)
                    }
                }

                tile.copy.set(0f, 0f, 0f, 0f)
                tile.delete.set(0f, 0f, 0f, 0f)
                if (hover > 0.01f) {
                    r.withAlpha(hover) {
                        var bx = px + pw - 4f - 13f
                        tile.copy.set(bx, py + 4f, 13f, 13f)
                        iconButton(r, tile.copy, Icons.COPY, mouseX, mouseY)
                        if (!Theme.isBuiltIn(p.name)) {
                            bx -= 15f
                            tile.delete.set(bx, py + 4f, 13f, 13f)
                            iconButton(r, tile.delete, Icons.DELETE, mouseX, mouseY)
                        }
                    }
                }
            }
        }
    }

    private fun iconButton(r: Renderer2D, rect: Rect, icon: Int, mouseX: Float, mouseY: Float) {
        val hot = rect.contains(mouseX, mouseY)
        r.roundedRect(rect.x, rect.y, rect.w, rect.h, 3.5f, Color(0, 0, 0, if (hot) 170 else 120))
        r.icon(icon, rect.centerX, rect.centerY, 8f, Color(255, 255, 255, if (hot) 255 else 200))
    }

    private fun renderEditor(r: Renderer2D, x: Float, y: Float, width: Float, mouseX: Float, mouseY: Float): Float {
        val header = 28f
        val rowH = 26f
        val columns = 2
        val colW = (width - 20f - 10f) / columns
        val height = header + 1f + 6f + ((rows.size + columns - 1) / columns) * rowH + 4f

        r.roundedRect(x, y, width, height, Theme.CARD_RADIUS, Theme.card)
        r.outline(x, y, width, height, Theme.CARD_RADIUS, 0.5f, Theme.cardBorder)
        r.textCenteredY("Custom theme", x + 10f, y + header / 2f, Theme.text, Theme.TITLE_SIZE, Theme.titleFont)

        var bx = x + width - 8f
        for (button in buttons.asReversed()) {
            val tw = r.textWidth(button.label, 7f, Theme.bodyFont)
            val bw = 6f + 8f + 4f + tw + 7f
            bx -= bw
            button.rect.set(bx, y + (header - 16f) / 2f, bw, 16f)
            val hover = button.hover.update(button.rect.contains(mouseX, mouseY))
            r.roundedRect(button.rect.x, button.rect.y, bw, 16f, Theme.CONTROL_RADIUS, Theme.control.mix(Theme.controlHover, hover))
            r.icon(button.icon, button.rect.x + 6f + 4f, button.rect.centerY, 8.5f, Theme.textDim.mix(Theme.accent, hover))
            r.textCenteredY(button.label, button.rect.x + 6f + 8f + 4f, button.rect.centerY, Theme.textDim.mix(Theme.text, hover), 7f, Theme.bodyFont)
            bx -= 4f
        }
        r.rect(x, y + header, width, 0.5f, Theme.divider)

        for ((index, row) in rows.withIndex()) {
            val rx = x + 10f + (index % columns) * (colW + 10f)
            val ry = y + header + 7f + (index / columns) * rowH
            r.text(row.setting.name, rx, ry, Theme.text, Theme.NAME_SIZE, Theme.bodyFont)
            r.text(row.setting.description, rx, ry + 10f, Theme.textFaint, Theme.DESCRIPTION_SIZE, Theme.bodyFont)

            val hex = "#%06X".format(row.setting.value.argb and 0xFFFFFF)
            val tw = r.textWidth(hex, 6.5f, Theme.bodyFont)
            val cw = 4f + 9f + 4f + tw + 6f
            row.chip.set(rx + colW - cw, ry + 3f, cw, 13f)
            val hover = row.hover.update(row.chip.contains(mouseX, mouseY))
            r.roundedRect(row.chip.x, row.chip.y, cw, 13f, 4f, Theme.control.mix(Theme.controlHover, hover))
            r.roundedRect(row.chip.x + 3f, row.chip.y + 2.5f, 9f, 8f, 2.5f, row.setting.value.withAlpha(255))
            r.textCenteredY(hex, row.chip.x + 4f + 9f + 3f, row.chip.centerY, Theme.textDim, 6.5f, Theme.bodyFont)
        }
        return height
    }

    private fun savePreset() {
        val custom = Theme.resolve(Theme.palettes.last())
        val stored = ThemePresets.add(custom.copy(name = "Preset"))
        ClickGuiModule.theme.value = stored.name
        ClickGuiScreen.toast("Saved as ${stored.name}")
    }

    private fun copy(palette: Palette) {
        mc.keyboardHandler.clipboard = Theme.toCode(palette)
        ClickGuiScreen.toast("Copied ${palette.name}")
    }

    private fun paste() {
        val palette = Theme.fromCode(mc.keyboardHandler.clipboard)
        if (palette == null) {
            ClickGuiScreen.toast("Clipboard has no theme")
            return
        }
        val colors = ClickGuiModule.customColors
        colors[0].value = palette.background
        colors[1].value = palette.card
        colors[2].value = palette.accent
        colors[3].value = palette.text
        ClickGuiModule.theme.value = Theme.CUSTOM
        ClickGuiScreen.toast("Pasted ${palette.name}")
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        buttons.firstOrNull { it.rect.contains(mouseX, mouseY) }?.let {
            if (button == Mouse.LEFT) it.action()
            return true
        }
        rows.firstOrNull { it.chip.contains(mouseX, mouseY) }?.let { row ->
            if (button != Mouse.LEFT) return true
            ClickGuiModule.theme.value = Theme.CUSTOM
            ClickGuiScreen.picker.open(row.setting, row.chip)
            return true
        }
        val tile = tiles.values.firstOrNull { it.rect.contains(mouseX, mouseY) } ?: return false
        val palette = Theme.palettes.firstOrNull { it.name == tile.name } ?: return true
        when {
            tile.copy.contains(mouseX, mouseY) -> copy(Theme.resolve(palette))
            tile.delete.contains(mouseX, mouseY) -> {
                if (Theme.current.name == palette.name) ClickGuiModule.theme.value = Theme.CUSTOM
                ThemePresets.remove(palette.name)
                ClickGuiScreen.toast("Deleted ${palette.name}")
            }
            button == Mouse.LEFT -> ClickGuiModule.theme.value = palette.name
        }
        return true
    }

    fun tileArea(name: String): Rect? = tiles[name]?.rect
    fun customChipArea(index: Int): Rect? = rows.getOrNull(index)?.chip
    fun buttonArea(label: String): Rect? = buttons.firstOrNull { it.label == label }?.rect
}
