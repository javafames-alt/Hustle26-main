package im.yaHustler.module.impl.render;

import im.yaHustler.Hustle26;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.ClickGuiScreen;
import net.minecraft.client.Minecraft;

@ModuleInfo(id = "clickgui", name = "ClickGUI", category = Category.RENDER,
        description = "Настройка интерфейса, темы и сохранённые конфигурации")
public final class ClickGui extends Module {
    public final BooleanSetting shaderBackground = new BooleanSetting("Shader Background", false);
    public final BooleanSetting blur = add(new BooleanSetting("Blur", true).visibleWhen(() -> false));
    public final NumberSetting blurStrength = add(new NumberSetting("Blur Radius", 14, 8, 30, 1));
    public final ModeSetting glassShader = new ModeSetting("Glass Shader", "Off", "Off", "Corner", "Full surface", "Perspective");
    public final NumberSetting shaderStrength = new NumberSetting("Shader Strength", 75, 10, 100, 1);
    public final NumberSetting shaderSpeed = new NumberSetting("Shader Speed", 1, .2, 3, .1);
    public final NumberSetting scale = add(new NumberSetting("GUI Scale", 1, .55, 1.5, .01));
    public final BooleanSetting themeBlur = add(new BooleanSetting("Theme Blur", true));
    public final NumberSetting opacity = add(new NumberSetting("GUI Alpha", .90, 0, 1, .01));
    public final BooleanSetting shadow = add(new BooleanSetting("GUI Shadow", true));
    public final NumberSetting shadowStrength = add(new NumberSetting("GUI Shadow Strength", 65, 0, 100, 1).visibleWhen(shadow::enabled));
    public final NumberSetting shadowSize = add(new NumberSetting("GUI Shadow Size", 8, 2, 20, .5).visibleWhen(shadow::enabled));
    public final NumberSetting cornerRadius = add(new NumberSetting("Module Corner Radius", 5, 0, 14, 1));
    public final ModeSetting animation = add(new ModeSetting("Анимация появления", "Cinematic", "Cinematic", "Slide", "Fade", "Off"));

    @Override
    protected void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof ClickGuiScreen)) {
            client.gui.setScreen(new ClickGuiScreen(Hustle26.MODULES));
        }
        setEnabled(false);
    }
}
