package morfeov.coliseum.bot.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import morfeov.coliseum.bot.net.BotConfigHandler;
import morfeov.coliseum.bot.net.BotConnection;
import morfeov.coliseum.bot.net.BotLoginHandler;
import morfeov.coliseum.bot.net.BotPlayHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.world.entity.player.Input;

public final class BotSession implements AutoCloseable {
    private final BotClientConfig config;
    private final ScheduledExecutorService executor;
    private volatile BotConnection connection;
    private volatile BotPlayHandler play;
    private volatile BotPhase phase = BotPhase.CONNECTING;
    private volatile String status = "Подключение";
    private volatile boolean closed;
    private volatile Input input = Input.EMPTY;
    private volatile GroupMovement groupMovement;
    private volatile boolean manualControl;

    record GroupMovement(UUID target, Input mirrorInput, float yaw, float pitch) {}

    public BotSession(BotClientConfig config) {
        this.config = config;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "Coliseum bot " + config.name());
            thread.setDaemon(true);
            return thread;
        });
    }
    public synchronized void connect() {
        if (closed || connection != null || phase != BotPhase.CONNECTING) throw new IllegalStateException("Бот уже запущен");
        phase = BotPhase.LOGIN;
        BotConnection.connect(config, this::configure, this::disconnected).whenComplete((transport, error) -> {
            if (error != null) { fail("Не удалось подключиться: " + rootMessage(error)); return; }
            synchronized (this) {
                if (closed) { transport.close(); transport.tick(); return; }
                connection = transport;
                executor.scheduleAtFixedRate(this::tick, 0, 50, TimeUnit.MILLISECONDS);
            }
        });
    }
    private ClientConfigurationPacketListener configure(Connection transport, BotLoginHandler.LoginState login) {
        phase = BotPhase.CONFIGURATION;
        status = "Загрузка конфигурации";
        play = null;
        return new BotConfigHandler(transport, login, (active, state) -> {
            var handler = new BotPlayHandler(active, state, this::configure, this::disconnected);
            play = handler;
            phase = BotPhase.PLAY;
            status = "Загрузка мира";
            return handler;
        }, this::disconnected);
    }
    private void tick() {
        try {
            var transport = connection;
            if (closed || transport == null) return;
            transport.tick();
            var handler = play;
            if (handler != null && handler.worldPackets().player().positioned()) {
                status = "В игре";
                GroupMovement movement = groupMovement;
                handler.tick(manualControl || movement == null ? input : groupInput(handler, movement));
            }
            if (!transport.connected()) closeTransport();
        } catch (Throwable error) { fail("Ошибка бота: " + rootMessage(error)); }
    }

    private static Input groupInput(BotPlayHandler handler, GroupMovement movement) {
        var target = handler.worldPackets().entities().playerMovement(movement.target());
        if (target == null) return Input.EMPTY;
        var player = handler.worldPackets().player();
        if (movement.mirrorInput() != null) {
            player.look(movement.yaw(), movement.pitch());
            return movement.mirrorInput();
        }
        var delta = target.position().subtract(player.snapshot().position());
        double distance = delta.horizontalDistance();
        if (distance < 2) return Input.EMPTY;
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        player.look(yaw, 0);
        return new Input(true, false, false, false, delta.y > .5 && distance < 4, false, distance > 6);
    }
    private void disconnected(DisconnectionDetails details) { fail(details.reason().getString()); }
    private synchronized void fail(String message) {
        status = message;
        closeTransport();
    }
    private synchronized void closeTransport() {
        closed = true;
        phase = BotPhase.DISCONNECTED;
        if (connection != null) connection.close();
        executor.shutdown();
    }
    private static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    public BotClientConfig config() { return config; }
    public BotPhase phase() { return phase; }
    public String status() { return status; }
    public BotPlayHandler play() { return play; }
    public boolean closed() { return closed; }
    public void input(Input value) { input = value; }
    public void manualControl(boolean enabled) {
        input = Input.EMPTY;
        manualControl = enabled;
    }
    void groupMovement(GroupMovement movement) { groupMovement = movement; }
    public void action(java.util.function.Consumer<BotPlayHandler> action) {
        if (closed) return;
        try { executor.execute(() -> { var handler = play; if (handler != null && !closed && connection.transport().getPacketListener() == handler) action.accept(handler); }); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }
    @Override public synchronized void close() {
        if (!closed) status = "Отключён";
        closeTransport();
    }
}
