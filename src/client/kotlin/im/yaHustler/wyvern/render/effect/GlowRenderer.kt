package im.yaHustler.wyvern.render.effect

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import im.yaHustler.wyvern.feature.friends.Friends
import im.yaHustler.wyvern.module.impl.render.NameTags
import im.yaHustler.wyvern.render.pipeline.WorldPipelines
import im.yaHustler.wyvern.ui.theme.ThemeColors
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import org.joml.Vector4f
import java.util.Optional
import java.util.OptionalDouble

// NameTags' Glow ESP. Picked entities get an outline colour, so vanilla draws their silhouettes (depth-tested, never
// through walls) into the entity outline target; instead of vanilla's sobel line that mask is blurred here and
// composited as a halo in the theme's gradient.
object GlowRenderer {
    private const val RADIUS = 2
    private const val STRENGTH = 3f

    private val world = Chain("Wyvern glow")
    private val preview = Chain("Wyvern glow preview")
    private var previewTarget: RenderTarget? = null
    private var emptyDepth: RenderTarget? = null

    /** Render state of the ESP preview's entity, so its picture gets the glow too. */
    var previewState: EntityRenderState? = null

    private val sampler get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    private val espActive get() = NameTags.enabled && NameTags.glowTargets.value.isNotEmpty()

    /** Whether this frame's outline mask goes through the glow instead of vanilla's outline (Glow ESP or [Ghosts]). */
    @JvmStatic
    var active = false
        private set

    // latched once per frame, so ghosts expiring mid-frame can't hand vanilla a half-processed mask
    @JvmStatic
    fun beginFrame() {
        active = espActive
    }

    // white: glow_esp.fsh reads red = green = 0 as a ghost
    @JvmStatic
    fun outlineColor(entity: Entity, original: Int): Int {
        if (!espActive || entity === Minecraft.getInstance().player || !targets(entity)) return original
        return -1
    }

    fun targets(entity: Entity) = when {
        entity is Player -> (if (Friends.isFriend(entity)) "Друзья" else "Игроки") in NameTags.glowTargets
        entity is ItemEntity -> "Предметы" in NameTags.glowTargets
        entity is LivingEntity && entity !is ArmorStand -> "Мобы" in NameTags.glowTargets
        else -> false
    }

    /** Depth for vanilla's outline pass: the scene's while the glow is on, otherwise an empty one that never occludes. */
    @JvmStatic
    fun outlineDepth(): GpuTextureView? {
        val main = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        if (active) return main.depthTextureView
        val target = emptyDepth?.also { if (it.width != main.width || it.height != main.height) it.resize(main.width, main.height) }
            ?: TextureTarget("Wyvern glow depth", main.width, main.height, null, GpuFormat.D32_FLOAT).also { emptyDepth = it }
        return target.depthTextureView
    }

    @JvmStatic
    fun outlineClearDepth(original: OptionalDouble): OptionalDouble = if (active) original else OptionalDouble.of(0.0)

    @JvmStatic
    fun composite(mask: RenderTarget) {
        val output = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTextureView ?: return
        val view = mask.colorTextureView ?: return
        world.run(view, mask.width, mask.height, output, WorldPipelines.GLOW_ESP, null)
    }

    /** Glow for the ESP preview's entity picture: a texture of the same size to blit behind it, or null. */
    @JvmStatic
    fun previewGlow(state: EntityRenderState, mask: GpuTextureView): GpuTextureView? {
        if (state !== previewState) return null
        val width = mask.getWidth(0)
        val height = mask.getHeight(0)
        val target = previewTarget?.also { if (it.width != width || it.height != height) it.resize(width, height) }
            ?: TextureTarget("Wyvern glow preview", width, height, GpuFormat.RGBA8_UNORM, null).also { previewTarget = it }
        val output = target.colorTextureView ?: return null
        preview.run(mask, width, height, output, WorldPipelines.GLOW_ESP_PREVIEW, Vector4f(0f))
        return output
    }

    private class Chain(private val label: String) {
        private val levels = mutableListOf<RenderTarget>()
        private val info by lazy { MappableRingBuffer({ "$label info" }, 130, 64) }

        fun run(mask: GpuTextureView, width: Int, height: Int, output: GpuTextureView, pipeline: RenderPipeline, clear: Vector4f?) {
            ensureLevels(width, height)
            pass(WorldPipelines.GLOW_ESP_DOWN, mask, levels[0])
            for (i in 1 until RADIUS) pass(WorldPipelines.GLOW_ESP_DOWN, view(levels[i - 1]), levels[i])
            for (i in RADIUS - 1 downTo 1) pass(WorldPipelines.GLOW_ESP_UP, view(levels[i]), levels[i - 1])

            val first = ThemeColors.first
            val second = ThemeColors.second
            info.currentBuffer().map(false, true).use { buffer ->
                Std140Builder.intoBuffer(buffer.data())
                    .putVec4(first.redF, first.greenF, first.blueF, 1f)
                    .putVec4(second.redF, second.greenF, second.blueF, 1f)
                    .putVec4(STRENGTH, NameTags.glowFill.value.toFloat(), 0f, (System.currentTimeMillis() % 3_600_000L) / 1000f)
                    .putVec4(1f, 0f, 0f, 0f)
            }
            RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass({ label }, output, Optional.ofNullable(clear))
                .use { pass ->
                    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
                    pass.setUniform("GlowInfo", info.currentBuffer())
                    pass.setUniform("MaskSampler", mask, sampler)
                    pass.setUniform("GlowSampler", view(levels[0]), sampler)
                    pass.draw(3, 1, 0, 0)
                }
            info.rotate()
        }

        private fun pass(pipeline: RenderPipeline, input: GpuTextureView, output: RenderTarget) {
            RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass({ label }, view(output), Optional.empty())
                .use { pass ->
                    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
                    pass.setUniform("InSampler", input, sampler)
                    pass.draw(3, 1, 0, 0)
                }
        }

        private fun view(target: RenderTarget) = checkNotNull(target.colorTextureView)

        private fun ensureLevels(width: Int, height: Int) {
            for (i in 0 until RADIUS) {
                val w = (width shr (i + 1)).coerceAtLeast(1)
                val h = (height shr (i + 1)).coerceAtLeast(1)
                if (i < levels.size) {
                    val target = levels[i]
                    if (target.width != w || target.height != h) target.resize(w, h)
                } else {
                    levels += TextureTarget("$label $i", w, h, Blur.FORMAT, null)
                }
            }
        }
    }
}
