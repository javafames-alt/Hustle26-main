package morfeov.coliseum.module.impl.player;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.ui.BotScreen;
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
