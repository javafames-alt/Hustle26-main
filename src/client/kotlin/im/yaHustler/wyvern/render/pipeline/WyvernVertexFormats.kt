package im.yaHustler.wyvern.render.pipeline

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.vertex.VertexFormat

object WyvernVertexFormats {
    val RECT: VertexFormat = VertexFormat.builder(0)
        .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
        .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
        .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
        .addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
        .addAttribute(DefaultVertexFormat.UV1_SEMANTIC_NAME, GpuFormat.RG16_SINT)
        .addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
        .build()

    val TEXT: VertexFormat = VertexFormat.builder(0)
        .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
        .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
        .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
        .addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
        .build()
}
