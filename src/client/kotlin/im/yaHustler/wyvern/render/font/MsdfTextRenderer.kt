package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.module.impl.misc.NameProtect
import im.yaHustler.wyvern.render.color.Color
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import org.joml.Matrix3x2f

class MsdfTextRenderer(private val font: GlyphFont, weightPx: Float = 0f) : TextRenderer {
    private val scaleBase = 1f / font.rasterSize

    private val weight = (weightPx * font.rasterSize / MsdfFont.RASTER_SIZE / font.range).toFloat()

    override fun width(text: String, size: Float): Float {
        val text = NameProtect.protect(text)
        val scale = size * scaleBase
        var width = 0f
        var previous = -1
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == Formatting.SECTION && i + 1 < text.length) {
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (previous != -1) width += font.kerning(previous, cp)
            width += font.glyph(cp).advance
            previous = cp
        }
        return width * scale
    }

    override fun ascent(size: Float) = font.ascender * size * scaleBase
    override fun height(size: Float) = (font.ascender + font.descender) * size * scaleBase

    override fun draw(graphics: GuiGraphicsExtractor, text: String, x: Float, y: Float, size: Float, color: Color, shadow: Boolean) {
        val text = NameProtect.protect(text)
        if (text.isEmpty() || color.alpha == 0) return
        val pose = Matrix3x2f(graphics.pose())
        val scissor = graphics.scissorStack.peek()

        pagesUsed = 0
        if (shadow) {
            val offset = size * 0.06f
            layout(text, x + offset, y + offset, size, Color(0, 0, 0, (color.alpha * 0.6f).toInt()), softness = 0.3f, keepColor = true)
        }
        layout(text, x, y, size, color, softness = 0f, keepColor = false)

        font.uploadPending()
        for (i in 0 until pagesUsed) {
            val quads = builders[i]
            if (!quads.isEmpty) graphics.guiRenderState.addGuiElement(quads.build(pose, pages[i], scissor))
        }
    }

    private val pages = ArrayList<FontAtlas>(2)
    private val builders = ArrayList<QuadBuilder>(2)
    private var pagesUsed = 0

    private fun builderFor(atlas: FontAtlas): QuadBuilder {
        for (i in 0 until pagesUsed) if (pages[i] === atlas) return builders[i]
        if (pagesUsed == pages.size) {
            pages += atlas
            builders += QuadBuilder()
        } else {
            pages[pagesUsed] = atlas
        }
        return builders[pagesUsed++].also { it.reset() }
    }

    private fun layout(
        text: String, x: Float, y: Float, size: Float, baseColor: Color,
        softness: Float, keepColor: Boolean,
    ) {
        val scale = size * scaleBase
        val baseline = y + font.ascender * scale
        var penX = x
        var color = baseColor
        var previous = -1
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == Formatting.SECTION && i + 1 < text.length) {
                if (!keepColor) Formatting.apply(text[i + 1], baseColor)?.let { color = it }
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (previous != -1) penX += font.kerning(previous, cp) * scale
            previous = cp

            val glyph = font.glyph(cp)
            val atlas = glyph.atlas
            if (atlas != null) {
                val x0 = penX + glyph.left * scale
                val y0 = baseline - glyph.top * scale
                builderFor(atlas).add(
                    x0, y0, x0 + glyph.width * scale, y0 + glyph.height * scale,
                    glyph.u0, glyph.v0, glyph.u1, glyph.v1, softness, weight, color.argb,
                )
            }
            penX += glyph.advance * scale
        }
    }

    private class QuadBuilder {
        private var data = FloatArray(64 * TextRenderState.QUAD_FLOATS)
        private var colors = IntArray(64)
        private var count = 0
        private var minX = Float.MAX_VALUE
        private var minY = Float.MAX_VALUE
        private var maxX = -Float.MAX_VALUE
        private var maxY = -Float.MAX_VALUE

        val isEmpty get() = count == 0

        fun reset() {
            count = 0
            minX = Float.MAX_VALUE; minY = Float.MAX_VALUE
            maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE
        }

        fun add(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, softness: Float, weight: Float, color: Int) {
            if (count == colors.size) {
                colors = colors.copyOf(count * 2)
                data = data.copyOf(count * 2 * TextRenderState.QUAD_FLOATS)
            }
            val o = count * TextRenderState.QUAD_FLOATS
            data[o] = x0; data[o + 1] = y0; data[o + 2] = x1; data[o + 3] = y1
            data[o + 4] = u0; data[o + 5] = v0; data[o + 6] = u1; data[o + 7] = v1
            data[o + 8] = softness; data[o + 9] = weight
            colors[count++] = color
            minX = minOf(minX, x0); minY = minOf(minY, y0); maxX = maxOf(maxX, x1); maxY = maxOf(maxY, y1)
        }

        fun build(pose: Matrix3x2f, atlas: FontAtlas, scissor: ScreenRectangle?) =
            TextRenderState(
                pose, atlas,
                data.copyOf(count * TextRenderState.QUAD_FLOATS), colors.copyOf(count),
                scissor, minX, minY, maxX, maxY,
            )
    }
}
