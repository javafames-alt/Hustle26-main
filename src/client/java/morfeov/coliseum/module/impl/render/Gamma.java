package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;

@ModuleInfo(id="gamma", name="Gamma", category=Category.RENDER,
    description="Повышает яркость: светло ночью и в пещерах")
public final class Gamma extends Module {
    private final NumberSetting brightness = add(new NumberSetting("Яркость", 100, 0, 100, 1));

    public void apply(LightmapRenderState state) {
        if (!enabled() || !state.needsUpdate) return;
        float amount = brightness.floatValue() / 100.0f;
        if (amount <= 0) return;
        // Raise the light floor without changing the player's saved video settings.
        state.ambientColor = new Vector3f(state.ambientColor).lerp(new Vector3f(1.0f), amount);
        state.darknessEffectScale *= 1.0f - amount;
        state.bossOverlayWorldDarkening *= 1.0f - amount;
    }
}
