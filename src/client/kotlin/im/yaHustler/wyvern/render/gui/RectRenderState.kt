package im.yaHustler.wyvern.render.gui

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class RectRenderState(
    private val pose: Matrix3x2fc,
    private val x: Float,
    private val y: Float,
    private val width: Float,
    private val height: Float,
    radius: Float,
    thickness: Float,
    softness: Float,
    private val topLeft: Int,
    private val topRight: Int,
    private val bottomRight: Int,
    private val bottomLeft: Int,
    private val scissor: ScreenRectangle?,
    private val pipeline: RenderPipeline,
    private val textures: TextureSetup,
    private val fill: Int = 0,
) : GuiElementRenderState {

    private val radius16 = fixed(radius)
    private val thickness16 = fixed(thickness)
    private val softness16 = fixed(softness)

    private val padding = 1f + softness

    private val bounds: ScreenRectangle? = run {
        val p = ceil(padding).toInt()
        val rect = ScreenRectangle(
            floor(x).toInt() - p,
            floor(y).toInt() - p,
            ceil(width).toInt() + 2 * p + 1,
            ceil(height).toInt() + 2 * p + 1,
        ).transformMaxBounds(pose)
        if (scissor != null) scissor.intersection(rect) else rect
    }

    override fun buildVertices(consumer: VertexConsumer) {
        val hw = width / 2f
        val hh = height / 2f
        val x0 = x - padding
        val y0 = y - padding
        val x1 = x + width + padding
        val y1 = y + height + padding
        val lx = hw + padding
        val ly = hh + padding

        vertex(consumer, x0, y0, -lx, -ly, hw, hh, topLeft)
        vertex(consumer, x0, y1, -lx, ly, hw, hh, bottomLeft)
        vertex(consumer, x1, y1, lx, ly, hw, hh, bottomRight)
        vertex(consumer, x1, y0, lx, -ly, hw, hh, topRight)
    }

    private fun vertex(consumer: VertexConsumer, px: Float, py: Float, lx: Float, ly: Float, hw: Float, hh: Float, color: Int) {
        consumer.addVertexWith2DPose(pose, px, py)
            .setColor(color)
            .setUv(lx, ly)
            .setUv3(hw, hh)
            .setUv1(radius16, thickness16)
            .setUv2(softness16, fill)
    }

    override fun pipeline(): RenderPipeline = pipeline
    override fun textureSetup(): TextureSetup = textures
    override fun scissorArea(): ScreenRectangle? = scissor
    override fun bounds(): ScreenRectangle? = bounds

    private companion object {
        fun fixed(value: Float) = (value * 16f).roundToInt().coerceIn(0, Short.MAX_VALUE.toInt())
    }
}
