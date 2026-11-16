package im.yaHustler.wyvern.event

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.render.world.Renderer3D
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3

sealed class TickEvent : Event() {
    data object Pre : TickEvent()
    data object Post : TickEvent()
}

class KeyEvent(val key: Int, val action: Int, val modifiers: Int) : CancellableEvent() {
    val isPress get() = action == InputConstants.PRESS
}

class Render2DEvent(val renderer: Renderer2D, val tickDelta: Float) : Event()

class Render3DEvent(val tickDelta: Float) : Event() {
    val renderer get() = Renderer3D
}

class RenderHandEvent(val pose: PoseStack, val arm: HumanoidArm) : Event()

sealed class PacketEvent(val packet: Packet<*>) : CancellableEvent() {
    class Send(packet: Packet<*>) : PacketEvent(packet)
    class Receive(packet: Packet<*>) : PacketEvent(packet)
}

class ChatSendEvent(val message: String) : CancellableEvent()

class WorldJoinEvent : Event()
class WorldLeaveEvent : Event()

class AttackBlockEvent(val pos: BlockPos, val direction: Direction) : CancellableEvent()

class BreakBlockEvent(val pos: BlockPos) : Event()

sealed class AttackEntityEvent(val target: Entity) : Event() {
    class Pre(target: Entity) : AttackEntityEvent(target)
    class Post(target: Entity) : AttackEntityEvent(target)
}

class EntityStatusEvent(val entity: Entity, val id: Byte) : Event()

class JumpEvent : Event()

class MoveEvent(var movement: Vec3) : Event()

