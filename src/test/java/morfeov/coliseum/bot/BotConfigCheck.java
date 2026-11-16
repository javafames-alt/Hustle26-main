package morfeov.coliseum.bot;

import morfeov.coliseum.bot.client.BotClientConfig;
import morfeov.coliseum.bot.net.BotProxy;
import static org.junit.jupiter.api.Assertions.*;

public final class BotConfigCheck {
    public static void main(String[] args) {
        var config = BotClientConfig.offline("BotTest", "localhost", 25565, "");
        assertEquals(3, config.uuid().version());
        assertEquals(BotClientConfig.offlineUuid("BotTest"), config.uuid());
        assertNotEquals(config.uuid(), BotClientConfig.offlineUuid("BotOther"));
        assertThrows(IllegalArgumentException.class, () -> BotClientConfig.offline("bad name", "localhost", 25565, ""));
        assertThrows(IllegalArgumentException.class, () -> BotClientConfig.offline("BotTest", "localhost", 65536, ""));
        assertNull(BotProxy.parse(""));
        var socks = BotProxy.parse("127.0.0.1:1080:user:password");
        assertEquals(BotProxy.Type.SOCKS5, socks.type());
        assertEquals("user", socks.username()); assertEquals("password", socks.password());
        assertFalse(socks.toString().contains("password"));
        assertTrue(socks.createHandler() instanceof io.netty.handler.proxy.Socks5ProxyHandler);
        var ipv6 = BotProxy.parse("[::1]:1080");
        assertEquals("::1", ipv6.host()); assertEquals(1080, ipv6.port());
        var http = BotProxy.parse("http://user:password@localhost:8080");
        assertTrue(http.createHandler() instanceof io.netty.handler.proxy.HttpProxyHandler);
        assertThrows(IllegalArgumentException.class, () -> BotProxy.parse("localhost:0"));
        assertThrows(IllegalArgumentException.class, () -> BotProxy.parse("ftp://localhost:1080"));
        System.out.println("Bot offline identity and proxy configuration checks passed.");
        BotLoginCheck.run();
    }
}
