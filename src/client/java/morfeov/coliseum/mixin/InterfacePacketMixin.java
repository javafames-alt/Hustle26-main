package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.hud.Notifications;
import morfeov.coliseum.hud.ServerTelemetry;
import morfeov.coliseum.module.impl.render.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class InterfacePacketMixin {
    @Inject(method="handleSetTime",at=@At("TAIL"))
    private void coliseum$serverTime(ClientboundSetTimePacket packet,CallbackInfo ci){ServerTelemetry.time(packet.gameTime());}
    @Inject(method="handleEntityEvent",at=@At("TAIL"))
    private void coliseum$totemPop(ClientboundEntityEventPacket packet,CallbackInfo ci){
        var mc=Minecraft.getInstance();var hud=Coliseum.MODULES.get(Hud.class);
        var angel=Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.TotemAngel.class);
        if(packet.getEventId()==35 && mc.level!=null && angel!=null) angel.pop(packet.getEntity(mc.level));
        if(packet.getEventId()!=35||mc.level==null||hud==null||!hud.enabled()||!hud.notifications.enabled()||!hud.totemWarnings.enabled())return;
        var entity=packet.getEntity(mc.level);if(entity!=null)Notifications.post(entity.getName().getString()+" использовал тотем",null);
    }
}
