package im.yaHustler.wyvern.module.impl.render

import im.yaHustler.wyvern.event.AttackEntityEvent
import im.yaHustler.wyvern.event.EntityStatusEvent
import im.yaHustler.wyvern.event.TickEvent
import im.yaHustler.wyvern.event.on
import im.yaHustler.wyvern.module.Category
import im.yaHustler.wyvern.module.Module
import im.yaHustler.wyvern.render.particle.Particle
import im.yaHustler.wyvern.render.particle.ParticleManager
import im.yaHustler.wyvern.render.particle.ParticleShapes
import im.yaHustler.wyvern.ui.theme.ThemeColors
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object Particles : Module("Particles", "Частицы при ударе, тотеме, ходьбе и в мире", Category.RENDER) {
    private val triggers = multi(
        "Спавнить при", "Ударе", "Поп тотема", "Бездействие", "Ходьбе", "Эндер жемчуг", "Стрела",
        default = setOf("Ударе", "Поп тотема", "Ходьбе"), description = "Выбор условий спавна",
    )
    private val onHit by number("Спавнить при ударе", 15.0, 5.0..50.0, 1.0, "Частиц за удар") { "Ударе" in triggers }
    private val inWorld by number("Спавнить в мире", 20.0, 3.0..50.0, 1.0, "Частиц вокруг в бездействии") { "Бездействие" in triggers }
    private val elements = multi(
        "Элементы", *ParticleShapes.ALL.map { it.name }.toTypedArray(),
        default = setOf("Орбиз"), description = "Чем рисуются частицы",
    )

    private val random = Random()
    private val pendingPops = ArrayDeque<Entity>()
    private var idleTicks = 0
    private var shapesSource: Set<String>? = null
    private var shapesCache: List<ParticleShapes.Shape> = listOf(ParticleShapes.ORB)

    private val spawnable = setOf(
        Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.WATER, Blocks.LAVA, Blocks.GRASS_BLOCK, Blocks.TALL_GRASS,
        Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.VINE,
        Blocks.GLOW_LICHEN, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.SNOW, Blocks.COBWEB, Blocks.ICE, Blocks.PACKED_ICE,
        Blocks.BLUE_ICE, Blocks.FROSTED_ICE, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK,
    )

    fun activeShapes(): List<ParticleShapes.Shape> {
        val current = elements.value
        if (current != shapesSource) {
            shapesSource = current
            shapesCache = ParticleShapes.ALL
                .filter { shape -> current.any { it.equals(shape.name, ignoreCase = true) } }
                .ifEmpty { listOf(ParticleShapes.ORB) }
        }
        return shapesCache
    }

    fun glowEnabled() = enabled

    fun color(index: Int): Int =
        ThemeColors.wave(index).argb

    init {
        on<TickEvent.Pre> {
            val level = mc.level ?: return@on reset()
            idleTicks++
            if ("Поп тотема" in triggers) {
                while (pendingPops.isNotEmpty()) {
                    ParticleManager.emit(ParticleManager.TotemBurst(pendingPops.removeFirst(), 30))
                }
            } else {
                pendingPops.clear()
            }
            if ("Бездействие" in triggers) spawnIdleParticles()
            val pearls = "Эндер жемчуг" in triggers
            val arrows = "Стрела" in triggers
            if (pearls || arrows) {
                for (entity in level.entitiesForRendering()) {
                    if (pearls && entity is ThrownEnderpearl || arrows && entity is Arrow) {
                        spawnProjectileTrail(entity)
                    }
                }
            }
        }

        on<TickEvent.Post> {
            if ("Ходьбе" !in triggers) return@on
            val player = mc.player ?: return@on
            // walking particles surround your own body: from first person they only get in the way of the view
            if (mc.options.cameraType.isFirstPerson) return@on
            if (player.x == player.xo && player.y == player.yo && player.z == player.zo) return@on
            repeat(2) {
                val height = player.bbHeight * random.nextFloat()
                val speed = 2.0 * (1.0 + random.nextFloat())
                spawnFloating(
                    Vec3(player.xo + random.nextFloat(-0.5f, 0.5f), player.yo + height, player.zo + random.nextFloat(-0.5f, 0.5f)),
                    Vec3(
                        (player.deltaMovement.x + random.nextFloat(-0.1f, 0.1f)) * speed,
                        0.0,
                        (player.deltaMovement.z + random.nextFloat(-0.1f, 0.1f)) * speed,
                    ),
                    lifetimeMs = 800, color = color(ParticleManager.count * 50), size = random.nextFloat(0.1f, 0.225f),
                )
            }
        }

        on<AttackEntityEvent.Pre> { event ->
            if ("Ударе" !in triggers) return@on
            val target = event.target as? LivingEntity ?: return@on
            spawnRicochet(target)
        }

        on<EntityStatusEvent> { event ->
            if (event.id.toInt() == 35) pendingPops.addLast(event.entity)
        }
    }

    override fun onEnable() = reset()
    override fun onDisable() = reset()

    private fun reset() {
        pendingPops.clear()
        idleTicks = 0
    }

    private fun spawnRicochet(target: LivingEntity) {
        val life = ticksOf(3000)
        for (i in 0 until onHit.toInt()) {
            val args = Particle.Args(
                target.x + (random.nextDouble() - 0.5) * 0.5,
                target.y + random.nextDouble() * target.bbHeight,
                target.z + (random.nextDouble() - 0.5) * 0.5,
            )
                .velocity((random.nextDouble() * 0.5 - 0.25) * 0.9, random.nextDouble() * 0.25 * 0.01, (random.nextDouble() * 0.5 - 0.25) * 0.9)
                .physics(Particle.Physics.RICOCHET)
                .fade(Particle.Fade.LINEAR)
                .gravity(1f)
                .collide(true)
                .size(0.6f, 0.6f)
                .lifetime(life)
                .renderDistance(15.0)
            ParticleManager.spawn(args, color(i * 30), 0.15f, life)
        }
    }

    private fun spawnIdleParticles() {
        if (idleTicks % 15 != 0) return
        val camera = mc.gameRenderer.mainCamera().position()
        val speed = 3f
        repeat(inWorld.toInt() * 10) {
            val distance = random.nextFloat(1f, 30f).toDouble()
            val angle = Math.toRadians(random.nextFloat(0f, 360f).toDouble())
            spawnFloating(
                Vec3(camera.x - sin(angle) * distance, camera.y + random.nextFloat(-15f, 15f), camera.z + cos(angle) * distance),
                Vec3(random.nextFloat(-speed, speed).toDouble(), random.nextFloat(-1f, 1f).toDouble(), random.nextFloat(-speed, speed).toDouble()),
                lifetimeMs = 2500, color = color(ParticleManager.count * 50), size = random.nextFloat(0.1f, 0.225f),
            )
        }
        idleTicks = 0
    }

    private fun spawnProjectileTrail(entity: Entity) {
        if (entity.deltaMovement.horizontalDistance() < 0.35) return
        val pos = entity.position()
        val motion = entity.deltaMovement
        repeat(5) {
            val radius = random.nextFloat()
            spawnFloating(
                pos,
                Vec3(
                    random.nextFloat(-radius, radius) + motion.x,
                    random.nextFloat(-radius, radius) + motion.y,
                    random.nextFloat(-radius, radius) + motion.z,
                ),
                lifetimeMs = 1000, color = color(ParticleManager.count * 50), size = random.nextFloat(0.1f, 0.225f),
            )
        }
    }

    private fun spawnFloating(pos: Vec3, velocity: Vec3, lifetimeMs: Int, color: Int, size: Float) {
        val level = mc.level ?: return
        if (level.getBlockState(BlockPos.containing(pos)).block !in spawnable) return
        val life = ticksOf(lifetimeMs)
        val args = Particle.Args(pos.x, pos.y, pos.z)
            .velocity(velocity.x * 0.07, velocity.y * 0.07, velocity.z * 0.07)
            .gravity((WORLD_GRAVITY * 175.0).toFloat())
            .lifetime(life)
            .drag(0.98f)
            .collide(false)
            .size(max(0.02f, size), max(0.02f, size))
        ParticleManager.spawn(args, color, size, ticksOf(300))
    }

    private fun ticksOf(milliseconds: Int) = max(1, (milliseconds / 50f).roundToInt())
}

private const val WORLD_GRAVITY = 8.0e-6
