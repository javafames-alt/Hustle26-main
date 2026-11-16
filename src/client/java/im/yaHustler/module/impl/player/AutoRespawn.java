package im.yaHustler.module.impl.player;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(id = "auto_respawn", name = "AutoRespawn", category = Category.PLAYER, description = "Автоматически возрождает после смерти")
public final class AutoRespawn extends Module {
    private final NumberSetting delay = add(new NumberSetting("Задержка, мс", 0, 0, 5000, 50));
    private LocalPlayer deadPlayer;
    private long diedAt;
    private boolean requested;

    @Override public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || !player.isDeadOrDying()) { reset(); return; }
        if (client.level.getLevelData().isHardcore()) return;
        if (deadPlayer != player) {
            deadPlayer = player;
            diedAt = System.nanoTime();
            requested = false;
        }
        if (requested || !(client.gui.screen() instanceof DeathScreen) || System.nanoTime() - diedAt < delay.get() * 1_000_000) return;
        requested = true;
        player.respawn();
    }
    private void reset() { deadPlayer = null; diedAt = 0; requested = false; }
    @Override protected void onDisable() { reset(); }
}
