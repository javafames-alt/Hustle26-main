package morfeov.coliseum.port;
import morfeov.coliseum.port.event.*;
public abstract class PortModule extends morfeov.coliseum.module.Module implements IMinecraft {
    protected PortModule() { super(); }
    protected PortModule(morfeov.coliseum.module.ModuleInfo metadata) { super(metadata); }
    public void onInput(EventInput event) {}
    public void onPostMotion(EventPostMotion event) {}
    public void onDisconnect(EventDisconnect event) {}
    public void onPacketReceive(EventPacketReceive event) {}
    public void onPacket(EventPacketReceive event) { onPacketReceive(event); }
    public void onPacketSend(EventPacketSend event) {}
    public void onMovePost(EventOnMovePost event) {}
    public void onAttack(EventAttackEntity event) {}
    public void onAttackSubmitted(EventAttackSubmitted event) {}
}
