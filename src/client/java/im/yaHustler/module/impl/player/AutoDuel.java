package im.yaHustler.module.impl.player;
import java.util.*;
import im.yaHustler.module.*;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventPacketReceive;
import im.yaHustler.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id="auto_duel", name="AutoDuel", category=Category.PLAYER, description="Отправляет запросы на дуэль")
public final class AutoDuel extends PortModule {
    private final ModeSetting mode=add(new ModeSetting("Режим", "Шары", "Шары", "Щит", "Шипы 3", "Незеритка", "Читерский рай", "Лук", "Классик", "Тотемы", "Нодебафф"));
    private final Set<String> sent=new HashSet<>();
    private long commandAt, resetAt, menuAt;
    private Vec3 lastPosition;
    @Override protected void onEnable() { sent.clear(); commandAt=resetAt=menuAt=System.currentTimeMillis(); lastPosition=null; }
    @Override public void onDisconnect(im.yaHustler.port.event.EventDisconnect e) { sent.clear(); lastPosition=null; }
    @Override public void onPacketReceive(EventPacketReceive event) {
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) return;
        String text=packet.content().getString().toLowerCase(Locale.ROOT);
        if (text.contains("начало") && text.contains("через") && text.contains("секунд!") || text.contains("дуэли » во время поединка запрещено использовать команды")) setEnabled(false);
    }
    @Override public void tick(Minecraft mc) {
        if (mc.player==null || mc.level==null || mc.getConnection()==null || mc.gameMode==null) return;
        Vec3 pos=mc.player.position();
        if (lastPosition!=null && pos.distanceToSqr(lastPosition)>250000) { setEnabled(false); return; }
        lastPosition=pos;
        long now=System.currentTimeMillis(); var players=mc.getConnection().getOnlinePlayers();
        if (now-resetAt>=800L*players.size()) { sent.clear(); resetAt=now; }
        if (now-commandAt>=2000) for (var entry:players) {
            String name=entry.getProfile().name();
            if (name.matches("[A-Za-z0-9_]{3,16}") && !name.equals(mc.player.getGameProfile().name()) && sent.add(name)) { mc.getConnection().sendCommand("duel "+name); commandAt=now; break; }
        }
        if (mc.gui.screen()==null || now-menuAt<150) return;
        String title=mc.gui.screen().getTitle().getString();
        int slot=title.contains("Выбор набора (1/1)") ? switch(mode.get()) { case "Щит"->0; case "Шипы 3"->1; case "Лук"->2; case "Тотемы"->3; case "Нодебафф"->4; case "Шары"->5; case "Классик"->6; case "Читерский рай"->7; default->8; } : title.contains("Настройка поединка") ? 0 : -1;
        if (slot>=0 && slot<mc.player.containerMenu.slots.size()) { mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,slot,0,ContainerInput.QUICK_MOVE,mc.player); menuAt=now; }
    }
}
