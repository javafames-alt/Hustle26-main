package morfeov.coliseum.port;

import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** A bounded, session-local movement queue; correction and disconnect always release it. */
public final class PacketBufferManager implements IMinecraft {
    public static final PacketBufferManager INSTANCE = new PacketBufferManager();
    private final Set<String> owners = new HashSet<>();
    private final ArrayDeque<Packet<?>> packets = new ArrayDeque<>();
    private Connection connection;
    private long acquired;
    private static final ThreadLocal<Boolean> silent = ThreadLocal.withInitial(() -> false);
    public static boolean sendingSilent() { return silent.get(); }
    public static void sendSilentPacket(Packet<?> packet) {
        if (mc.getConnection() == null) return;
        silent.set(true);
        try { mc.getConnection().send(packet); } finally { silent.remove(); }
    }
    public void acquire(String owner) { if (owners.add(owner) && owners.size() == 1) acquired=System.currentTimeMillis(); }
    public void release(String owner) { owners.remove(owner); if (owners.isEmpty()) flush(); }
    public boolean capture(Connection source, Packet<?> packet) {
        if (connection != null && source != connection) reset();
        connection=source;
        if (sendingSilent() || owners.isEmpty() || !(packet instanceof ServerboundMovePlayerPacket)) return false;
        if (packets.size() >= 100 || System.currentTimeMillis()-acquired > 1000) { owners.clear(); flush(); return false; }
        packets.add(packet);
        return true;
    }
    public void flush() {
        if (mc.getConnection() == null || mc.getConnection().getConnection() != connection) { packets.clear(); return; }
        while (!packets.isEmpty()) sendSilentPacket(packets.removeFirst());
    }
    public void reset() { packets.clear(); owners.clear(); connection=null; }
}
