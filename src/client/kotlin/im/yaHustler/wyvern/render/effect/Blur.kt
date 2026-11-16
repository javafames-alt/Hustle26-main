package im.yaHustler.wyvern.render.effect

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import im.yaHustler.wyvern.render.pipeline.GuiPipelines
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.TextureSetup
import java.util.Optional

object Blur {
    var strength = 4
        set(value) {
            field = value.coerceIn(1, MAX_LEVELS)
        }

    private const val MAX_LEVELS = 6

    val FORMAT: GpuFormat = GpuFormat.RGBA16_FLOAT

    private val levels = mutableListOf<RenderTarget>()
    private var requested = false

    private val sampler get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun textureSetup(): TextureSetup {
        if (!requested || setup == null) {
            requested = true
            ensureTargets()
            setup = TextureSetup.singleTexture(checkNotNull(levels[0].colorTextureView), sampler)
        }
        return checkNotNull(setup)
    }

    private var setup: TextureSetup? = null

    @JvmStatic
    fun process() {
        if (!requested) return
        requested = false

        val main = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val source = main.colorTextureView ?: return
        val count = strength.coerceAtMost(levels.size)

        pass(GuiPipelines.KAWASE_DOWN, source, levels[0])
        for (i in 1 until count) pass(GuiPipelines.KAWASE_DOWN, view(levels[i - 1]), levels[i])
        for (i in count - 1 downTo 1) pass(GuiPipelines.KAWASE_UP, view(levels[i]), levels[i - 1])
    }

    private fun pass(pipeline: RenderPipeline, input: GpuTextureView, output: RenderTarget) {
        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass({ "Wyvern blur" }, view(output), Optional.empty())
            .use { pass ->
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
                pass.setUniform("InSampler", input, sampler)
                pass.draw(3, 1, 0, 0)
            }
    }

    private fun view(target: RenderTarget) = checkNotNull(target.colorTextureView)

    private fun ensureTargets() {
        val main = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val width = main.width.coerceAtLeast(1)
        val height = main.height.coerceAtLeast(1)

        for (i in 0 until strength) {
            val w = (width shr (i + 1)).coerceAtLeast(1)
            val h = (height shr (i + 1)).coerceAtLeast(1)
            if (i < levels.size) {
                val target = levels[i]
                if (target.width != w || target.height != h) target.resize(w, h)
            } else {
                levels += TextureTarget("Wyvern blur $i", w, h, FORMAT, null)
            }
        }
    }
}
