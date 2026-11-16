package im.yaHustler.wyvern.render.font.msdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object MsdfGenerator {
    class Bitmap(val width: Int, val height: Int, val argb: IntArray)

    private class Seed(var value: Long = 0)

    private fun switchColor(color: Int, seed: Seed, banned: Int = EdgeColor.BLACK): Int {
        val combined = color and banned
        if (combined == EdgeColor.RED || combined == EdgeColor.GREEN || combined == EdgeColor.BLUE) {
            return combined xor EdgeColor.WHITE
        }
        if (color == EdgeColor.BLACK || color == EdgeColor.WHITE) {
            val start = intArrayOf(EdgeColor.CYAN, EdgeColor.MAGENTA, EdgeColor.YELLOW)
            val next = start[(seed.value % 3).toInt()]
            seed.value /= 3
            return next
        }
        val shifted = color shl (1 + (seed.value and 1).toInt())
        seed.value = seed.value shr 1
        return (shifted or (shifted shr 3)) and EdgeColor.WHITE
    }

    private fun symmetricalTrichotomy(position: Int, n: Int) =
        (3 + 2.875 * position / (n - 1) - 1.4375 + 0.5).toInt() - 3

    private fun isCorner(a: Vec, b: Vec, crossThreshold: Double) = a.dot(b) <= 0.0 || abs(a.cross(b)) > crossThreshold

    fun colorEdges(shape: Shape, angleThreshold: Double = 3.0) {
        val crossThreshold = sin(angleThreshold)
        val seed = Seed()
        for (contour in shape.contours) {
            val edges = contour.edges
            if (edges.isEmpty()) continue
            val corners = mutableListOf<Int>()
            var prevDirection = edges.last().direction(1.0)
            for ((index, edge) in edges.withIndex()) {
                if (isCorner(prevDirection.normalize(), edge.direction(0.0).normalize(), crossThreshold)) corners += index
                prevDirection = edge.direction(1.0)
            }

            when {
                corners.isEmpty() -> edges.forEach { it.color = EdgeColor.WHITE }
                corners.size == 1 -> {
                    val c0 = switchColor(EdgeColor.WHITE, seed)
                    val c2 = switchColor(c0, seed)
                    val colors = intArrayOf(c0, EdgeColor.WHITE, c2)
                    val corner = corners[0]
                    if (edges.size >= 3) {
                        val m = edges.size
                        for (i in 0 until m) edges[(corner + i) % m].color = colors[1 + symmetricalTrichotomy(i, m)]
                    } else {
                        val parts = edges.flatMap { it.splitInThirds() }
                        val ordered = if (corner == 0) parts else parts.drop(3) + parts.take(3)
                        if (ordered.size >= 6) {
                            ordered.forEachIndexed { i, part -> part.color = colors[i / 2] }
                        } else {
                            ordered.forEachIndexed { i, part -> part.color = colors[i] }
                        }
                        contour.edges = ordered.toMutableList()
                    }
                }
                else -> {
                    val cornerCount = corners.size
                    var spline = 0
                    val start = corners[0]
                    val m = edges.size
                    var color = switchColor(EdgeColor.WHITE, seed)
                    val initialColor = color
                    for (i in 0 until m) {
                        val index = (start + i) % m
                        if (spline + 1 < cornerCount && corners[spline + 1] == index) {
                            spline++
                            color = switchColor(color, seed, if (spline == cornerCount - 1) initialColor else EdgeColor.BLACK)
                        }
                        edges[index].color = color
                    }
                }
            }
        }
    }

    fun generate(shape: Shape, width: Int, height: Int, originX: Double, originY: Double, range: Double): Bitmap {
        val edges = shape.contours.flatMap { it.edges }
        val polygons = flatten(shape)
        val size = width * height
        val red = DoubleArray(size)
        val green = DoubleArray(size)
        val blue = DoubleArray(size)
        val trueDistance = DoubleArray(size)
        val inside = BooleanArray(size)
        val param = DoubleArray(1)

        var agree = 0
        var disagree = 0
        for (j in 0 until height) for (i in 0 until width) {
            val p = Vec(originX + i + 0.5, originY - j - 0.5)
            var r = SignedDistance(); var rEdge: Edge? = null; var rParam = 0.0
            var g = SignedDistance(); var gEdge: Edge? = null; var gParam = 0.0
            var b = SignedDistance(); var bEdge: Edge? = null; var bParam = 0.0
            var closest = SignedDistance()

            for (edge in edges) {
                val d = edge.signedDistance(p, param)
                if (d.closerThan(closest)) closest = d
                if (edge.color and EdgeColor.RED != 0 && d.closerThan(r)) { r = d; rEdge = edge; rParam = param[0] }
                if (edge.color and EdgeColor.GREEN != 0 && d.closerThan(g)) { g = d; gEdge = edge; gParam = param[0] }
                if (edge.color and EdgeColor.BLUE != 0 && d.closerThan(b)) { b = d; bEdge = edge; bParam = param[0] }
            }
            rEdge?.toPseudoDistance(r, p, rParam)
            gEdge?.toPseudoDistance(g, p, gParam)
            bEdge?.toPseudoDistance(b, p, bParam)

            val k = j * width + i
            red[k] = r.distance
            green[k] = g.distance
            blue[k] = b.distance
            trueDistance[k] = closest.distance
            inside[k] = winding(polygons, p.x, p.y) != 0
            if ((closest.distance > 0) == inside[k]) agree++ else disagree++
        }

        val flip = if (disagree > agree) -1.0 else 1.0
        val out = IntArray(size)
        for (k in 0 until size) {
            val sdf = abs(trueDistance[k]) * if (inside[k]) 1.0 else -1.0
            var r = red[k] * flip
            var g = green[k] * flip
            var b = blue[k] * flip
            if ((median(r, g, b) > 0) != inside[k]) {
                r = sdf; g = sdf; b = sdf
            }
            out[k] = (encode(sdf, range) shl 24) or (encode(r, range) shl 16) or (encode(g, range) shl 8) or encode(b, range)
        }
        return Bitmap(width, height, out)
    }

    private fun median(a: Double, b: Double, c: Double) = max(min(a, b), min(max(a, b), c))

    private fun encode(distance: Double, range: Double) = ((distance / range + 0.5) * 255.0).roundToInt().coerceIn(0, 255)

    private fun flatten(shape: Shape): List<DoubleArray> = shape.contours.map { contour ->
        val points = ArrayList<Double>()
        for (edge in contour.edges) {
            val steps = if (edge is LinearEdge) 1 else 12
            for (s in 0 until steps) {
                val p = edge.point(s.toDouble() / steps)
                points += p.x
                points += p.y
            }
        }
        points.toDoubleArray()
    }

    private fun winding(polygons: List<DoubleArray>, x: Double, y: Double): Int {
        var winding = 0
        for (poly in polygons) {
            val n = poly.size / 2
            for (i in 0 until n) {
                val x0 = poly[i * 2]; val y0 = poly[i * 2 + 1]
                val x1 = poly[(i + 1) % n * 2]; val y1 = poly[(i + 1) % n * 2 + 1]
                if (y0 <= y) {
                    if (y1 > y && (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) > 0) winding++
                } else if (y1 <= y && (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) < 0) {
                    winding--
                }
            }
        }
        return winding
    }
}
