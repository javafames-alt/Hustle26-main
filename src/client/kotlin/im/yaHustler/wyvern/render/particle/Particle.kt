package im.yaHustler.wyvern.render.particle

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Particle(args: Args) {
    enum class Physics { SWEPT, RICOCHET }

    enum class Fade { SMOOTH, LINEAR }

    class Args(val x: Double, val y: Double, val z: Double) {
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        var lifetime = 0
        var gravity = 0f
        var drag = 0.98f
        var bounce = 0f
        var collide = true
        var groundBoost = false
        var width = 0.2f
        var height = 0.2f
        var delay = 0
        var spread = false
        var spin = 0f
        var startScale = 1f; var midScale = 1f; var endScale = 1f; var midPoint = 0.2f
        var physics = Physics.SWEPT
        var fade = Fade.SMOOTH
        var renderDistance = 0.0

        fun velocity(x: Double, y: Double, z: Double) = apply { vx = x; vy = y; vz = z }
        fun lifetime(ticks: Int) = apply { lifetime = ticks }
        fun gravity(value: Float) = apply { gravity = value }
        fun drag(value: Float) = apply { drag = value }
        fun bounce(value: Float) = apply { bounce = Mth.clamp(value, 0f, 1.95f) }
        fun collide(value: Boolean) = apply { collide = value }
        fun groundBoost(value: Boolean) = apply { groundBoost = value }
        fun size(w: Float, h: Float) = apply { width = w; height = h }
        fun delay(ticks: Int) = apply { delay = max(0, ticks) }
        fun spread(value: Boolean) = apply { spread = value }
        fun spin(value: Float) = apply { spin = value }
        fun scale(start: Float, mid: Float, end: Float, midPoint: Float) =
            apply { startScale = start; midScale = mid; endScale = end; this.midPoint = midPoint }
        fun physics(value: Physics) = apply { physics = value }
        fun fade(value: Fade) = apply { fade = value }
        fun renderDistance(value: Double) = apply { renderDistance = value }
    }

    var prevX = 0.0; private set
    var prevY = 0.0; private set
    var prevZ = 0.0; private set
    var x = 0.0; private set
    var y = 0.0; private set
    var z = 0.0; private set
    private var vx = 0.0
    private var vy = 0.0
    private var vz = 0.0
    private var box = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    private var onGround = false
    private val collides = args.collide
    private var resting = false
    var removed = false
        private set

    private var width = args.width
    private var height = args.height
    var age = 0
        private set
    val lifetime: Int = if (args.lifetime <= 0) (4f / (ThreadLocalRandom.current().nextFloat() * 0.9f + 0.1f)).toInt() else args.lifetime
    private val gravity = args.gravity
    private val drag = args.drag
    private var bounce = Mth.clamp(args.bounce, 0f, 1.95f)
    private var peakY = args.y
    private var rolling = false
    private val groundBoost = args.groundBoost
    private var rotation = 0f
    private var prevRotation = 0f
    private val spin = args.spin
    private val startScale = args.startScale
    private val midScale = args.midScale
    private val endScale = args.endScale
    private val midPoint = args.midPoint
    private val delay = args.delay
    val physics = args.physics
    val fade = args.fade
    val renderDistanceSqr = if (args.renderDistance <= 0.0) 0.0 else args.renderDistance * args.renderDistance

    init {
        setPos(args.x, args.y, args.z)
        prevX = x; prevY = y; prevZ = z
        if (args.spread) spreadVelocity(args.vx, args.vy, args.vz) else { vx = args.vx; vy = args.vy; vz = args.vz }
    }

    val alive get() = !removed

    fun remove() {
        removed = true
    }

    fun lerpX(delta: Float) = prevX + (x - prevX) * delta
    fun lerpY(delta: Float) = prevY + (y - prevY) * delta
    fun lerpZ(delta: Float) = prevZ + (z - prevZ) * delta
    fun lerpRotation(delta: Float) = prevRotation + (rotation - prevRotation) * delta
    val spins get() = spin != 0f
    val bounds get() = box

    fun scale(delta: Float): Float {
        val life = max(1, lifetime)
        val t = min(1f, max(0f, age - 1 + delta) / life)
        return if (t < midPoint) {
            midScale.let { startScale + (it - startScale) * smooth(t / max(1.0e-4f, midPoint)) }
        } else {
            midScale + (endScale - midScale) * smooth((t - midPoint) / max(1.0e-4f, 1f - midPoint))
        }
    }

    fun tick() {
        try {
            step()
        } catch (_: Throwable) {
            remove()
        }
    }

    private fun step() {
        if (removed) return
        prevX = x; prevY = y; prevZ = z
        prevRotation = rotation
        rotation += spin
        val ticked = age++
        if (ticked >= lifetime) return remove()
        if (ticked < delay) return
        if (resting) {
            vx = 0.0; vy = 0.0; vz = 0.0
            return
        }
        when (physics) {
            Physics.SWEPT -> swept()
            Physics.RICOCHET -> ricochet()
        }
    }

    private fun swept() {
        if (rolling) return roll()
        vy -= 0.04 * gravity
        move(vx, vy, vz)
        if (!onGround && y > peakY) peakY = y
        if (groundBoost && y == prevY) {
            vx *= 1.1
            vz *= 1.1
        }
        vx *= drag; vy *= drag; vz *= drag
        if (onGround) {
            vx *= 0.7
            vz *= 0.7
        }
    }

    private fun roll() {
        vy = -0.04 * gravity
        move(vx, vy, vz)
        if (onGround) vy = 0.0
        vx *= 0.986
        vz *= 0.986
        if (vx * vx + vz * vz < 4.0e-5) {
            resting = true
            vx = 0.0; vy = 0.0; vz = 0.0
        }
    }

    private fun move(dx0: Double, dy0: Double, dz0: Double) {
        if (resting) return
        var dx = dx0; var dy = dy0; var dz = dz0
        val level = Minecraft.getInstance().level
        if (collides && level != null && (dx != 0.0 || dy != 0.0 || dz != 0.0)) {
            if (dx * dx + dy * dy + dz * dz < MAX_COLLIDE_SQR && !freeSpace(box, dx, dy, dz)) {
                val moved = Entity.collideBoundingBox(CollisionContext.empty(), Vec3(dx, dy, dz), box, level, emptyList())
                dx = moved.x; dy = moved.y; dz = moved.z
            }
        }
        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            box = box.move(dx, dy, dz)
            x = (box.minX + box.maxX) / 2.0
            y = box.minY
            z = (box.minZ + box.maxZ) / 2.0
        }

        val hitY = dy0 != dy
        val hitSide = dx0 != dx || dz0 != dz
        val hitCeiling = hitY && dy0 > 0.0
        onGround = hitY && dy0 < 0.0
        val random = ThreadLocalRandom.current()
        if (dx0 != dx) {
            if (bounce > 0f) {
                vx = -dx0 * bounce
                bounce *= 0.9f
                val kick = abs(dx0) * 0.1
                vz += (random.nextDouble() * 2.0 - 1.0) * kick
                vy += random.nextDouble() * kick * 0.5
            } else {
                vx = 0.0
            }
        }
        if (dz0 != dz) {
            if (bounce > 0f) {
                vz = -dz0 * bounce
                bounce *= 0.9f
                val kick = abs(dz0) * 0.1
                vx += (random.nextDouble() * 2.0 - 1.0) * kick
                vy += random.nextDouble() * kick * 0.5
            } else {
                vz = 0.0
            }
        }
        if (hitY) {
            if (bounce <= 0f) {
                vy = 0.0
            } else if (onGround) {
                val fall = max(0.0, peakY - y)
                val up = sqrt(max(fall, 0.01)) * 0.2 * bounce
                bounce *= 0.9f
                peakY = y
                if (up > 0.055) {
                    vy = up
                    val kick = up * 0.1
                    vx += (random.nextDouble() * 2.0 - 1.0) * kick
                    vz += (random.nextDouble() * 2.0 - 1.0) * kick
                } else {
                    vy = 0.0
                    if (up > 0.01) {
                        val angle = random.nextDouble() * Math.PI * 2.0
                        vx += cos(angle) * up * 0.5
                        vz += sin(angle) * up * 0.5
                    }
                    rolling = true
                }
            } else if (hitCeiling) {
                vy = -abs(dy0) * bounce
                bounce *= 0.9f
            }
        }
        if (!rolling) {
            val speed = vx * vx + vy * vy + vz * vz
            if (onGround && !hitSide && abs(dy0) >= 1.0e-5 && abs(dy) < 1.0e-5 && speed <= 2.25e-4) {
                resting = true
                vx = 0.0; vy = 0.0; vz = 0.0
            } else if (speed > 2.25e-4) {
                resting = false
            }
        }
    }

    private fun ricochet() {
        reflect()
        vx *= 0.999; vy *= 0.999; vz *= 0.999
        setPos(x + vx, y + vy, z + vz)
        vx *= 0.9998
        vy -= 0.015 * gravity
        vz *= 0.9998
    }

    private fun reflect() {
        if (!collides) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (vx == 0.0 && vy == 0.0 && vz == 0.0) return
        val from = Vec3(x, y, z)
        val hit = level.clip(ClipContext(from, from.add(vx, vy, vz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) return
        val n = hit.direction.unitVec3i
        val dot = 2.0 * (vx * n.x + vy * n.y + vz * n.z)
        vx -= n.x * dot
        vy -= n.y * dot
        vz -= n.z * dot
    }

    private fun setPos(px: Double, py: Double, pz: Double) {
        x = px; y = py; z = pz
        val half = width / 2.0
        box = AABB(px - half, py, pz - half, px + half, py + height, pz + half)
    }

    private fun spreadVelocity(ax: Double, ay: Double, az: Double) {
        val random = ThreadLocalRandom.current()
        vx = ax + (random.nextDouble() * 2.0 - 1.0) * 0.4
        vy = ay + (random.nextDouble() * 2.0 - 1.0) * 0.4
        vz = az + (random.nextDouble() * 2.0 - 1.0) * 0.4
        val speed = (random.nextDouble() + random.nextDouble() + 1.0) * 0.15
        val length = sqrt(vx * vx + vy * vy + vz * vz)
        if (length < 1.0e-8) return
        vx = vx / length * speed * 0.4
        vy = vy / length * speed * 0.4 + 0.1
        vz = vz / length * speed * 0.4
    }

    companion object {
        private const val MAX_COLLIDE_SQR = 100.0 * 100.0

        private fun smooth(t: Float): Float {
            val k = t.coerceIn(0f, 1f)
            return k * k * (3f - 2f * k)
        }

        private fun freeSpace(box: AABB, dx: Double, dy: Double, dz: Double): Boolean {
            val level = Minecraft.getInstance().level ?: return false
            val x0 = Mth.floor(box.minX + min(dx, 0.0) - 1.0e-7) - 1
            val x1 = Mth.floor(box.maxX + max(dx, 0.0) + 1.0e-7) + 1
            val y0 = Mth.floor(box.minY + min(dy, 0.0) - 1.0e-7) - 1
            val y1 = Mth.floor(box.maxY + max(dy, 0.0) + 1.0e-7) + 1
            val z0 = Mth.floor(box.minZ + min(dz, 0.0) - 1.0e-7) - 1
            val z1 = Mth.floor(box.maxZ + max(dz, 0.0) + 1.0e-7) + 1
            val pos = BlockPos.MutableBlockPos()
            for (bx in x0..x1) {
                val ex = if (bx == x0 || bx == x1) 1 else 0
                for (by in y0..y1) {
                    val ey = ex + if (by == y0 || by == y1) 1 else 0
                    for (bz in z0..z1) {
                        val edges = ey + if (bz == z0 || bz == z1) 1 else 0
                        if (edges == 3) continue
                        val state = level.getBlockState(pos.set(bx, by, bz))
                        val blocked = when (edges) {
                            0 -> !state.isAir
                            1 -> state.hasLargeCollisionShape()
                            else -> state.`is`(Blocks.MOVING_PISTON)
                        }
                        if (blocked) return false
                    }
                }
            }
            return true
        }
    }
}
