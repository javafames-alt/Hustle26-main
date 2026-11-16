package im.yaHustler.wyvern.render.pipeline

import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import im.yaHustler.wyvern.render.effect.Blur
import im.yaHustler.wyvern.render.font.MsdfFont
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.location
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.register
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.shader
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional
import kotlin.math.abs

object GuiPipelines {
    private const val BLUR_OFFSET = 1.5f
    private const val HEAD_RADIUS = 0.42f
    private const val MENU_ZOOM = 1.06f
    private const val MENU_SHIFT = 0.022f

    private val RECT_SNIPPET = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withVertexShader(shader("rect"))
        .withFragmentShader(shader("rect"))
        .withVertexBinding(0, WyvernVertexFormats.RECT)
        .buildSnippet()

    val RECT: RenderPipeline = register(
        RenderPipeline.builder(RECT_SNIPPET)
            .withLocation(location("rect"))
            .build()
    )

    val RECT_BLUR: RenderPipeline = register(
        RenderPipeline.builder(RECT_SNIPPET)
            .withLocation(location("rect_blur"))
            .withShaderDefine("BACKDROP_BLUR")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .build()
    )

    private val MSDF_TEXT = listOf(MsdfFont.RANGE, 10.0, 12.0).associateWith { range ->
        register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(location("msdf_text_${range.toInt()}"))
                .withVertexShader(shader("msdf_text"))
                .withFragmentShader(shader("msdf_text"))
                .withShaderDefine("PX_RANGE", range.toFloat())
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, WyvernVertexFormats.TEXT)
                .build()
        )
    }

    val ROUNDED_HEAD: RenderPipeline = register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(location("rounded_head"))
            .withFragmentShader(shader("rounded_head"))
            .withShaderDefine("RADIUS", HEAD_RADIUS)
            .build()
    )

    val KAWASE_DOWN: RenderPipeline = blurPass("kawase_down")
    val KAWASE_UP: RenderPipeline = blurPass("kawase_up")

    val MENU_BACKGROUND: RenderPipeline = register(
        RenderPipeline.builder()
            .withLocation(location("menu_background"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(shader("menu_background"))
            .withShaderDefine("ZOOM", MENU_ZOOM)
            .withShaderDefine("SHIFT", MENU_SHIFT)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MenuInfo", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
    )

    fun msdfText(range: Double): RenderPipeline =
        MSDF_TEXT[range] ?: MSDF_TEXT.minBy { abs(it.key - range) }.value

    internal fun init() = Unit

    private fun blurPass(name: String) = register(
        RenderPipeline.builder()
            .withLocation(location(name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(shader(name))
            .withShaderDefine("BLUR_OFFSET", BLUR_OFFSET)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState(Optional.empty(), Blur.FORMAT, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
    )
}
