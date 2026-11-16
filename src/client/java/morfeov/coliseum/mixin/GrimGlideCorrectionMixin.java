package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.movement.GrimGlide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class GrimGlideCorrectionMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void coliseum$grimGlideCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        // PacketUtils first schedules this handler on the client thread; observe the correction once there.
        if (client.isSameThread() && client.player != null && client.player.connection == (Object) this)
            GrimGlide.positionCorrected();
    }
}
