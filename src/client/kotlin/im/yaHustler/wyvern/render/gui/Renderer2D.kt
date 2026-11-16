package im.yaHustler.wyvern.render.gui

import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.effect.Blur
import im.yaHustler.wyvern.render.font.Fonts
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.font.TextRenderer
import im.yaHustler.wyvern.render.pipeline.GuiPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Matrix3x2f
import kotlin.math.ceil

class Renderer2D(val graphics: GuiGraphicsExtractor) {
    val width get() = graphics.guiWidth().toFloat()
    val height get() = graphics.guiHeight().toFloat()

    var alpha = 1f

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) =
        roundedRect(x, y, w, h, 0f, color)

    fun roundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) =
        shape(x, y, w, h, radius, color, color, color, color)

    fun gradientV(x: Float, y: Float, w: Float, h: Float, radius: Float, top: Color, bottom: Color) =
        shape(x, y, w, h, radius, top, top, bottom, bottom)

    fun gradientH(x: Float, y: Float, w: Float, h: Float, radius: Float, left: Color, right: Color) =
        shape(x, y, w, h, radius, left, right, right, left)

    fun outline(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Color) =
        shape(x, y, w, h, radius, color, color, color, color, thickness = thickness)

    // A capsule from one point to the other: round caps make chained segments (arcs, chevrons, polylines) meet
    // without notches or gaps at the joints, and the SDF edge keeps it antialiased at any angle.
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Color) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length < 0.01f) return
        val half = thickness / 2f
        withPose {
            translate(x1, y1)
            graphics.pose().rotate(Math.atan2(dy.toDouble(), dx.toDouble()).toFloat())
            roundedRect(-half, -half, length + thickness, thickness, half, color)
        }
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Color) =
        roundedRect(cx - r, cy - r, r * 2, r * 2, r, color)

    fun ring(cx: Float, cy: Float, radius: Float, thickness: Float, progress: Float, color: Color) {
        val packed = ShapeFill.ring(progress)
        shape(cx - radius, cy - radius, radius * 2, radius * 2, radius, color, color, color, color, thickness = thickness, fill = packed)
    }

    fun dashedOutline(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, dash: Float, color: Color) {
        val packed = ShapeFill.dashed(dash)
        shape(x, y, w, h, radius, color, color, color, color, thickness = thickness, fill = packed)
    }

    fun playerHead(skin: PlayerSkin, x: Float, y: Float, size: Float) {
        val texture = skin.body().texturePath()
        val color = fade(Color.WHITE)
        withPose {
            translate(x, y)
            scale(size / 32f)
            graphics.blit(GuiPipelines.ROUNDED_HEAD, texture, 0, 0, 8f, 8f, 32, 32, 8, 8, 64, 64, color)
            graphics.blit(GuiPipelines.ROUNDED_HEAD, texture, 0, 0, 40f, 8f, 32, 32, 8, 8, 64, 64, color)
        }
    }

    fun shadow(x: Float, y: Float, w: Float, h: Float, radius: Float, blur: Float, color: Color = Color(0, 0, 0, 120)) =
        shape(x, y, w, h, radius, color, color, color, color, softness = blur)

    fun glow(x: Float, y: Float, w: Float, h: Float, radius: Float, blur: Float, color: Color) =
        shadow(x, y, w, h, radius, blur, color)

    fun blur(x: Float, y: Float, w: Float, h: Float, radius: Float, tint: Color = Color(20, 18, 28)) =
        shape(x, y, w, h, radius, tint, tint, tint, tint, pipelineBlur = true)

    fun dotGrid(x: Float, y: Float, w: Float, h: Float, spacing: Float, color: Color) =
        shape(x, y, w, h, 0f, color, color, color, color, fill = ShapeFill.dotGrid(spacing))

    fun hueSpectrum(x: Float, y: Float, w: Float, h: Float, radius: Float) =
        shape(x, y, w, h, radius, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, fill = ShapeFill.HUE_SPECTRUM)

    fun checkerboard(x: Float, y: Float, w: Float, h: Float, radius: Float) =
        shape(x, y, w, h, radius, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, fill = ShapeFill.CHECKERBOARD)

    fun shape(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        topLeft: Color, topRight: Color, bottomRight: Color, bottomLeft: Color,
        thickness: Float = 0f,
        softness: Float = 0f,
        pipelineBlur: Boolean = false,
        fill: Int = ShapeFill.VERTEX_COLORS,
    ) {
        if (w <= 0f || h <= 0f || alpha <= 0f) return
        val tl = fade(topLeft)
        val tr = fade(topRight)
        val br = fade(bottomRight)
        val bl = fade(bottomLeft)
        if ((tl or tr or br or bl) ushr 24 == 0) return
        val textures = if (pipelineBlur) Blur.textureSetup() else TextureSetup.noTexture()
        graphics.guiRenderState.addGuiElement(
            RectRenderState(
                Matrix3x2f(graphics.pose()),
                x, y, w, h, radius, thickness, softness,
                tl, tr, br, bl,
                graphics.scissorStack.peek(),
                if (pipelineBlur) GuiPipelines.RECT_BLUR else GuiPipelines.RECT,
                textures,
                fill,
            )
        )
    }

    fun text(
        text: String, x: Float, y: Float, color: Color,
        size: Float = DEFAULT_TEXT_SIZE, font: TextRenderer = Fonts.regular, shadow: Boolean = false,
    ) = font.draw(graphics, text, x, y, size, Color(fade(color)), shadow)

    fun centeredText(
        text: String, cx: Float, y: Float, color: Color,
        size: Float = DEFAULT_TEXT_SIZE, font: TextRenderer = Fonts.regular, shadow: Boolean = false,
    ) = text(text, cx - textWidth(text, size, font) / 2f, y, color, size, font, shadow)

    fun textCenteredY(
        text: String, x: Float, cy: Float, color: Color,
        size: Float = DEFAULT_TEXT_SIZE, font: TextRenderer = Fonts.regular, shadow: Boolean = false,
    ) = text(text, x, cy - font.height(size) / 2f, color, size, font, shadow)

    fun icon(codepoint: Int, cx: Float, cy: Float, size: Float, color: Color) =
        Icons.draw(graphics, codepoint, cx, cy, size, Color(fade(color)))

    fun textWidth(text: String, size: Float = DEFAULT_TEXT_SIZE, font: TextRenderer = Fonts.regular) = font.width(text, size)
    fun textHeight(size: Float = DEFAULT_TEXT_SIZE, font: TextRenderer = Fonts.regular) = font.height(size)

    inline fun scissor(x: Float, y: Float, w: Float, h: Float, block: () -> Unit) {
        graphics.enableScissor(x.toInt(), y.toInt(), ceil(x + w).toInt(), ceil(y + h).toInt())
        try {
            block()
        } finally {
            graphics.disableScissor()
        }
    }

    inline fun withPose(block: () -> Unit) {
        graphics.pose().pushMatrix()
        try {
            block()
        } finally {
            graphics.pose().popMatrix()
        }
    }

    inline fun withAlpha(alpha: Float, block: () -> Unit) {
        val previous = this.alpha
        this.alpha = previous * alpha
        try {
            block()
        } finally {
            this.alpha = previous
        }
    }

    fun translate(x: Float, y: Float) {
        graphics.pose().translate(x, y)
    }

    fun scale(x: Float, y: Float = x) {
        graphics.pose().scale(x, y)
    }

    fun rotate(degrees: Float) {
        graphics.pose().rotate(Math.toRadians(degrees.toDouble()).toFloat())
    }

    fun scaleAround(cx: Float, cy: Float, factor: Float) {
        translate(cx, cy)
        scale(factor)
        translate(-cx, -cy)
    }

    fun nextLayer() = graphics.nextStratum()

    private fun fade(color: Color): Int =
        if (alpha >= 1f) color.argb else color.withAlpha((color.alpha * alpha).toInt().coerceIn(0, 255)).argb

    companion object {
        const val DEFAULT_TEXT_SIZE = 9f
    }
}
