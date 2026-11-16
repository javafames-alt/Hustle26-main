package im.yaHustler.wyvern.render.font.msdf

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec(x * s, y * s)
    operator fun unaryMinus() = Vec(-x, -y)
    fun dot(o: Vec) = x * o.x + y * o.y
    fun cross(o: Vec) = x * o.y - y * o.x
    fun length() = sqrt(x * x + y * y)
    fun isZero() = x == 0.0 && y == 0.0

    fun normalize(): Vec {
        val len = length()
        return if (len == 0.0) Vec(0.0, 1.0) else Vec(x / len, y / len)
    }

    fun orthonormal(): Vec {
        val len = length()
        return if (len == 0.0) Vec(0.0, -1.0) else Vec(y / len, -x / len)
    }

    fun mix(o: Vec, t: Double) = Vec(x + (o.x - x) * t, y + (o.y - y) * t)
}

private fun nonZeroSign(v: Double) = if (v > 0.0) 1.0 else -1.0

class SignedDistance(var distance: Double = -1e240, var dot: Double = 1.0) {
    fun closerThan(o: SignedDistance) =
        abs(distance) < abs(o.distance) || (abs(distance) == abs(o.distance) && dot < o.dot)
}

object EdgeColor {
    const val BLACK = 0
    const val RED = 1
    const val GREEN = 2
    const val YELLOW = 3
    const val BLUE = 4
    const val MAGENTA = 5
    const val CYAN = 6
    const val WHITE = 7
}

sealed class Edge(var color: Int = EdgeColor.WHITE) {
    abstract fun point(t: Double): Vec
    abstract fun direction(t: Double): Vec

    abstract fun signedDistance(origin: Vec, param: DoubleArray): SignedDistance

    abstract fun split(t: Double): Pair<Edge, Edge>

    abstract val points: List<Vec>

    fun splitInThirds(): List<Edge> {
        val (a, rest) = split(1.0 / 3.0)
        val (b, c) = rest.split(0.5)
        return listOf(a, b, c).onEach { it.color = color }
    }

    fun toPseudoDistance(distance: SignedDistance, origin: Vec, param: Double) {
        if (param < 0.0) {
            val dir = direction(0.0).normalize()
            val aq = origin - point(0.0)
            if (aq.dot(dir) < 0.0) {
                val pseudo = aq.cross(dir)
                if (abs(pseudo) <= abs(distance.distance)) {
                    distance.distance = pseudo
                    distance.dot = 0.0
                }
            }
        } else if (param > 1.0) {
            val dir = direction(1.0).normalize()
            val bq = origin - point(1.0)
            if (bq.dot(dir) > 0.0) {
                val pseudo = bq.cross(dir)
                if (abs(pseudo) <= abs(distance.distance)) {
                    distance.distance = pseudo
                    distance.dot = 0.0
                }
            }
        }
    }

    protected fun endpointResult(minDistance: Double, param: Double, qa: Vec, endToOrigin: Vec): SignedDistance =
        when {
            param in 0.0..1.0 -> SignedDistance(minDistance, 0.0)
            param < 0.5 -> SignedDistance(minDistance, abs(direction(0.0).normalize().dot(qa.normalize())))
            else -> SignedDistance(minDistance, abs(direction(1.0).normalize().dot(endToOrigin.normalize())))
        }
}

class LinearEdge(val p0: Vec, val p1: Vec, color: Int = EdgeColor.WHITE) : Edge(color) {
    override val points get() = listOf(p0, p1)
    override fun point(t: Double) = p0.mix(p1, t)
    override fun direction(t: Double) = p1 - p0

    override fun signedDistance(origin: Vec, param: DoubleArray): SignedDistance {
        val aq = origin - p0
        val ab = p1 - p0
        val t = aq.dot(ab) / ab.dot(ab)
        param[0] = t
        val eq = (if (t > 0.5) p1 else p0) - origin
        val endpointDistance = eq.length()
        if (t > 0.0 && t < 1.0) {
            val ortho = ab.orthonormal().dot(aq)
            if (abs(ortho) < endpointDistance) return SignedDistance(ortho, 0.0)
        }
        return SignedDistance(nonZeroSign(aq.cross(ab)) * endpointDistance, abs(ab.normalize().dot(eq.normalize())))
    }

    override fun split(t: Double): Pair<Edge, Edge> {
        val m = point(t)
        return LinearEdge(p0, m, color) to LinearEdge(m, p1, color)
    }
}

class QuadraticEdge(val p0: Vec, val p1: Vec, val p2: Vec, color: Int = EdgeColor.WHITE) : Edge(color) {
    override val points get() = listOf(p0, p1, p2)

    override fun point(t: Double) = p0.mix(p1, t).mix(p1.mix(p2, t), t)

    override fun direction(t: Double): Vec {
        val tangent = (p1 - p0).mix(p2 - p1, t)
        return if (tangent.isZero()) p2 - p0 else tangent
    }

    override fun signedDistance(origin: Vec, param: DoubleArray): SignedDistance {
        val qa = p0 - origin
        val ab = p1 - p0
        val br = p2 - p1 - ab
        val roots = DoubleArray(3)
        val count = solveCubic(roots, br.dot(br), 3.0 * ab.dot(br), 2.0 * ab.dot(ab) + qa.dot(br), qa.dot(ab))

        var epDir = direction(0.0)
        var minDistance = nonZeroSign(epDir.cross(qa)) * qa.length()
        var t = -qa.dot(epDir) / epDir.dot(epDir)
        val endToOrigin = p2 - origin
        run {
            epDir = direction(1.0)
            val distance = endToOrigin.length()
            if (distance < abs(minDistance)) {
                minDistance = nonZeroSign(epDir.cross(endToOrigin)) * distance
                t = (origin - p1).dot(epDir) / epDir.dot(epDir)
            }
        }
        for (i in 0 until count) {
            val root = roots[i]
            if (root > 0.0 && root < 1.0) {
                val qe = qa + ab * (2.0 * root) + br * (root * root)
                val distance = qe.length()
                if (distance <= abs(minDistance)) {
                    minDistance = nonZeroSign((ab + br * root).cross(qe)) * distance
                    t = root
                }
            }
        }
        param[0] = t
        return endpointResult(minDistance, t, qa, endToOrigin)
    }

    override fun split(t: Double): Pair<Edge, Edge> {
        val a = p0.mix(p1, t)
        val b = p1.mix(p2, t)
        val m = a.mix(b, t)
        return QuadraticEdge(p0, a, m, color) to QuadraticEdge(m, b, p2, color)
    }
}

class CubicEdge(val p0: Vec, val p1: Vec, val p2: Vec, val p3: Vec, color: Int = EdgeColor.WHITE) : Edge(color) {
    override val points get() = listOf(p0, p1, p2, p3)

    override fun point(t: Double): Vec {
        val p12 = p1.mix(p2, t)
        return p0.mix(p1, t).mix(p12, t).mix(p12.mix(p2.mix(p3, t), t), t)
    }

    override fun direction(t: Double): Vec {
        val tangent = (p1 - p0).mix(p2 - p1, t).mix((p2 - p1).mix(p3 - p2, t), t)
        if (!tangent.isZero()) return tangent
        if (t == 0.0) return p2 - p0
        if (t == 1.0) return p3 - p1
        return tangent
    }

    override fun signedDistance(origin: Vec, param: DoubleArray): SignedDistance {
        val qa = p0 - origin
        val ab = p1 - p0
        val br = p2 - p1 - ab
        val az = (p3 - p2) - (p2 - p1) - br

        var epDir = direction(0.0)
        var minDistance = nonZeroSign(epDir.cross(qa)) * qa.length()
        var best = -qa.dot(epDir) / epDir.dot(epDir)
        val endToOrigin = p3 - origin
        run {
            epDir = direction(1.0)
            val distance = endToOrigin.length()
            if (distance < abs(minDistance)) {
                minDistance = nonZeroSign(epDir.cross(endToOrigin)) * distance
                best = (epDir - endToOrigin).dot(epDir) / epDir.dot(epDir)
            }
        }
        for (i in 0..SEARCH_STARTS) {
            var t = i.toDouble() / SEARCH_STARTS
            var qe = qa + ab * (3.0 * t) + br * (3.0 * t * t) + az * (t * t * t)
            for (step in 0 until SEARCH_STEPS) {
                val d1 = ab * 3.0 + br * (6.0 * t) + az * (3.0 * t * t)
                val d2 = br * 6.0 + az * (6.0 * t)
                t -= qe.dot(d1) / (d1.dot(d1) + qe.dot(d2))
                if (t <= 0.0 || t >= 1.0) break
                qe = qa + ab * (3.0 * t) + br * (3.0 * t * t) + az * (t * t * t)
                val distance = qe.length()
                if (distance < abs(minDistance)) {
                    minDistance = nonZeroSign(direction(t).cross(qe)) * distance
                    best = t
                }
            }
        }
        param[0] = best
        return endpointResult(minDistance, best, qa, endToOrigin)
    }

    override fun split(t: Double): Pair<Edge, Edge> {
        val a = p0.mix(p1, t)
        val b = p1.mix(p2, t)
        val c = p2.mix(p3, t)
        val ab = a.mix(b, t)
        val bc = b.mix(c, t)
        val m = ab.mix(bc, t)
        return CubicEdge(p0, a, ab, m, color) to CubicEdge(m, bc, c, p3, color)
    }

    private companion object {
        const val SEARCH_STARTS = 4
        const val SEARCH_STEPS = 4
    }
}

class Contour(var edges: MutableList<Edge> = mutableListOf())

class Shape(val contours: List<Contour>) {
    fun bounds(): DoubleArray {
        var l = Double.MAX_VALUE
        var b = Double.MAX_VALUE
        var r = -Double.MAX_VALUE
        var t = -Double.MAX_VALUE
        for (contour in contours) for (edge in contour.edges) for (p in edge.points) {
            l = min(l, p.x); b = min(b, p.y); r = max(r, p.x); t = max(t, p.y)
        }
        return doubleArrayOf(l, b, r, t)
    }

    val isEmpty get() = contours.all { it.edges.isEmpty() }
}

private fun solveQuadratic(x: DoubleArray, a: Double, b: Double, c: Double): Int {
    if (a == 0.0 || abs(b) > 1e12 * abs(a)) {
        if (b == 0.0) return if (c == 0.0) -1 else 0
        x[0] = -c / b
        return 1
    }
    var dscr = b * b - 4.0 * a * c
    return when {
        dscr > 0.0 -> {
            dscr = sqrt(dscr)
            x[0] = (-b + dscr) / (2.0 * a)
            x[1] = (-b - dscr) / (2.0 * a)
            2
        }
        dscr == 0.0 -> {
            x[0] = -b / (2.0 * a)
            1
        }
        else -> 0
    }
}

private fun solveCubicNormed(x: DoubleArray, a0: Double, b: Double, c: Double): Int {
    var a = a0
    val a2 = a * a
    var q = (a2 - 3.0 * b) / 9.0
    val r = (a * (2.0 * a2 - 9.0 * b) + 27.0 * c) / 54.0
    val r2 = r * r
    val q3 = q * q * q
    a /= 3.0
    if (r2 < q3) {
        val t = acos((r / sqrt(q3)).coerceIn(-1.0, 1.0))
        q = -2.0 * sqrt(q)
        x[0] = q * cos(t / 3.0) - a
        x[1] = q * cos((t + 2.0 * Math.PI) / 3.0) - a
        x[2] = q * cos((t - 2.0 * Math.PI) / 3.0) - a
        return 3
    }
    val u = (if (r < 0.0) 1.0 else -1.0) * (abs(r) + sqrt(r2 - q3)).pow(1.0 / 3.0)
    val v = if (u == 0.0) 0.0 else q / u
    x[0] = (u + v) - a
    if (u == v || abs(u - v) < 1e-12 * abs(u + v)) {
        x[1] = -0.5 * (u + v) - a
        return 2
    }
    return 1
}

fun solveCubic(x: DoubleArray, a: Double, b: Double, c: Double, d: Double): Int {
    if (a != 0.0) {
        val bn = b / a
        if (abs(bn) < 1e6) return solveCubicNormed(x, bn, c / a, d / a)
    }
    return solveQuadratic(x, b, c, d)
}
