package morfeov.coliseum.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class GuiVisibility {
    private GuiVisibility() {}
    public static boolean hidesHud() { return hidesHud(Minecraft.getInstance().gui.screen()); }
    public static boolean hidesHud(Screen screen) {
        return screen instanceof ClickGuiScreen || screen instanceof ThemePickerScreen
                || screen instanceof HudEditorScreen editor && editor.backdropScreen()!=null;
    }
}
