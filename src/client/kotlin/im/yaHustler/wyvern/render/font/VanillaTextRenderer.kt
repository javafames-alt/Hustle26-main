package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.render.color.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object VanillaTextRenderer : TextRenderer {
    private val font get() = Minecraft.getInstance().font
    private fun scale(size: Float) = size / 9f

    override fun width(text: String, size: Float) = font.width(text) * scale(size)
    override fun ascent(size: Float) = 7f * scale(size)
    override fun height(size: Float) = 8f * scale(size)

    override fun draw(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, size: Float, color: Color, shadow: Boolean) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale(size))
        graphics.text(font, text, 0, 0, color.argb, shadow)
        pose.popMatrix()
    }
}
