package im.yaHustler.module.impl.player;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.ui.BotScreen;
import net.minecraft.client.Minecraft;

@ModuleInfo(id = "bot", name = "Bots", category = Category.BOTS,
    description = "Менеджер ботов")
public final class Bot extends Module {
    @Override protected void onEnable() {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new BotScreen(client.gui.screen()));
        setEnabled(false);
    }
}
