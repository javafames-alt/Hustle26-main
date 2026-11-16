package im.yaHustler.wyvern.render.font

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import im.yaHustler.wyvern.render.pipeline.GuiPipelines
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.floor

class TextRenderState(
    private val pose: Matrix3x2fc,
    private val atlas: FontAtlas,
    private val quads: FloatArray,
    private val colors: IntArray,
    private val scissor: ScreenRectangle?,
    minX: Float, minY: Float, maxX: Float, maxY: Float,
) : GuiElementRenderState {

    private val bounds: ScreenRectangle? = run {
        val rect = ScreenRectangle(
            floor(minX).toInt(), floor(minY).toInt(),
            (ceil(maxX) - floor(minX)).toInt() + 1, (ceil(maxY) - floor(minY)).toInt() + 1,
        ).transformMaxBounds(pose)
        if (scissor != null) scissor.intersection(rect) else rect
    }

    override fun buildVertices(consumer: VertexConsumer) {
        var i = 0
        for (color in colors) {
            val x0 = quads[i]; val y0 = quads[i + 1]; val x1 = quads[i + 2]; val y1 = quads[i + 3]
            val u0 = quads[i + 4]; val v0 = quads[i + 5]; val u1 = quads[i + 6]; val v1 = quads[i + 7]
            val softness = quads[i + 8]; val weight = quads[i + 9]
            i += QUAD_FLOATS
            consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color).setUv3(softness, weight)
            consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color).setUv3(softness, weight)
            consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color).setUv3(softness, weight)
            consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color).setUv3(softness, weight)
        }
    }

    override fun pipeline(): RenderPipeline = GuiPipelines.msdfText(atlas.range)
    override fun textureSetup(): TextureSetup = atlas.textureSetup
    override fun scissorArea(): ScreenRectangle? = scissor
    override fun bounds(): ScreenRectangle? = bounds

    companion object {
        const val QUAD_FLOATS = 10
    }
}
