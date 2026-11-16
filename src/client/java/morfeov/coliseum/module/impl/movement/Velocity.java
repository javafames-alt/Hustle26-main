package morfeov.coliseum.module.impl.movement;

import java.util.ArrayDeque;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;

@ModuleInfo(id="velocity", name="Velocity", category=Category.MOVEMENT,
    description="Задерживает входящие пакеты после отбрасывания в воздухе")
public final class Velocity extends PortModule {
    private static Velocity instance;
    public final NumberSetting delay = add(new NumberSetting("Задержка, мс",300,50,1000,50));
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private boolean buffering;
    private boolean active;
    private long started, pauseUntil;
    public Velocity() { instance=this; }
    public static boolean receive(Packet<?> packet, PacketListener listener) {
        Velocity module=instance;
        return module!=null && module.capture(packet,listener);
    }
    private synchronized boolean capture(Packet<?> packet, PacketListener listener) {
        if (!active) return false;
        if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
            for (Packet<?> child:bundle.subPackets()) {
                if (!capture(child,listener)) {
                    Pending entry=new Pending(child,listener);
                    mc.execute(()-> { if(mc.getConnection()==listener)apply(entry); });
                }
            }
            return true;
        }
        if (packet instanceof ClientboundDisconnectPacket) { clear(); return false; }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            flush(); pauseUntil=System.nanoTime()+2_000_000_000L; return false;
        }
        if (mc.player==null || mc.level==null || listener!=mc.getConnection()) { clear(); return false; }
        long now=System.nanoTime();
        if (now<pauseUntil) return false;
        if (!buffering) {
            if (!(packet instanceof ClientboundSetEntityMotionPacket motion) || motion.id()!=mc.player.getId() || mc.player.onGround()) return false;
            buffering=true; started=now;
        }
        if (packet instanceof ClientboundSetHealthPacket || packet instanceof ClientboundSoundPacket
            || packet instanceof ClientboundSystemChatPacket) return false;
        queue.addLast(new Pending(packet,listener));
        if (queue.size()>=4096) flush();
        return true;
    }
    @Override public synchronized void tick(Minecraft client) {
        if (client.player==null || client.level==null) { clear(); return; }
        if (buffering && (client.player.onGround() || System.nanoTime()-started>=delay.get()*1_000_000L)) flush();
    }
    @Override protected synchronized void onEnable() { clear(); pauseUntil=0; active=true; }
    @Override protected synchronized void onDisable() { active=false; flush(); }
    @Override public synchronized void onDisconnect(EventDisconnect event) { clear(); }
    private void clear() { queue.clear(); buffering=false; }
    private void flush() {
        var pending=new ArrayDeque<>(queue); clear();
        mc.execute(() -> {
            for (Pending entry:pending) if (mc.getConnection()==entry.listener()) apply(entry);
        });
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void apply(Pending entry) { ((Packet)entry.packet()).handle(entry.listener()); }
    private record Pending(Packet<?> packet, PacketListener listener) {}
}
