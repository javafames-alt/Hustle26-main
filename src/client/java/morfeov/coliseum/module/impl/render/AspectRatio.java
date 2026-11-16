package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;

@ModuleInfo(id="aspect_ratio", name="AspectRatio", category=Category.RENDER, description="Растягивает соотношение сторон экрана")
public final class AspectRatio extends Module {
    private final ModeSetting preset = add(new ModeSetting("Aspect Ratio", "4:3", "16:9", "16:10", "4:3", "5:4", "Custom"));
    private final NumberSetting custom = add(new NumberSetting("Custom Ratio", 1.333, 0.1, 4, 0.001)).visibleWhen(() -> preset.is("Custom"));
    public float ratio() {
        return switch (preset.get()) {
            case "16:9" -> 16f / 9f;
            case "16:10" -> 1.6f;
            case "5:4" -> 1.25f;
            case "Custom" -> custom.floatValue();
            default -> 4f / 3f;
        };
    }
}
