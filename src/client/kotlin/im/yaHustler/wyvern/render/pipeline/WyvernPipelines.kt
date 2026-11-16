package im.yaHustler.wyvern.render.pipeline

import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import im.yaHustler.wyvern.Wyvern
import net.minecraft.client.renderer.RenderPipelines

object WyvernPipelines {
    val ADDITIVE = BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO)

    val DEPTH_TEST = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)
    val DEPTH_TEST_WRITE = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)

    fun init() {
        GuiPipelines.init()
        WorldPipelines.init()
    }

    internal fun location(name: String) = Wyvern.id("pipeline/$name")

    internal fun shader(name: String) = Wyvern.id("core/$name")

    internal fun register(pipeline: RenderPipeline): RenderPipeline = RenderPipelines.register(pipeline)
}
