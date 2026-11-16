package im.yaHustler.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Records the extracted HUD surfaces for the render-thread backdrop pass. */
public final class HudBackdrop {
    private static final List<float[]> SURFACES = new ArrayList<>();
    private HudBackdrop() {}
    public static synchronized void add(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius) {
        SURFACES.add(im.yaHustler.ui.BackdropStyle.transformedSurface(x, y, w, h, radius, g.pose()));
    }
    public static synchronized float[][] drain() {
        float[][] result = SURFACES.toArray(float[][]::new); SURFACES.clear(); return result;
    }
}
