package im.yaHustler.wyvern.render.world

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.StagedVertexBuffer

internal class WorldBatch(val pipeline: RenderPipeline, private val layout: Layout) {
    enum class Layout(val floats: Int) { SOLID(3), LINES(7), GLOW(5) }

    private var data = FloatArray(1024)
    private var colors = IntArray(256)
    private var size = 0
    var vertices = 0
        private set
    var draw: StagedVertexBuffer.Draw? = null

    val isEmpty get() = vertices == 0

    fun solid(x: Float, y: Float, z: Float, color: Int) {
        reserve()
        put(x, y, z)
        colors[vertices++] = color
    }

    fun line(x: Float, y: Float, z: Float, color: Int, nx: Float, ny: Float, nz: Float, width: Float) {
        reserve()
        put(x, y, z)
        put(nx, ny, nz)
        data[size++] = width
        colors[vertices++] = color
    }

    fun glow(x: Float, y: Float, z: Float, u: Float, v: Float, color: Int) {
        reserve()
        put(x, y, z)
        data[size++] = u
        data[size++] = v
        colors[vertices++] = color
    }

    fun write(consumer: VertexConsumer) {
        val d = data
        var i = 0
        when (layout) {
            Layout.SOLID -> for (v in 0 until vertices) {
                consumer.addVertex(d[i], d[i + 1], d[i + 2]).setColor(colors[v])
                i += 3
            }
            Layout.LINES -> for (v in 0 until vertices) {
                consumer.addVertex(d[i], d[i + 1], d[i + 2]).setColor(colors[v])
                    .setNormal(d[i + 3], d[i + 4], d[i + 5]).setLineWidth(d[i + 6])
                i += 7
            }
            Layout.GLOW -> for (v in 0 until vertices) {
                consumer.addVertex(d[i], d[i + 1], d[i + 2]).setUv(d[i + 3], d[i + 4]).setColor(colors[v])
                i += 5
            }
        }
    }

    fun clear() {
        size = 0
        vertices = 0
        draw = null
    }

    private fun put(a: Float, b: Float, c: Float) {
        data[size] = a
        data[size + 1] = b
        data[size + 2] = c
        size += 3
    }

    private fun reserve() {
        if (size + layout.floats > data.size) data = data.copyOf(data.size * 2)
        if (vertices == colors.size) colors = colors.copyOf(colors.size * 2)
    }
}
