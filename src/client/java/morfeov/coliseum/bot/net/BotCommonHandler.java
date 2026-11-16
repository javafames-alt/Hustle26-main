package morfeov.coliseum.bot.net;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.resources.Identifier;

public abstract class BotCommonHandler implements ClientCommonPacketListener {
    protected final Connection connection;
    protected final Map<Identifier, byte[]> cookies;
    private final Consumer<DisconnectionDetails> disconnected;

    protected BotCommonHandler(Connection connection, Map<Identifier, byte[]> cookies, Consumer<DisconnectionDetails> disconnected) {
        this.connection = connection;
        this.cookies = new HashMap<>(cookies);
        this.disconnected = disconnected;
    }
    @Override public boolean isAcceptingMessages() { return connection.isConnected(); }
    @Override public void onDisconnect(DisconnectionDetails details) { disconnected.accept(details); }
    @Override public void onPacketError(net.minecraft.network.protocol.Packet packet, Exception error) {
        connection.disconnect(Component.literal("Ошибка пакета бота: " + packet.type()));
    }
    @Override public void handleKeepAlive(ClientboundKeepAlivePacket packet) { connection.send(new ServerboundKeepAlivePacket(packet.getId())); }
    @Override public void handlePing(ClientboundPingPacket packet) { connection.send(new ServerboundPongPacket(packet.getId())); }
    @Override public void handleDisconnect(ClientboundDisconnectPacket packet) { connection.disconnect(packet.reason()); }
    @Override public void handleRequestCookie(ClientboundCookieRequestPacket packet) {
        byte[] value = cookies.get(packet.key());
        connection.send(new ServerboundCookieResponsePacket(packet.key(), value == null ? null : value.clone()));
    }
    @Override public void handleStoreCookie(ClientboundStoreCookiePacket packet) { cookies.put(packet.key(), packet.payload().clone()); }
    @Override public void handleResourcePackPush(ClientboundResourcePackPushPacket packet) {
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DECLINED));
        if (packet.required()) connection.disconnect(Component.literal("Для бота пока не поддерживаются обязательные ресурспаки"));
    }
    @Override public void handleTransfer(ClientboundTransferPacket packet) { connection.disconnect(Component.literal("Перенаправление бота на другой сервер пока не поддерживается")); }
    @Override public void handleCustomPayload(ClientboundCustomPayloadPacket packet) {}
    @Override public void handleResourcePackPop(ClientboundResourcePackPopPacket packet) {}
    @Override public void handlePostEffects(ClientboundPostEffectsPacket packet) {}
    @Override public void handleCustomReportDetails(ClientboundCustomReportDetailsPacket packet) {}
    @Override public void handleServerLinks(ClientboundServerLinksPacket packet) {}
    @Override public void handleClearDialog(ClientboundClearDialogPacket packet) {}
    @Override public void handleShowDialog(ClientboundShowDialogPacket packet) {}
}
