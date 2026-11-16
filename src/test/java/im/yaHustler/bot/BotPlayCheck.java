package im.yaHustler.bot;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import im.yaHustler.bot.net.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.flag.FeatureFlags;
import static org.junit.jupiter.api.Assertions.*;

final class BotPlayCheck {
    static void run() {
        var connection = new BotLoginCheck.TestConnection();
        var state = new BotConfigHandler.GameState(new BotLoginHandler.LoginState(new GameProfile(UUID.randomUUID(), "BotCheck"), UUID.randomUUID(), Map.of()), RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS, Map.of());
        var handler = new BotPlayHandler(connection, state, (transport, login) -> new BotConfigHandler(transport, login, (active, next) -> { throw new AssertionError(); }, details -> {}), details -> {});
        connection.listener = handler;
        handler.handleBundlePacket(new ClientboundBundlePacket(List.of(new ClientboundSetHealthPacket(5, 8, 1), new ClientboundSystemChatPacket(Component.literal("Hello bot"), false))));
        assertEquals(5, handler.worldPackets().player().vitals().health());
        assertEquals(List.of("Hello bot"), handler.chat());
        handler.selectSlot(5);
        assertEquals(5, handler.worldPackets().inventory().selectedSlot());
        assertEquals(5, ((ServerboundSetCarriedItemPacket) connection.sent.getLast()).getSlot());
        handler.sendChat("/help");
        assertEquals("help", ((ServerboundChatCommandPacket) connection.sent.getLast()).command());
        handler.sendChat("test");
        assertEquals("test", ((ServerboundChatPacket) connection.sent.getLast()).message());
        for (int i = 0; i < 120; i++) handler.handleSystemChat(new ClientboundSystemChatPacket(Component.literal("message " + i), false));
        assertEquals(100, handler.chat().size());
        connection.transitions.clear();
        handler.handleConfigurationStart(ClientboundStartConfigurationPacket.INSTANCE);
        assertEquals(List.of("inbound", "send", "outbound"), connection.transitions);
        assertInstanceOf(BotConfigHandler.class, connection.listener);
        assertSame(ServerboundConfigurationAcknowledgedPacket.INSTANCE, connection.sent.getLast());
        System.out.println("Bot play packet bundles, chat, hotbar and reconfiguration transition checks passed.");
    }
}
