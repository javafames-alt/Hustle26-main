package im.yaHustler.wyvern.render.world

import com.mojang.blaze3d.systems.RenderSystem
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.pipeline.WorldPipelines
import im.yaHustler.wyvern.render.world.WorldBatch.Layout
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Renderer3D {
    private const val RIBBON_PIXEL = 0.00075

    private val quads = WorldBatch(WorldPipelines.QUADS, Layout.SOLID)
    private val quadsNoDepth = WorldBatch(WorldPipelines.QUADS_NO_DEPTH, Layout.SOLID)
    private val lines = WorldBatch(WorldPipelines.LINES, Layout.LINES)
    private val linesNoDepth = WorldBatch(WorldPipelines.LINES_NO_DEPTH, Layout.LINES)
    private val trailBand = WorldBatch(WorldPipelines.TRAIL_BAND, Layout.SOLID)
    private val trailLines = WorldBatch(WorldPipelines.TRAIL_LINE, Layout.LINES)
    private val additiveNoDepth = WorldBatch(WorldPipelines.ADDITIVE_QUADS_NO_DEPTH, Layout.SOLID)
    private val additive = WorldBatch(WorldPipelines.ADDITIVE_QUADS, Layout.SOLID)
    private val glows = WorldBatch(WorldPipelines.GLOW, Layout.GLOW)
    private val glowsNoDepth = WorldBatch(WorldPipelines.GLOW_NO_DEPTH, Layout.GLOW)
    private val batches = arrayOf(quads, quadsNoDepth, lines, linesNoDepth, trailBand, trailLines, additiveNoDepth, additive, glows, glowsNoDepth)

    private val vertexBuffer by lazy { StagedVertexBuffer({ "Wyvern world vertex buffer" }, 256 * 1024) }

    private val cameraRight = Vector3f(1f, 0f, 0f)
    private val cameraUp = Vector3f(0f, 1f, 0f)
    private val worldToClip = Matrix4f()
    private val clipScratch = Vector4f()
    private val billboardScratch = FloatArray(9)
    private val ribbonScratch = DoubleArray(6)
    private var projectionReady = false
    private var camX = 0.0
    private var camY = 0.0
    private var camZ = 0.0

    var cameraPos: Vec3 = Vec3.ZERO
        private set

    var tickDelta = 0f
        private set

    val right: Vector3fc get() = cameraRight
    val up: Vector3fc get() = cameraUp

    fun box(box: AABB, color: Color, throughWalls: Boolean = false) =
        boxFaces(solid(throughWalls), box, color.argb, color.argb)

    fun additiveBox(box: AABB, color: Color) =
        boxFaces(additiveNoDepth, box, color.argb, color.argb)

    fun gradientBox(box: AABB, bottom: Color, top: Color, throughWalls: Boolean = false) =
        boxFaces(solid(throughWalls), box, bottom.argb, top.argb)

    fun boxOutline(box: AABB, color: Color, width: Float = 2f, throughWalls: Boolean = false) {
        val x0 = box.minX; val y0 = box.minY; val z0 = box.minZ
        val x1 = box.maxX; val y1 = box.maxY; val z1 = box.maxZ
        horizontalOutline(box, y0, color, width, throughWalls)
        horizontalOutline(box, y1, color, width, throughWalls)
        line(x0, y0, z0, x0, y1, z0, color, width, throughWalls)
        line(x1, y0, z0, x1, y1, z0, color, width, throughWalls)
        line(x1, y0, z1, x1, y1, z1, color, width, throughWalls)
        line(x0, y0, z1, x0, y1, z1, color, width, throughWalls)
    }

    fun triangle(a: Vec3, b: Vec3, c: Vec3, color: Color, throughWalls: Boolean = false) =
        quad(solid(throughWalls), a, b, c, c, color.argb, color.argb, color.argb, color.argb)

    fun additiveTriangle(a: Vec3, b: Vec3, c: Vec3, color: Color, throughWalls: Boolean = false) =
        quad(additive(throughWalls), a, b, c, c, color.argb, color.argb, color.argb, color.argb)

    fun additiveQuad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, ca: Color, cb: Color, cc: Color, cd: Color, throughWalls: Boolean = false) =
        quad(additive(throughWalls), a, b, c, d, ca.argb, cb.argb, cc.argb, cd.argb)

    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, color: Color, throughWalls: Boolean = false) =
        quad(a, b, c, d, color, color, color, color, throughWalls)

    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, ca: Color, cb: Color, cc: Color, cd: Color, throughWalls: Boolean = false) =
        quad(solid(throughWalls), a, b, c, d, ca.argb, cb.argb, cc.argb, cd.argb)

    fun trailQuad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, ca: Color, cb: Color, cc: Color, cd: Color) =
        quad(trailBand, a, b, c, d, ca.argb, cb.argb, cc.argb, cd.argb)

    fun glow(pos: Vec3, size: Float, color: Color, throughWalls: Boolean = false) {
        val batch = if (throughWalls) glowsNoDepth else glows
        val cx = rx(pos.x); val cy = ry(pos.y); val cz = rz(pos.z)
        val h = size / 2f
        val rx = cameraRight.x * h; val ry = cameraRight.y * h; val rz = cameraRight.z * h
        val ux = cameraUp.x * h; val uy = cameraUp.y * h; val uz = cameraUp.z * h
        val argb = color.argb
        batch.glow(cx - rx - ux, cy - ry - uy, cz - rz - uz, -1f, -1f, argb)
        batch.glow(cx - rx + ux, cy - ry + uy, cz - rz + uz, -1f, 1f, argb)
        batch.glow(cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 1f, argb)
        batch.glow(cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, -1f, argb)
    }

    fun billboard(center: Vec3, size: Float, angle: Float, triangles: FloatArray, color: Color, throughWalls: Boolean = false) {
        val batch = additive(throughWalls)
        val cx = rx(center.x); val cy = ry(center.y); val cz = rz(center.z)
        val c = cos(angle)
        val s = sin(angle)
        val rx = cameraRight.x * size; val ry = cameraRight.y * size; val rz = cameraRight.z * size
        val ux = cameraUp.x * size; val uy = cameraUp.y * size; val uz = cameraUp.z * size
        val argb = color.argb
        val p = billboardScratch
        var i = 0
        while (i + 5 < triangles.size) {
            for (k in 0 until 3) {
                val x = triangles[i + k * 2]
                val y = triangles[i + k * 2 + 1]
                val tx = x * c - y * s
                val ty = x * s + y * c
                p[k * 3] = cx + rx * tx + ux * ty
                p[k * 3 + 1] = cy + ry * tx + uy * ty
                p[k * 3 + 2] = cz + rz * tx + uz * ty
            }
            quad(batch, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[6], p[7], p[8], argb, argb, argb, argb)
            i += 6
        }
    }

    fun trailLine(from: Vec3, to: Vec3, fromColor: Color, toColor: Color, width: Float) {
        if (!segment(trailLines, from.x, from.y, from.z, to.x, to.y, to.z, fromColor.argb, toColor.argb, width)) {
            trailLines.line(rx(from.x), ry(from.y), rz(from.z), fromColor.argb, 0f, 1f, 0f, width)
            trailLines.line(rx(to.x), ry(to.y), rz(to.z), toColor.argb, 0f, 1f, 0f, width)
        }
    }

    fun line(from: Vec3, to: Vec3, color: Color, width: Float = 2f, throughWalls: Boolean = false) =
        line(from.x, from.y, from.z, to.x, to.y, to.z, color, width, throughWalls)

    fun line(
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        color: Color, width: Float = 2f, throughWalls: Boolean = false,
    ) {
        segment(if (throughWalls) linesNoDepth else lines, x0, y0, z0, x1, y1, z1, color.argb, color.argb, width)
    }

    fun ribbon(from: Vec3, to: Vec3, fromColor: Color, toColor: Color, px: Float) {
        val ax = from.x - camX; val ay = from.y - camY; val az = from.z - camZ
        val bx = to.x - camX; val by = to.y - camY; val bz = to.z - camZ
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1.0E-9) return
        dx /= len; dy /= len; dz /= len
        val s = ribbonScratch
        ribbonSide(ax, ay, az, dx, dy, dz, px, s, 0)
        ribbonSide(bx, by, bz, dx, dy, dz, px, s, 3)
        quad(
            additiveNoDepth,
            (ax + s[0]).toFloat(), (ay + s[1]).toFloat(), (az + s[2]).toFloat(),
            (bx + s[3]).toFloat(), (by + s[4]).toFloat(), (bz + s[5]).toFloat(),
            (bx - s[3]).toFloat(), (by - s[4]).toFloat(), (bz - s[5]).toFloat(),
            (ax - s[0]).toFloat(), (ay - s[1]).toFloat(), (az - s[2]).toFloat(),
            fromColor.argb, toColor.argb, toColor.argb, fromColor.argb,
        )
    }

    fun project(pos: Vec3, width: Float, height: Float): Vector2f? {
        val out = Vector2f()
        return if (project(pos.x, pos.y, pos.z, width, height, out)) out else null
    }

    fun project(x: Double, y: Double, z: Double, width: Float, height: Float, out: Vector2f): Boolean {
        if (!projectionReady) return false
        val clip = clipScratch.set(rx(x), ry(y), rz(z), 1f).mul(worldToClip)
        if (clip.w <= 0.05f) return false
        out.set((clip.x / clip.w * 0.5f + 0.5f) * width, (0.5f - clip.y / clip.w * 0.5f) * height)
        return true
    }

    internal fun begin(camera: CameraRenderState, tickDelta: Float) {
        cameraPos = camera.pos
        camX = cameraPos.x
        camY = cameraPos.y
        camZ = cameraPos.z
        camera.projectionMatrix.mul(camera.viewRotationMatrix, worldToClip)
        projectionReady = true
        this.tickDelta = tickDelta
        val view = RenderSystem.getModelViewMatrixCopy()
        view.positiveX(cameraRight)
        view.positiveY(cameraUp)
        batches.forEach(WorldBatch::clear)
    }

    internal fun flush() {
        if (batches.all(WorldBatch::isEmpty)) return

        val buffer = vertexBuffer
        for (batch in batches) {
            if (batch.isEmpty) continue
            val format = checkNotNull(batch.pipeline.getVertexFormatBinding(0)) { "${batch.pipeline} has no vertex binding 0" }
            val draw = buffer.appendDraw(format, batch.pipeline.primitiveTopology)
            batch.write(buffer.getVertexBuilder(draw))
            batch.draw = draw
            buffer.requestIndexCount(draw)
        }
        buffer.upload()
        RenderSystem.resizeAllAutoStorageIndexBuffers()

        WorldRenderPass.draw("Wyvern world") { pass ->
            for (batch in batches) {
                val info = batch.draw?.let(buffer::getExecuteInfo) ?: continue
                pass.setPipeline(RenderSystem.getCompiledPipeline(batch.pipeline))
                pass.setVertexBuffer(0, info.vertexBuffer().slice())
                pass.setIndexBuffer(info.indexBuffer(), info.indexType())
                pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0)
            }
        }
        buffer.endFrame()
        batches.forEach(WorldBatch::clear)
    }

    private fun solid(throughWalls: Boolean) = if (throughWalls) quadsNoDepth else quads

    private fun additive(throughWalls: Boolean) = if (throughWalls) additiveNoDepth else additive

    private fun rx(x: Double) = (x - camX).toFloat()
    private fun ry(y: Double) = (y - camY).toFloat()
    private fun rz(z: Double) = (z - camZ).toFloat()

    private fun horizontalOutline(box: AABB, y: Double, color: Color, width: Float, throughWalls: Boolean) {
        line(box.minX, y, box.minZ, box.maxX, y, box.minZ, color, width, throughWalls)
        line(box.maxX, y, box.minZ, box.maxX, y, box.maxZ, color, width, throughWalls)
        line(box.maxX, y, box.maxZ, box.minX, y, box.maxZ, color, width, throughWalls)
        line(box.minX, y, box.maxZ, box.minX, y, box.minZ, color, width, throughWalls)
    }

    private fun boxFaces(batch: WorldBatch, box: AABB, bottom: Int, top: Int) {
        val x0 = rx(box.minX); val y0 = ry(box.minY); val z0 = rz(box.minZ)
        val x1 = rx(box.maxX); val y1 = ry(box.maxY); val z1 = rz(box.maxZ)
        quad(batch, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, bottom, bottom, bottom)
        quad(batch, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top, top, top, top)
        quad(batch, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, bottom, top, top, bottom)
        quad(batch, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, bottom, bottom, top, top)
        quad(batch, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, bottom, bottom, top, top)
        quad(batch, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, bottom, top, top, bottom)
    }

    private fun quad(batch: WorldBatch, a: Vec3, b: Vec3, c: Vec3, d: Vec3, ca: Int, cb: Int, cc: Int, cd: Int) = quad(
        batch,
        rx(a.x), ry(a.y), rz(a.z), rx(b.x), ry(b.y), rz(b.z),
        rx(c.x), ry(c.y), rz(c.z), rx(d.x), ry(d.y), rz(d.z),
        ca, cb, cc, cd,
    )

    private fun quad(
        batch: WorldBatch,
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float,
        c0: Int, c1: Int, c2: Int, c3: Int,
    ) {
        batch.solid(x0, y0, z0, c0)
        batch.solid(x1, y1, z1, c1)
        batch.solid(x2, y2, z2, c2)
        batch.solid(x3, y3, z3, c3)
    }

    private fun segment(
        batch: WorldBatch,
        x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double,
        from: Int, to: Int, width: Float,
    ): Boolean {
        val ax = rx(x0); val ay = ry(y0); val az = rz(z0)
        val bx = rx(x1); val by = ry(y1); val bz = rz(z1)
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1.0e-6f) return false
        val nx = dx / len; val ny = dy / len; val nz = dz / len
        batch.line(ax, ay, az, from, nx, ny, nz, width)
        batch.line(bx, by, bz, to, nx, ny, nz, width)
        return true
    }

    private fun ribbonSide(
        x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double, px: Float, out: DoubleArray, at: Int,
    ) {
        val dist = maxOf(sqrt(x * x + y * y + z * z), 1.0E-6)
        val tx = -x / dist; val ty = -y / dist; val tz = -z / dist
        var sx = dy * tz - dz * ty; var sy = dz * tx - dx * tz; var sz = dx * ty - dy * tx
        var sq = sx * sx + sy * sy + sz * sz
        if (sq < 1.0E-12) {
            sx = -dz; sy = 0.0; sz = dx
            sq = sx * sx + sz * sz
            if (sq < 1.0E-12) { sx = 1.0; sy = 0.0; sz = 0.0; sq = 1.0 }
        }
        val k = maxOf(px * dist * RIBBON_PIXEL, 1.0E-6) / sqrt(sq)
        out[at] = sx * k; out[at + 1] = sy * k; out[at + 2] = sz * k
    }
}
