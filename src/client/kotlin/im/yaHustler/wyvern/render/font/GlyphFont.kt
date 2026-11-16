package im.yaHustler.wyvern.render.font

interface GlyphFont {
    val rasterSize: Float
    val range: Double
    val ascender: Float
    val descender: Float

    fun glyph(codepoint: Int): MsdfFont.Glyph
    fun kerning(left: Int, right: Int): Float

    fun uploadPending()
}
