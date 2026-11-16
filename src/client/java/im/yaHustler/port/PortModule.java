package im.yaHustler.port;
import im.yaHustler.port.event.*;
public abstract class PortModule extends im.yaHustler.module.Module implements IMinecraft {
    protected PortModule() { super(); }
    protected PortModule(im.yaHustler.module.ModuleInfo metadata) { super(metadata); }
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
