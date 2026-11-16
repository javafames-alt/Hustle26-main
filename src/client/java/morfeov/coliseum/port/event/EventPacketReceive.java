package morfeov.coliseum.port.event;
public final class EventPacketReceive {
    private final net.minecraft.network.protocol.Packet<?> packet; private boolean cancelled;
    public EventPacketReceive(net.minecraft.network.protocol.Packet<?> packet) { this.packet=packet; }
    public net.minecraft.network.protocol.Packet<?> getPacket() { return packet; }
    public void cancel() { cancelled=true; } public boolean cancelled() { return cancelled; }
}
