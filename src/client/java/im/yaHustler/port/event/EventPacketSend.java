package im.yaHustler.port.event;
public final class EventPacketSend {
    private final net.minecraft.network.protocol.Packet<?> packet; private boolean cancelled;
    public EventPacketSend(net.minecraft.network.protocol.Packet<?> packet) { this.packet=packet; }
    public net.minecraft.network.protocol.Packet<?> getPacket() { return packet; }
    public void cancel() { cancelled=true; } public boolean cancelled() { return cancelled; }
}
