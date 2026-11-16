package im.yaHustler.bot.net;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.KnownPacksManager;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public final class BotConfigHandler extends BotCommonHandler implements ClientConfigurationPacketListener {
    public record GameState(BotLoginHandler.LoginState login, RegistryAccess.Frozen registries,
                            FeatureFlagSet features, Map<Identifier, byte[]> cookies) {}
    @FunctionalInterface public interface GameFactory {
        ClientGamePacketListener create(Connection connection, GameState state);
    }
    private final BotLoginHandler.LoginState login;
    private final GameFactory next;
    private final RegistryDataCollector registries = new RegistryDataCollector();
    private FeatureFlagSet features = FeatureFlags.DEFAULT_FLAGS;
    private KnownPacksManager knownPacks;

    public BotConfigHandler(Connection connection, BotLoginHandler.LoginState login, GameFactory next, Consumer<DisconnectionDetails> disconnected) {
        super(connection, login.cookies(), disconnected);
        this.login = login;
        this.next = java.util.Objects.requireNonNull(next);
    }
    @Override public void handleRegistryData(ClientboundRegistryDataPacket packet) { registries.appendContents(packet.registry(), packet.entries()); }
    @Override public void handleUpdateTags(ClientboundUpdateTagsPacket packet) { registries.appendTags(packet.tags()); }
    @Override public void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket packet) { features = FeatureFlags.REGISTRY.fromNames(packet.features()); }
    @Override public void handleSelectKnownPacks(ClientboundSelectKnownPacks packet) {
        if (knownPacks == null) knownPacks = new KnownPacksManager();
        connection.send(new ServerboundSelectKnownPacks(knownPacks.trySelectingPacks(packet.knownPacks())));
    }
    @Override public void handleConfigurationFinished(ClientboundFinishConfigurationPacket packet) {
        if (!connection.isConnected() || connection.getPacketListener() != this) return;
        try {
            RegistryAccess.Frozen loaded;
            var base = ClientRegistryLayer.createRegistryAccess().compositeAccess();
            if (knownPacks == null) loaded = registries.collectGameRegistries(ResourceProvider.EMPTY, base, true);
            else try (var resources = knownPacks.createResourceManager()) { loaded = registries.collectGameRegistries(resources, base, true); }
            var listener = java.util.Objects.requireNonNull(next.create(connection, new GameState(login, loaded, features, Map.copyOf(cookies))));
            connection.setupInboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(loaded)), listener);
            connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
            connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(loaded), new GameProtocols.Context() {
                @Override public boolean hasInfiniteMaterials() { return false; }
                @Override public boolean canUseCommandBlocks() { return false; }
            }));
        } catch (RuntimeException e) {
            connection.disconnect(Component.literal("Не удалось загрузить конфигурацию мира бота"));
        }
    }
    @Override public void handleCodeOfConduct(ClientboundCodeOfConductPacket packet) {
        connection.disconnect(Component.literal("Сервер требует отдельного принятия правил"));
    }
    @Override public void handleResetChat(ClientboundResetChatPacket packet) {}
}
