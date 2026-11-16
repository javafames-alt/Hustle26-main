package morfeov.coliseum.bot.net;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import morfeov.coliseum.bot.client.BotClientConfig;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;

public final class BotConnection implements AutoCloseable {
    private final Connection connection;
    private BotConnection(Connection connection) { this.connection = connection; }

    public static CompletableFuture<BotConnection> connect(BotClientConfig config, BotLoginHandler.ConfigurationFactory next,
                                                         Consumer<DisconnectionDetails> disconnected) {
        return CompletableFuture.supplyAsync(() -> {
            if (config.proxy() != null && !config.proxy().isBlank()) throw new IllegalArgumentException("Прокси ещё не подключён к транспорту ботов");
            if (config.protocolVersion() != BotClientConfig.NATIVE_PROTOCOL_VERSION) throw new IllegalArgumentException("Транспорт поддерживает только протокол текущего клиента");
            Connection connection = new Connection(PacketFlow.CLIENTBOUND);
            try {
                var address = ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress(config.host(), config.port()))
                    .orElseThrow(() -> new UnknownHostException(config.host()));
                Connection.connect(address.asInetSocketAddress(), EventLoopGroupHolder.remote(false), connection).syncUninterruptibly();
                var listener = new BotLoginHandler(connection, next, disconnected);
                connection.initiateServerboundPlayConnection(config.host(), config.port(), listener);
                connection.send(new ServerboundHelloPacket(config.name(), config.uuid()));
                return new BotConnection(connection);
            } catch (Exception e) {
                connection.disconnect(Component.literal("Не удалось подключить бота"));
                throw new CompletionException(e);
            }
        });
    }

    public void tick() {
        if (connection.isConnected()) connection.tick();
        else connection.handleDisconnection();
    }
    public boolean connected() { return connection.isConnected(); }
    public Connection transport() { return connection; }
    @Override public void close() { connection.disconnect(Component.literal("Бот отключён пользователем")); }
}
