package im.yaHustler.wyvern.render.particle

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.textures.GpuTextureView
import im.yaHustler.wyvern.Wyvern
import im.yaHustler.wyvern.event.EventBus
import im.yaHustler.wyvern.event.Listenable
import im.yaHustler.wyvern.event.Listener
import im.yaHustler.wyvern.event.Render3DEvent
import im.yaHustler.wyvern.event.TickEvent
import im.yaHustler.wyvern.event.WorldLeaveEvent
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.module.impl.render.Particles
import im.yaHustler.wyvern.render.color.Color
import im.yaHustler.wyvern.render.pipeline.WorldPipelines
import im.yaHustler.wyvern.render.world.Renderer3D
import im.yaHustler.wyvern.render.world.WorldRenderPass
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.ArrayDeque
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min

object ParticleManager : Listenable {
    override val listeners = mutableListOf<Listener<*>>()

    private val entries = ArrayDeque<ParticleEntry>()
    private val emitters = ArrayDeque<Emitter>()
    private val sprites = HashMap<Identifier, SpriteBatch>()
    private val brokenTextures = HashSet<Identifier>()
    private val drawBuffer by lazy { StagedVertexBuffer({ "Wyvern particle sprites" }, 64 * 1024) }

    private val baseRight = Vector3f()
    private val baseUp = Vector3f()
    private val axis = Vector3f()
    private val spunRight = Vector3f()
    private val spunUp = Vector3f()
    private val spin = Quaternionf()

    private var drawViews = arrayOfNulls<GpuTextureView>(0)
    private var drawBatches = arrayOfNulls<StagedVertexBuffer.Draw>(0)

    private const val MAX_PARTICLES = 16384

    val count get() = entries.size

    fun init() {
        on<TickEvent.Pre> { tick() }
        on<Render3DEvent> { event -> render(event.tickDelta) }
        on<WorldLeaveEvent> { clear() }
        EventBus.subscribe(this)
    }

    fun spawn(args: Particle.Args, color: Int, size: Float = 0.2f, fadeTicks: Int = 6): Particle {
        val particle = Particle(args)
        val life = max(1, particle.lifetime)
        val fade = if (fadeTicks <= 0) max(1, life / 2) else fadeTicks.coerceIn(1, life)
        trimToLimit(1)
        val entry = ParticleEntry(particle, color, max(0.01f, size), fade)
        entry.alpha = alphaFor(particle, fade)
        entry.previousAlpha = entry.alpha
        entries.addLast(entry)
        return particle
    }

    fun emit(emitter: Emitter) {
        emitters.addLast(emitter)
    }

    fun clear() {
        entries.clear()
        emitters.clear()
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) return clear()

        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.particle.tick()
            if (!entry.particle.alive) {
                iterator.remove()
                continue
            }
            entry.previousAlpha = entry.alpha
            entry.alpha = alphaFor(entry.particle, entry.fadeTicks)
        }

        if (emitters.isNotEmpty()) {
            for (emitter in emitters.toList()) {
                try {
                    emitter.tick()
                } catch (_: Throwable) {
                    emitters.remove(emitter)
                }
            }
            emitters.removeIf { it.done }
        }

        val player = minecraft.player
        for (entry in entries) {
            val limit = entry.particle.renderDistanceSqr
            entry.visible = limit <= 0.0 || player == null ||
                player.distanceToSqr(entry.particle.x, entry.particle.y, entry.particle.z) <= limit
        }
    }

    private fun trimToLimit(additional: Int) {
        var excess = entries.size + additional - MAX_PARTICLES
        while (excess-- > 0) {
            entries.pollFirst()?.particle?.remove() ?: break
        }
    }

    private fun render(delta: Float) {
        for (batch in sprites.values) batch.vertices = 0
        if (entries.isEmpty()) return

        val shapes = Particles.activeShapes()
        val glow = Particles.glowEnabled()
        val camera = Renderer3D.cameraPos

        baseRight.set(Renderer3D.up)
        baseUp.set(Renderer3D.right).negate()
        baseRight.cross(baseUp, axis).normalize()

        for (entry in entries) {
            if (!entry.visible) continue
            val particle = entry.particle
            if (!particle.alive) continue

            var fade = entry.previousAlpha + (entry.alpha - entry.previousAlpha) * delta
            if (fade <= 0.001f) continue
            val scale = particle.scale(delta)
            if (scale <= 0.001f) continue

            if (particle.spins) {
                spin.setAngleAxis(particle.lerpRotation(delta), axis.x, axis.y, axis.z)
                spin.transform(spunRight.set(baseRight))
                spin.transform(spunUp.set(baseUp))
            } else {
                spunRight.set(baseRight)
                spunUp.set(baseUp)
            }

            val shape = shapes[min(shapes.size - 1, (entry.pick * shapes.size).toInt())]
            val batch = sprites.getOrPut(shape.texture) { SpriteBatch() }
            val size = entry.size * scale
            val half = size * 0.5f
            fade *= 0.75f
            val color = Color(entry.color)
            val shaded = color.withAlpha((color.alpha * fade).toInt().coerceIn(0, 255))
            val halo = shaded.withAlpha((128f * fade).toInt().coerceIn(0, 255))
            val centerX = (particle.lerpX(delta) - camera.x).toFloat() + spunUp.x * half
            val centerY = (particle.lerpY(delta) - camera.y).toFloat() + spunUp.y * half
            val centerZ = (particle.lerpZ(delta) - camera.z).toFloat() + spunUp.z * half

            // with the halo on, one quad HALO_SCALE times larger: quick_particle.fsh draws the halo around the sprite
            val quad = if (glow) size * WorldPipelines.PARTICLE_HALO_SCALE else size
            appendQuad(batch, centerX, centerY, centerZ, quad, packLight(shaded, halo))
        }
    }

    private fun appendQuad(batch: SpriteBatch, centerX: Float, centerY: Float, centerZ: Float, size: Float, color: Int) {
        batch.quad(
            centerX, centerY, centerZ,
            spunRight.x * size, spunRight.y * size, spunRight.z * size,
            spunUp.x * size, spunUp.y * size, spunUp.z * size,
            color,
        )
    }

    @JvmStatic
    fun flush() {
        if (sprites.values.none { it.vertices > 0 }) return

        val buffer = drawBuffer
        if (drawViews.size < sprites.size) {
            drawViews = arrayOfNulls(sprites.size)
            drawBatches = arrayOfNulls(sprites.size)
        }
        var drawCount = 0
        for ((texture, batch) in sprites) {
            if (batch.vertices == 0) continue
            val view = loadTextureView(texture) ?: continue

            val draw = buffer.appendDraw(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)
            val consumer = buffer.getVertexBuilder(draw)
            for (vertex in 0 until batch.vertices) {
                val index = vertex * 5
                consumer.addVertex(batch.data[index], batch.data[index + 1], batch.data[index + 2])
                    .setUv(batch.data[index + 3], batch.data[index + 4])
                    .setColor(batch.colors[vertex])
            }
            buffer.requestIndexCount(draw)
            drawViews[drawCount] = view
            drawBatches[drawCount] = draw
            drawCount++
        }
        if (drawCount == 0) return

        buffer.upload()
        RenderSystem.resizeAllAutoStorageIndexBuffers()

        val sampler = WorldRenderPass.linearSampler
        WorldRenderPass.draw("Wyvern particle sprites") { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(if (Particles.glowEnabled()) WorldPipelines.PARTICLE_HALO else WorldPipelines.PARTICLE))
            for (index in 0 until drawCount) {
                val info = buffer.getExecuteInfo(drawBatches[index]!!) ?: continue
                pass.setUniform("Sampler0", drawViews[index]!!, sampler)
                pass.setVertexBuffer(0, info.vertexBuffer().slice())
                pass.setIndexBuffer(info.indexBuffer(), info.indexType())
                pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0)
            }
        }
        buffer.endFrame()
    }

    private fun loadTextureView(texture: Identifier): GpuTextureView? {
        if (texture in brokenTextures) return null
        return try {
            Minecraft.getInstance().textureManager.getTexture(texture).textureView
        } catch (exception: Exception) {
            brokenTextures += texture
            Wyvern.logger.error("Particle texture {} failed to load", texture, exception)
            null
        }
    }

    private fun alphaFor(particle: Particle, fadeTicks: Int): Float {
        val life = max(1, particle.lifetime)
        val age = min(life, particle.age).toFloat()
        if (particle.fade == Particle.Fade.LINEAR) return (1f - age / life).coerceIn(0f, 1f)

        val fade = max(1, min(fadeTicks, life))
        val fadeStart = life - fade
        val fadeOut = if (age > fadeStart) (life - age) / fade else 1f
        val fadeInTicks = min(3f, fade.toFloat())
        val fadeIn = age / max(1f, fadeInTicks)
        val t = min(fadeIn, fadeOut).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun packLight(first: Color, second: Color = Color.TRANSPARENT): Int {
        val firstAlpha = first.alpha / 255f
        val secondAlpha = second.alpha / 255f
        val red = first.red * firstAlpha + second.red * secondAlpha
        val green = first.green * firstAlpha + second.green * secondAlpha
        val blue = first.blue * firstAlpha + second.blue * secondAlpha
        val intensity = maxOf(red, maxOf(green, blue)) / 2f
        if (intensity <= 0f) return 0

        val alpha = Math.round(intensity).coerceIn(1, 255)
        val scale = 255f / (alpha * 2f)
        val packedRed = Math.round(red * scale).coerceIn(0, 255)
        val packedGreen = Math.round(green * scale).coerceIn(0, 255)
        val packedBlue = Math.round(blue * scale).coerceIn(0, 255)
        return (alpha shl 24) or (packedRed shl 16) or (packedGreen shl 8) or packedBlue
    }

    abstract class Emitter {
        var done = false
            protected set

        abstract fun tick()
    }

    abstract class EntityBurst(protected val entity: Entity, private val ticks: Int, private val perTick: Int = 16) : Emitter() {
        private var ticked = 0

        override fun tick() {
            val random = ThreadLocalRandom.current()
            repeat(perTick) {
                val dx = random.nextDouble() * 2.0 - 1.0
                val dy = random.nextDouble() * 2.0 - 1.0
                val dz = random.nextDouble() * 2.0 - 1.0
                if (dx * dx + dy * dy + dz * dz > 1.0) return@repeat
                spawn(entity.getX(dx / 4.0), entity.getY(0.5 + dy / 4.0), entity.getZ(dz / 4.0), dx, dy + 0.2, dz)
            }
            if (++ticked >= ticks) done = true
        }

        protected abstract fun spawn(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double)
    }

    class TotemBurst(entity: Entity, ticks: Int) : EntityBurst(entity, ticks) {
        override fun spawn(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) {
            val random = ThreadLocalRandom.current()
            val life = 80 + random.nextInt(40)
            val size = random.nextFloat(0.2f, 0.25f)
            ParticleManager.spawn(
                Particle.Args(x, y, z)
                    .velocity(dx, dy, dz)
                    .gravity(0.225f)
                    .drag(0.75f)
                    .lifetime(life)
                    .collide(true)
                    .size(size, size),
                totemColor(random), size, max(1, life / 2),
            )
        }

        private fun totemColor(random: ThreadLocalRandom): Int {
            val gold = random.nextInt(4) == 0
            val red = if (gold) 0.6f + random.nextFloat() * 0.2f else 0.1f + random.nextFloat() * 0.2f
            val green = if (gold) 0.6f + random.nextFloat() * 0.3f else 0.4f + random.nextFloat() * 0.3f
            val blue = random.nextFloat() * 0.2f
            return Color((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()).argb
        }
    }
}

private class ParticleEntry(val particle: Particle, val color: Int, val size: Float, val fadeTicks: Int) {
    val pick = ThreadLocalRandom.current().nextFloat()
    var previousAlpha = 0f
    var alpha = 0f
    var visible = true
}

private class SpriteBatch {
    var data = FloatArray(4096)
    var colors = IntArray(1024)
    var vertices = 0

    fun quad(
        centerX: Float, centerY: Float, centerZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        color: Int,
    ) {
        ensure(4)
        val index = vertices * 5

        data[index] = centerX - rightX - upX
        data[index + 1] = centerY - rightY - upY
        data[index + 2] = centerZ - rightZ - upZ
        data[index + 3] = 0f
        data[index + 4] = 0f

        data[index + 5] = centerX + rightX - upX
        data[index + 6] = centerY + rightY - upY
        data[index + 7] = centerZ + rightZ - upZ
        data[index + 8] = 0f
        data[index + 9] = 1f

        data[index + 10] = centerX + rightX + upX
        data[index + 11] = centerY + rightY + upY
        data[index + 12] = centerZ + rightZ + upZ
        data[index + 13] = 1f
        data[index + 14] = 1f

        data[index + 15] = centerX - rightX + upX
        data[index + 16] = centerY - rightY + upY
        data[index + 17] = centerZ - rightZ + upZ
        data[index + 18] = 1f
        data[index + 19] = 0f

        colors[vertices] = color
        colors[vertices + 1] = color
        colors[vertices + 2] = color
        colors[vertices + 3] = color
        vertices += 4
    }

    private fun ensure(additional: Int) {
        val end = vertices + additional
        if (end * 5 > data.size) data = data.copyOf(maxOf(data.size * 2, end * 5))
        if (end > colors.size) colors = colors.copyOf(maxOf(colors.size * 2, end))
    }
}
