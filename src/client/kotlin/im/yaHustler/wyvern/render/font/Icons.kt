package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.Wyvern
import im.yaHustler.wyvern.render.color.Color
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.gui.GuiGraphicsExtractor

object Icons {
    const val PERSON = 0xe7fd
    const val FPS = 0xe3e0
    const val CLOUD = 0xe2bd
    const val NAVIGATION = 0xe55d
    const val FLIGHT = 0xe539
    const val SCHEDULE = 0xe8b5
    const val LOGO = 0xe50f
    const val CROSSHAIR = 0xe1b3
    const val BOLT = 0xea0b
    const val GAMEPAD = 0xea28
    const val EYE = 0xe8f4
    const val WIDGETS = 0xe1bd
    const val EDIT = 0xe3c9
    const val DESCRIPTION = 0xe873
    const val KEYBOARD = 0xe312
    const val CHECK = 0xe5ca
    const val CLOSE = 0xe5cd
    const val SWAP = 0xe8d4
    const val GROUP = 0xe7ef
    const val SWAP_VERT = 0xe8d5
    const val SEARCH = 0xe8b6
    const val ADD = 0xe145
    const val SHUFFLE = 0xe043
    const val BOOKMARK = 0xe866
    const val BOOKMARK_BORDER = 0xe867
    const val CENTER_FOCUS = 0xe3b4
    const val NOTIFICATIONS = 0xe7f4
    const val CHEVRON_RIGHT = 0xe5cc
    const val PALETTE = 0xe40a
    const val SCIENCE = 0xea4b
    const val TIMER = 0xe425
    const val SHIELD = 0xe9e0
    const val FAVORITE = 0xe87d
    const val COPY = 0xe14d
    const val PASTE = 0xe14f
    const val SAVE = 0xe161
    const val DELETE = 0xe872
    const val DASHBOARD = 0xe871
    const val OPEN_WITH = 0xe89f
    const val SETTINGS = 0xe8b8

    private val font: MsdfFont? by lazy {
        val data = Icons::class.java.getResourceAsStream("/assets/wyvern/fonts/icons.ttf")?.use { it.readBytes() }
        if (data == null) {
            Wyvern.logger.warn("icons.ttf missing, icons won't render")
            null
        } else {
            MsdfFont.load("icons.ttf", data)
        }
    }

    fun draw(graphics: GuiGraphicsExtractor, codepoint: Int, cx: Float, cy: Float, size: Float, color: Color) {
        val font = font ?: return
        val renderer = renderer ?: return
        if (color.alpha == 0) return
        val glyph = font.glyph(codepoint)
        val scale = size / MsdfFont.RASTER_SIZE
        val inkCenterX = (glyph.left + glyph.width / 2f) * scale
        val inkCenterY = (font.ascender - glyph.top + glyph.height / 2f) * scale
        val text = strings.get(codepoint) ?: String(Character.toChars(codepoint)).also { strings.put(codepoint, it) }
        renderer.draw(graphics, text, cx - inkCenterX, cy - inkCenterY, size, color)
    }

    private val renderer: MsdfTextRenderer? by lazy { font?.let(::MsdfTextRenderer) }
    private val strings = Int2ObjectOpenHashMap<String>()
}
