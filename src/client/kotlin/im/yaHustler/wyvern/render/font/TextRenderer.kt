package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.render.color.Color
import net.minecraft.client.gui.GuiGraphicsExtractor

interface TextRenderer {
    fun width(text: String, size: Float): Float

    fun ascent(size: Float): Float

    fun height(size: Float): Float

    fun draw(
        graphics: GuiGraphicsExtractor,
        text: String,
        x: Float, y: Float, size: Float,
        color: Color,
        shadow: Boolean = false,
    )
}
