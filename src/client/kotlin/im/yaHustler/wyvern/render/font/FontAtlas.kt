package im.yaHustler.wyvern.render.font

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.texture.DynamicTexture

class FontAtlas private constructor(
    private val texture: DynamicTexture,
    private val width: Int,
    private val height: Int,
    val range: Double,
) : AutoCloseable {
    constructor(label: String) : this(DynamicTexture({ label }, SIZE, SIZE, true), SIZE, SIZE, MsdfFont.RANGE)

    private val image: NativeImage get() = texture.pixels

    private var shelfX = PADDING
    private var shelfY = PADDING
    private var shelfHeight = 0
    private var dirty = false

    val textureSetup: TextureSetup = TextureSetup.singleTexture(
        texture.textureView,
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
    )

    fun insert(argb: IntArray, width: Int, height: Int): Pair<Int, Int>? {
        if (width + 2 * PADDING > this.width || height + 2 * PADDING > this.height) return null
        if (shelfX + width + PADDING > this.width) {
            shelfX = PADDING
            shelfY += shelfHeight + PADDING
            shelfHeight = 0
        }
        if (shelfY + height + PADDING > this.height) return null

        val x = shelfX
        val y = shelfY
        for (row in 0 until height) {
            for (col in 0 until width) image.setPixel(x + col, y + row, argb[row * width + col])
        }
        shelfX += width + PADDING
        shelfHeight = maxOf(shelfHeight, height)
        dirty = true
        return x to y
    }

    fun uploadIfDirty() {
        if (!dirty) return
        texture.upload()
        dirty = false
    }

    override fun close() = texture.close()

    companion object {
        const val SIZE = 1024

        fun prebuilt(label: String, image: NativeImage, range: Double): FontAtlas =
            FontAtlas(DynamicTexture({ label }, image), image.width, image.height, range).also { it.texture.upload() }
        private const val PADDING = 2
    }
}
