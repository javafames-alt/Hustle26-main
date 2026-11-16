package im.yaHustler.module.impl.combat;

import im.yaHustler.Hustle26;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(id="anti_bot", name="AntiBot", category=Category.COMBAT, description="Проверяет ближайших игроков по tab-списку Hustle 26.3")
public final class AntiBot extends Module {
    public static boolean rejects(Entity entity) {
        var module=Hustle26.MODULES.get(AntiBot.class);
        return module!=null && module.enabled() && entity instanceof Player player && module.isBot(player);
    }
    public boolean isBot(Player player) {
        var mc=Minecraft.getInstance();
        if(!enabled() || mc.player==null || player==mc.player || mc.player.distanceToSqr(player)>=64 || mc.getConnection()==null) return false;
        var entry=mc.getConnection().getPlayerInfo(player.getUUID());
        return entry==null || suspiciousDisplay(entry.getTabListDisplayName()==null?null:entry.getTabListDisplayName().getString(),player.getGameProfile().name());
    }
    public static boolean suspiciousDisplay(String display,String name) { return display==null || display.equals(name); }
}
