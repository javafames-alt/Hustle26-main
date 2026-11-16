package im.yaHustler.bot;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import im.yaHustler.bot.net.BotLoginHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.login.*;
import net.minecraft.resources.Identifier;
import static org.junit.jupiter.api.Assertions.*;

final class BotLoginCheck {
    static final class TestConnection extends Connection {
        final List<Packet<?>> sent = new ArrayList<>();
        final List<String> transitions = new ArrayList<>();
        PacketListener listener;
        Component reason;
        int compression = -1;
        TestConnection() { super(PacketFlow.CLIENTBOUND); }
        @Override public void send(Packet<?> packet) { sent.add(packet); transitions.add("send"); }
        @Override public boolean isConnected() { return reason == null; }
        @Override public PacketListener getPacketListener() { return listener; }
        @Override public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T next) { transitions.add("inbound"); listener = next; }
        @Override public void setupOutboundProtocol(ProtocolInfo<?> protocol) { transitions.add("outbound"); }
        @Override public void setupCompression(int threshold, boolean validate) { compression = threshold; }
        @Override public void disconnect(Component reason) { this.reason = reason; }
    }

    static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        BotWorldCheck.run();
        BotInventoryCheck.run();
        BotEntitiesCheck.run();
        BotMovementCheck.run();
        BotPlayCheck.run();
        checkTeleport();
        checkWorldPackets();
        var first = new TestConnection();
        var second = new TestConnection();
        ClientConfigurationPacketListener configuration = (ClientConfigurationPacketListener) Proxy.newProxyInstance(
            BotLoginCheck.class.getClassLoader(), new Class<?>[]{ClientConfigurationPacketListener.class}, (object, method, args) -> null);
        var expected = new GameProfile(UUID.randomUUID(), "OfflineBot");
        var sessionId = UUID.randomUUID();
        var handler = new BotLoginHandler(first, (connection, state) -> {
            assertSame(first, connection); assertEquals(expected, state.profile()); assertEquals(sessionId, state.sessionId());
            assertTrue(state.cookies().isEmpty()); return configuration;
        }, details -> {});
        first.listener = handler;
        handler.handleCompression(new ClientboundLoginCompressionPacket(256));
        assertEquals(256, first.compression); assertEquals(-1, second.compression);
        handler.handleCustomQuery(new ClientboundCustomQueryPacket(42, null));
        var answer = (ServerboundCustomQueryAnswerPacket) first.sent.getLast();
        assertEquals(42, answer.transactionId()); assertNull(answer.payload());
        var key = Identifier.fromNamespaceAndPath("test", "cookie");
        handler.handleRequestCookie(new ClientboundCookieRequestPacket(key));
        var cookie = (ServerboundCookieResponsePacket) first.sent.getLast();
        assertEquals(key, cookie.key()); assertNull(cookie.payload());
        first.transitions.clear();
        handler.handleLoginFinished(new ClientboundLoginFinishedPacket(expected, sessionId));
        assertEquals(List.of("inbound", "send", "outbound"), first.transitions);
        assertSame(ServerboundLoginAcknowledgedPacket.INSTANCE, first.sent.getLast());
        assertSame(configuration, first.listener); assertTrue(second.sent.isEmpty());
        handler.handleHello(new ClientboundHelloPacket("", new byte[0], new byte[0], true));
        assertNotNull(first.reason); assertNull(second.reason);
        assertFalse(handler.isAcceptingMessages());
        checkConfiguration(expected, sessionId);
        System.out.println("Bot login packets, configuration transition and connection isolation checks passed.");
    }

    private static void checkWorldPackets() {
        var connection = new TestConnection();
        var packets = new im.yaHustler.bot.net.BotWorldPackets(connection, net.minecraft.core.RegistryAccess.EMPTY);
        var other = new im.yaHustler.bot.net.BotWorldPackets(new TestConnection(), net.minecraft.core.RegistryAccess.EMPTY);
        assertTrue(packets.handle(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(8, 10, 1)));
        assertTrue(packets.handle(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(3)));
        assertEquals(8, packets.player().vitals().health());
        assertEquals(3, packets.inventory().selectedSlot());
        assertEquals(20, other.player().vitals().health());
        assertEquals(0, other.inventory().selectedSlot());
        assertTrue(connection.sent.isEmpty());
    }

    private static void checkConfiguration(GameProfile profile, UUID sessionId) {
        var connection = new TestConnection();
        var otherConnection = new TestConnection();
        var login = new BotLoginHandler.LoginState(profile, sessionId, java.util.Map.of());
        var handler = new im.yaHustler.bot.net.BotConfigHandler(connection, login, (transport, state) -> {
            throw new AssertionError("Game factory must not be called before configuration finishes");
        }, details -> {});
        var other = new im.yaHustler.bot.net.BotConfigHandler(otherConnection, login, (transport, state) -> null, details -> {});
        handler.handleKeepAlive(new net.minecraft.network.protocol.common.ClientboundKeepAlivePacket(1234));
        assertEquals(1234, ((net.minecraft.network.protocol.common.ServerboundKeepAlivePacket) connection.sent.getLast()).getId());
        handler.handlePing(new net.minecraft.network.protocol.common.ClientboundPingPacket(42));
        assertEquals(42, ((net.minecraft.network.protocol.common.ServerboundPongPacket) connection.sent.getLast()).getId());
        var key = Identifier.fromNamespaceAndPath("test", "isolated");
        byte[] value = {1, 2, 3};
        handler.handleStoreCookie(new net.minecraft.network.protocol.common.ClientboundStoreCookiePacket(key, value));
        value[0] = 9;
        handler.handleRequestCookie(new ClientboundCookieRequestPacket(key));
        assertArrayEquals(new byte[]{1, 2, 3}, ((ServerboundCookieResponsePacket) connection.sent.getLast()).payload());
        other.handleRequestCookie(new ClientboundCookieRequestPacket(key));
        assertNull(((ServerboundCookieResponsePacket) otherConnection.sent.getLast()).payload());
        System.out.println("Bot configuration keep-alive, ping and isolated cookie storage checks passed.");
    }

    private static void checkTeleport() {
        var connection = new TestConnection();
        var player = new im.yaHustler.bot.world.BotPlayerState();
        var movement = new net.minecraft.world.entity.PositionMoveRotation(new net.minecraft.world.phys.Vec3(10, 64, -5), net.minecraft.world.phys.Vec3.ZERO, 30, 10);
        player.teleport(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(7, movement, java.util.Set.of()), connection);
        assertTrue(player.positioned());
        assertEquals(movement, player.snapshot());
        assertEquals(2, connection.sent.size());
        var ack = (net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket) connection.sent.getFirst();
        assertEquals(7, ack.id()); assertEquals(64, ack.y());
        var delta = new net.minecraft.world.entity.PositionMoveRotation(new net.minecraft.world.phys.Vec3(2, 1, -3), net.minecraft.world.phys.Vec3.ZERO, 5, -2);
        player.teleport(new net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket(8, delta, java.util.Set.of(net.minecraft.world.entity.Relative.X, net.minecraft.world.entity.Relative.Y, net.minecraft.world.entity.Relative.Z)), connection);
        assertEquals(new net.minecraft.world.phys.Vec3(12, 65, -8), player.snapshot().position());
        assertEquals(3, connection.sent.size());
        player.reset(); assertFalse(player.positioned());
        System.out.println("Bot teleport acknowledgement, relative coordinates and first-load checks passed.");
    }
}
