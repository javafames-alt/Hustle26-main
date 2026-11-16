package im.yaHustler.wyvern.render.world

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import net.minecraft.client.Minecraft
import java.util.Optional
import java.util.OptionalDouble

object WorldRenderPass {
    val linearSampler: GpuSampler get() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    inline fun draw(label: String, block: (RenderPass) -> Unit): Boolean {
        val target = Minecraft.getInstance().gameRenderer.mainRenderTarget()
        val color = target.colorTextureView ?: return false
        val depth = if (target.hasDepth()) target.depthTextureView else null
        val transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy())
        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass({ label }, color, Optional.empty(), depth, OptionalDouble.empty())
            .use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", transforms)
                block(pass)
            }
        return true
    }
}
