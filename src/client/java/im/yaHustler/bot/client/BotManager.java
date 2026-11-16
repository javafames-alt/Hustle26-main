package im.yaHustler.bot.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

public final class BotManager {
    private static final BotManager instance = new BotManager();
    private final Map<String, BotSession> sessions = new LinkedHashMap<>();
    private ClientLevel groupLevel;
    private String targetName;
    private boolean mirror;
    public static BotManager instance() { return instance; }
    public synchronized BotSession find(String name) { return sessions.get(name.toLowerCase(Locale.ROOT)); }
    public synchronized List<BotSession> sessions() { return List.copyOf(sessions.values()); }
    public synchronized BotSession connect(BotClientConfig config) {
        var existing = find(config.name());
        if (existing != null && !existing.closed()) throw new IllegalStateException("Бот с этим ником уже подключён");
        var session = new BotSession(config);
        sessions.put(config.name().toLowerCase(Locale.ROOT), session);
        session.connect();
        return session;
    }
    public synchronized void disconnect(String name) {
        var session = find(name);
        if (session != null) session.close();
    }
    public synchronized void moveGroup(ClientLevel level, String target, boolean mirror) {
        if (level == null) throw new IllegalStateException("Команда доступна только в игре");
        if (!target.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Укажи ник игрока");
        stopGroup();
        groupLevel = level;
        targetName = target;
        this.mirror = mirror;
    }

    public synchronized void stopGroup() {
        groupLevel = null;
        targetName = null;
        sessions.values().forEach(session -> session.groupMovement(null));
    }

    public synchronized void tick(Minecraft client) {
        if (groupLevel == null) return;
        if (groupLevel != client.level) {
            stopGroup();
            return;
        }
        Player target = null;
        for (Player player : groupLevel.players()) {
            if (targetName.equalsIgnoreCase(player.getGameProfile().name())) {
                target = player;
                break;
            }
        }
        BotSession.GroupMovement movement = target == null ? null : new BotSession.GroupMovement(
            target.getUUID(), mirror ? mirrorInput(target) : null, target.getYRot(), target.getXRot());
        sessions.values().forEach(session -> session.groupMovement(movement));
    }

    private static Input mirrorInput(Player target) {
        if (target instanceof LocalPlayer local) return local.getLastSentInput();
        double x = target.getX() - target.xo, y = target.getY() - target.yo, z = target.getZ() - target.zo;
        double yaw = Math.toRadians(target.getYRot());
        double forward = Math.cos(yaw) * z - Math.sin(yaw) * x;
        double left = Math.cos(yaw) * x + Math.sin(yaw) * z;
        return new Input(forward > .015, forward < -.015, left > .015, left < -.015,
            !target.onGround() && y > .015, target.isCrouching(), target.isSprinting());
    }

    public synchronized void close() {
        stopGroup();
        sessions.values().forEach(BotSession::close);
        sessions.clear();
    }
}
