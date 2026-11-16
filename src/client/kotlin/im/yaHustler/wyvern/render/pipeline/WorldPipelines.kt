package im.yaHustler.wyvern.render.pipeline

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import im.yaHustler.wyvern.render.effect.Blur
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.ADDITIVE
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.DEPTH_TEST
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.DEPTH_TEST_WRITE
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.location
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.register
import im.yaHustler.wyvern.render.pipeline.WyvernPipelines.shader
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional

object WorldPipelines {
    private val TRANSFORMED_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .buildSnippet()

    private val GLOW_SNIPPET = RenderPipeline.builder(TRANSFORMED_SNIPPET)
        .withVertexShader(shader("glow"))
        .withFragmentShader(shader("glow"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withCull(false)
        .buildSnippet()

    private val SMOOTH_LINE_SNIPPET = RenderPipeline.builder(TRANSFORMED_SNIPPET)
        .withVertexShader(shader("smooth_line"))
        .withFragmentShader(shader("smooth_line"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .buildSnippet()

    val QUADS = filled("world_quads", DEPTH_TEST)
    val QUADS_NO_DEPTH = filled("world_quads_no_depth", null)
    val ADDITIVE_QUADS = filled("world_additive_quads", DEPTH_TEST, ColorTargetState(BlendFunction.LIGHTNING), cull = false)
    val ADDITIVE_QUADS_NO_DEPTH = filled("world_additive_quads_no_depth", null, ColorTargetState(BlendFunction.LIGHTNING), cull = false)

    val LINES = lines("world_lines", DEPTH_TEST)
    val LINES_NO_DEPTH = lines("world_lines_no_depth", null)

    val GLOW: RenderPipeline = register(
        RenderPipeline.builder(GLOW_SNIPPET)
            .withLocation(location("world_glow"))
            .withDepthStencilState(DEPTH_TEST)
            .build()
    )

    val GLOW_NO_DEPTH: RenderPipeline = register(
        RenderPipeline.builder(GLOW_SNIPPET)
            .withLocation(location("world_glow_no_depth"))
            .withDepthStencilState(Optional.empty())
            .build()
    )

    val TRAIL_BAND: RenderPipeline = register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(location("trail_band"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .withDepthStencilState(DEPTH_TEST)
            .withCull(false)
            .build()
    )

    val TRAIL_LINE: RenderPipeline = register(
        RenderPipeline.builder(SMOOTH_LINE_SNIPPET)
            .withLocation(location("trail_line"))
            .withDepthStencilState(DEPTH_TEST)
            .build()
    )

    val CHINA_HAT = chinaHat("china_hat", ColorTargetState(BlendFunction.TRANSLUCENT))
    val CHINA_HAT_OUTLINE = chinaHat("china_hat_outline", ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))

    val CHINA_HAT_RIM: RenderPipeline = register(
        RenderPipeline.builder(SMOOTH_LINE_SNIPPET)
            .withLocation(location("china_hat_rim"))
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 0f, 5f))
            .build()
    )

    val JUMP_CIRCLE = jumpCircle("jump_circle", "jump_circle", BlendFunction.OVERLAY)
    val JUMP_WARP = jumpCircle("jump_warp", "jump_warp", BlendFunction.TRANSLUCENT)

    val PARTICLE: RenderPipeline = particle("particle", halo = false)
    val PARTICLE_HALO: RenderPipeline = particle("particle_halo", halo = true)

    const val PARTICLE_HALO_SCALE = 4f

    val GLOW_ESP_DOWN = glowBlur("glow_esp_down")
    val GLOW_ESP_UP = glowBlur("glow_esp_up")
    val GLOW_ESP = glowComposite("glow_esp", ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
    val GLOW_ESP_PREVIEW = glowComposite("glow_esp_preview", ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))

    internal fun init() = Unit

    private fun particle(name: String, halo: Boolean): RenderPipeline {
        val builder = RenderPipeline.builder(TRANSFORMED_SNIPPET)
            .withLocation(location(name))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(shader("quick_particle"))
            .withFragmentShader(shader("quick_particle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState(ADDITIVE))
            .withDepthStencilState(DEPTH_TEST)
            .withCull(false)
        if (halo) builder.withShaderDefine("HALO_SCALE", PARTICLE_HALO_SCALE)
        return register(builder.build())
    }

    private fun glowBlur(name: String) = register(
        RenderPipeline.builder()
            .withLocation(location(name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(shader(name))
            .withShaderDefine("BLUR_OFFSET", 1.5f)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState(Optional.empty(), Blur.FORMAT, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
    )

    private fun glowComposite(name: String, color: ColorTargetState) = register(
        RenderPipeline.builder()
            .withLocation(location(name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(shader("glow_esp"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("GlowInfo", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(
                BindGroupLayout.builder()
                    .withUniform("MaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GlowSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .build()
            )
            .withColorTargetState(color)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()
    )

    private fun filled(name: String, depth: DepthStencilState?, color: ColorTargetState? = null, cull: Boolean = true): RenderPipeline {
        val builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(location(name))
            .withDepthStencilState(Optional.ofNullable(depth))
        if (color != null) builder.withColorTargetState(color)
        if (!cull) builder.withCull(false)
        return register(builder.build())
    }

    private fun lines(name: String, depth: DepthStencilState?) = register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(location(name))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.ofNullable(depth))
            .build()
    )

    private fun chinaHat(name: String, color: ColorTargetState) = register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(location(name))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
            .withColorTargetState(color)
            .withDepthStencilState(DEPTH_TEST_WRITE)
            .withCull(false)
            .build()
    )

    private fun jumpCircle(name: String, fragment: String, blend: BlendFunction) = register(
        RenderPipeline.builder(TRANSFORMED_SNIPPET)
            .withLocation(location(name))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("JumpCircle", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(shader("jump_circle"))
            .withFragmentShader(shader(fragment))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState(blend))
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1f, 8f))
            .withCull(false)
            .build()
    )
}
