package morfeov.coliseum.bot.net;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.login.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Crypt;

public final class BotLoginHandler implements ClientLoginPacketListener {
    public record LoginState(GameProfile profile, UUID sessionId, Map<Identifier, byte[]> cookies) {}
    @FunctionalInterface public interface ConfigurationFactory {
        ClientConfigurationPacketListener create(Connection connection, LoginState state);
    }
    private final Connection connection;
    private final ConfigurationFactory configuration;
    private final Consumer<DisconnectionDetails> disconnected;

    public BotLoginHandler(Connection connection, ConfigurationFactory configuration, Consumer<DisconnectionDetails> disconnected) {
        this.connection = java.util.Objects.requireNonNull(connection);
        this.configuration = java.util.Objects.requireNonNull(configuration);
        this.disconnected = java.util.Objects.requireNonNull(disconnected);
    }

    @Override public void handleHello(ClientboundHelloPacket packet) {
        if (packet.shouldAuthenticate()) {
            connection.disconnect(Component.literal("Сервер требует лицензионный аккаунт; бот использует офлайн-ник"));
            return;
        }
        try {
            var secret = Crypt.generateSecretKey();
            var publicKey = packet.getPublicKey();
            var decrypt = Crypt.getCipher(Cipher.DECRYPT_MODE, secret);
            var encrypt = Crypt.getCipher(Cipher.ENCRYPT_MODE, secret);
            connection.send(new ServerboundKeyPacket(secret, publicKey, packet.getChallenge()), future -> {
                if (future.isSuccess()) connection.setEncryptionKey(decrypt, encrypt);
                else connection.disconnect(Component.literal("Не удалось включить шифрование соединения бота"));
            });
        } catch (Exception e) {
            connection.disconnect(Component.literal("Некорректный запрос шифрования от сервера"));
        }
    }

    @Override public void handleLoginFinished(ClientboundLoginFinishedPacket packet) {
        if (!connection.isConnected() || connection.getPacketListener() != this) return;
        var listener = configuration.create(connection, new LoginState(packet.gameProfile(), packet.sessionId(), Map.of()));
        connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, java.util.Objects.requireNonNull(listener));
        connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
        connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
    }
    @Override public void handleDisconnect(ClientboundLoginDisconnectPacket packet) { connection.disconnect(packet.reason()); }
    @Override public void handleCompression(ClientboundLoginCompressionPacket packet) { connection.setupCompression(packet.getCompressionThreshold(), false); }
    @Override public void handleCustomQuery(ClientboundCustomQueryPacket packet) { connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), null)); }
    @Override public void handleRequestCookie(ClientboundCookieRequestPacket packet) { connection.send(new ServerboundCookieResponsePacket(packet.key(), null)); }
    @Override public void onDisconnect(DisconnectionDetails details) { disconnected.accept(details); }
    @Override public boolean isAcceptingMessages() { return connection.isConnected(); }
}
