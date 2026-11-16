package im.yaHustler.mixin;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientboundMoveEntityPacket.class, ClientboundRotateHeadPacket.class})
public interface BotEntityPacketAccessor {
    @Accessor("entityId") int hustle263BotEntityId();
}
