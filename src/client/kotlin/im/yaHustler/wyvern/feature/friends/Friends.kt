package im.yaHustler.wyvern.feature.friends

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Stub of the original wyvern friends feature (only used for glow coloring).
 * The Hustle client has its own friend list, so wyvern-side glow never treats anyone as a friend.
 */
object Friends {
    fun isFriend(name: String): Boolean = false

    fun isFriend(entity: Entity): Boolean = entity is Player && isFriend(entity.gameProfile.name)
}
