package im.yaHustler.wyvern.render.font

import im.yaHustler.wyvern.Wyvern
import im.yaHustler.wyvern.render.font.msdf.Contour
import im.yaHustler.wyvern.render.font.msdf.CubicEdge
import im.yaHustler.wyvern.render.font.msdf.Edge
import im.yaHustler.wyvern.render.font.msdf.LinearEdge
import im.yaHustler.wyvern.render.font.msdf.MsdfGenerator
import im.yaHustler.wyvern.render.font.msdf.QuadraticEdge
import im.yaHustler.wyvern.render.font.msdf.Shape
import im.yaHustler.wyvern.render.font.msdf.Vec
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor

class MsdfFont(val name: String, fontData: ByteArray) : GlyphFont, AutoCloseable {
    class Glyph(
        val advance: Float,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val atlas: FontAtlas?,
    )

    private class Pending(val codepoint: Int, val advance: Float, val shape: Shape?)

    private val memory: ByteBuffer = MemoryUtil.memAlloc(fontData.size).put(fontData).flip()
    private val face: FT_Face
    private val glyphs = Int2ObjectOpenHashMap<Glyph>()
    private val atlases = mutableListOf<FontAtlas>()
    private val hasKerning: Boolean

    override val rasterSize = RASTER_SIZE.toFloat()
    override val range = RANGE

    override val ascender: Float
    override val descender: Float
    val lineHeight: Float

    init {
        synchronized(FreeTypeUtil.LIBRARY_LOCK) {
            MemoryStack.stackPush().use { stack ->
                val faceBuffer = stack.mallocPointer(1)
                FreeTypeUtil.assertError(
                    FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0L, faceBuffer),
                    "Loading font $name",
                )
                face = FT_Face.create(faceBuffer.get())
            }
        }
        FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, 0, RASTER_SIZE), "Sizing font $name")

        val metrics = face.size()!!.metrics()
        ascender = metrics.ascender() / 64f
        descender = -metrics.descender() / 64f
        lineHeight = metrics.height() / 64f
        hasKerning = face.face_flags() and FreeType.FT_FACE_FLAG_KERNING.toLong() != 0L
    }

    override fun glyph(codepoint: Int): Glyph = glyphs[codepoint] ?: run {
        preload(intArrayOf(codepoint))
        glyphs[codepoint]
    }

    fun preload(codepoints: IntArray) {
        val pending = codepoints.distinct().filter { !glyphs.containsKey(it) }.map(::readOutline)
        val bitmaps = pending.parallelStream().map { p -> p.shape?.let(::render) }.toList()
        for ((p, bitmap) in pending.zip(bitmaps)) glyphs.put(p.codepoint, place(p, bitmap))
    }

    override fun kerning(left: Int, right: Int): Float {
        if (!hasKerning) return 0f
        MemoryStack.stackPush().use { stack ->
            val vector = FT_Vector.malloc(stack)
            val l = FreeType.FT_Get_Char_Index(face, left.toLong())
            val r = FreeType.FT_Get_Char_Index(face, right.toLong())
            if (FreeType.FT_Get_Kerning(face, l, r, FreeType.FT_KERNING_DEFAULT, vector) != 0) return 0f
            return vector.x() / 64f
        }
    }

    override fun uploadPending() = atlases.forEach(FontAtlas::uploadIfDirty)

    private fun readOutline(codepoint: Int): Pending {
        var index = FreeType.FT_Get_Char_Index(face, codepoint.toLong())
        if (index == 0) index = FreeType.FT_Get_Char_Index(face, '?'.code.toLong())
        if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(face, index, LOAD_FLAGS), "Loading glyph $codepoint")) {
            return Pending(codepoint, 0f, null)
        }
        val slot = face.glyph()!!
        val advance = slot.advance().x() / 64f
        val outline = slot.outline()
        val pointCount = outline.n_points().toInt()
        if (pointCount == 0) return Pending(codepoint, advance, null)

        val points = outline.points()
        val tags = outline.tags()
        val ends = outline.contours()
        val contours = mutableListOf<Contour>()
        var first = 0
        for (c in 0 until outline.n_contours().toInt()) {
            val last = ends.get(c).toInt()
            val pts = (first..last).map { Vec(points.get(it).x() / 64.0, points.get(it).y() / 64.0) }
            val kinds = (first..last).map { tagKind(tags.get(it).toInt()) }
            val edges = contourEdges(pts, kinds)
            if (edges.isNotEmpty()) contours += Contour(edges)
            first = last + 1
        }
        val shape = Shape(contours)
        return Pending(codepoint, advance, if (shape.isEmpty) null else shape)
    }

    private fun tagKind(tag: Int) = when {
        tag and 1 != 0 -> ON
        tag and 2 != 0 -> CUBIC
        else -> CONIC
    }

    private fun contourEdges(points: List<Vec>, kinds: List<Int>): MutableList<Edge> {
        val n = points.size
        if (n < 2) return mutableListOf()
        val pts = ArrayList<Vec>()
        val ks = ArrayList<Int>()
        for (i in 0 until n) {
            pts += points[i]; ks += kinds[i]
            val next = (i + 1) % n
            if (kinds[i] == CONIC && kinds[next] == CONIC) {
                pts += points[i].mix(points[next], 0.5); ks += ON
            }
        }
        val start = ks.indexOf(ON)
        if (start < 0) return mutableListOf()
        val m = pts.size
        fun p(i: Int) = pts[(start + i) % m]
        fun k(i: Int) = ks[(start + i) % m]

        val edges = mutableListOf<Edge>()
        var i = 0
        while (i < m) {
            val from = p(i)
            when (k(i + 1)) {
                ON -> {
                    val to = p(i + 1)
                    if (from.x != to.x || from.y != to.y) edges += LinearEdge(from, to)
                    i += 1
                }
                CONIC -> {
                    edges += QuadraticEdge(from, p(i + 1), p(i + 2))
                    i += 2
                }
                else -> {
                    edges += CubicEdge(from, p(i + 1), p(i + 2), p(i + 3))
                    i += 3
                }
            }
        }
        return edges
    }

    private class Rendered(val bitmap: MsdfGenerator.Bitmap, val left: Int, val top: Int)

    private fun render(shape: Shape): Rendered {
        MsdfGenerator.colorEdges(shape)
        val (l, b, r, t) = shape.bounds().toList()
        val pad = (RANGE / 2 + 1).toInt()
        val left = floor(l).toInt() - pad
        val top = ceil(t).toInt() + pad
        val width = ceil(r).toInt() - floor(l).toInt() + 2 * pad
        val height = ceil(t).toInt() - floor(b).toInt() + 2 * pad
        return Rendered(MsdfGenerator.generate(shape, width, height, left.toDouble(), top.toDouble(), RANGE), left, top)
    }

    private fun place(pending: Pending, rendered: Rendered?): Glyph {
        if (rendered == null) return Glyph(pending.advance, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, null)
        val bitmap = rendered.bitmap
        var atlas = atlases.lastOrNull()
        var pos = atlas?.insert(bitmap.argb, bitmap.width, bitmap.height)
        if (pos == null) {
            atlas = FontAtlas("Wyvern font $name #${atlases.size}").also(atlases::add)
            pos = checkNotNull(atlas.insert(bitmap.argb, bitmap.width, bitmap.height)) { "Glyph ${pending.codepoint} larger than the atlas" }
        }
        val (ax, ay) = pos
        val size = FontAtlas.SIZE.toFloat()
        return Glyph(
            pending.advance,
            rendered.left.toFloat(), rendered.top.toFloat(),
            bitmap.width.toFloat(), bitmap.height.toFloat(),
            ax / size, ay / size, (ax + bitmap.width) / size, (ay + bitmap.height) / size,
            atlas,
        )
    }

    override fun close() {
        synchronized(FreeTypeUtil.LIBRARY_LOCK) { FreeType.FT_Done_Face(face) }
        MemoryUtil.memFree(memory)
        atlases.forEach(FontAtlas::close)
    }

    companion object {
        const val RASTER_SIZE = 48

        const val RANGE = 8.0

        private const val ON = 0
        private const val CONIC = 1
        private const val CUBIC = 2

        private val LOAD_FLAGS = FreeType.FT_LOAD_DEFAULT or FreeType.FT_LOAD_NO_BITMAP or FreeType.FT_LOAD_NO_HINTING

        fun load(name: String, data: ByteArray): MsdfFont? = runCatching { MsdfFont(name, data) }
            .onFailure { Wyvern.logger.error("Failed to load font $name", it) }
            .getOrNull()
    }
}
