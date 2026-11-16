package im.yaHustler.wyvern.render.particle

import im.yaHustler.wyvern.Wyvern
import net.minecraft.resources.Identifier

object ParticleShapes {
    class Shape(val name: String, val texture: Identifier)

    private fun shape(name: String, file: String) = Shape(name, Wyvern.id("textures/quick/particles/$file.png"))

    val ORB = shape("Орбиз", "orb")

    val ALL = listOf(
        ORB,
        shape("Звезды", "star"),
        shape("Сердечки", "heart"),
        shape("Доллары", "dollar"),
        shape("Снежинки", "snowflake"),
        shape("Короны", "crown"),
        shape("Молнии", "lightning"),
        shape("Линии", "line"),
        shape("Кругляши", "point"),
        shape("Ромбы", "rhombus"),
        shape("Искры", "spark"),
    )
}
