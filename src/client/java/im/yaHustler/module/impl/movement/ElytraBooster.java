package im.yaHustler.module.impl.movement;

import im.yaHustler.font.Fonts;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.ui.GuiVisibility;
import im.yaHustler.util.ColorUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** the original implementation's angle-dependent firework acceleration, remapped to native 26.3 names. */
@ModuleInfo(id = "elytrabooster", name = "ElytraBooster", category = Category.MOVEMENT,
    description = "Усиливает ускорение от фейерверков при полёте на элитрах")
public final class ElytraBooster extends Module {
    public static final String REALLY_WORLD_116 = "ReallyWorld 1.16.5";
    public static final String REALLY_WORLD_121 = "ReallyWorld 1.21.11";
    public static final String BRAVO = "BravoHvH";
    public static final String CUSTOM = "Custom";
    private static final float[] WORLD_116_HORIZONTAL = {1.6f, 1.63f, 1.66f, 1.71f, 1.73f, 1.81f, 1.81f, 1.81f, 1.81f};
    private static final float[] BRAVO_HORIZONTAL = {1.64f, 1.67f, 1.68f, 1.73f, 1.77f, 1.87f, 2.01f, 2.05f, 2.05f};
    private static final float[] BRAVO_VERTICAL = {1.65f, 1.7f, 1.71f, 1.74f, 1.79f, 1.91f, 2.01f, 2.02f, 2.02f};
    private static ElytraBooster instance;

    public final ModeSetting mode = add(new ModeSetting("Мод", REALLY_WORLD_116,
        REALLY_WORLD_116, REALLY_WORLD_121, BRAVO, CUSTOM));
    public final NumberSetting[] horizontalBoost = new NumberSetting[9];
    public final NumberSetting[] verticalBoost = new NumberSetting[9];
    public final BooleanSetting showAngles;
    private final BoosterAngleDisplay yawDisplay = new BoosterAngleDisplay();
    private final BoosterAngleDisplay pitchDisplay = new BoosterAngleDisplay();

    public ElytraBooster() {
        instance = this;
        for (int band = 0; band < horizontalBoost.length; band++) {
            horizontalBoost[band] = add(new NumberSetting(band * 5 + "-" + (band + 1) * 5 + " По X", 1.5, 1.5, 2.5, .01)
                .visibleWhen(() -> mode.is(CUSTOM)));
        }
        for (int band = 0; band < verticalBoost.length; band++) {
            verticalBoost[band] = add(new NumberSetting(band * 5 + "-" + (band + 1) * 5 + " По Y", 1.5, 1.5, 2.5, .01)
                .visibleWhen(() -> mode.is(CUSTOM)));
        }
        showAngles = add(new BooleanSetting("Рисовать текущий Yaw или Pitch", false));
    }

    public static ElytraBooster getInstance() { return instance; }

    /** Folding by 180 degrees preserves the original implementation's symmetry in every flight direction. */
    public static float foldedAngle(float degrees) {
        float folded = degrees % 180;
        if (folded > 90) folded -= 180;
        else if (folded < -90) folded += 180;
        return folded;
    }

    private static float distanceFromAxis(float degrees) {
        float distance = Math.abs(foldedAngle(degrees));
        return distance > 45 ? 90 - distance : distance;
    }

    private static int angleBand(float angle) {
        // The upper end of each five-degree interval belongs to that interval.
        for (int band = 0; band < 8; band++) if (angle <= (band + 1) * 5) return band;
        return 8;
    }

    public BoostFactors factors(float yaw, float pitch) {
        float horizontalAngle = distanceFromAxis(yaw);
        float verticalAngle = distanceFromAxis(pitch);
        int horizontalBand = angleBand(Math.max(horizontalAngle, verticalAngle));
        int verticalBand = angleBand(verticalAngle);
        float horizontal = 1.5f;
        float vertical = 1.5f;
        if (mode.is(REALLY_WORLD_116)) {
            horizontal = WORLD_116_HORIZONTAL[horizontalBand];
            if (verticalAngle <= 5) vertical = 1.6f;
            else if (verticalAngle <= 10) vertical = 1.62f;
            else if (verticalAngle <= 15) vertical = 1.6f;
            else if (verticalAngle <= 20) vertical = 1.62f;
            else if (verticalAngle <= 25) vertical = 1.64f;
            else if (verticalAngle <= 30) vertical = 1.68f;
            else if (verticalAngle <= 35) vertical = 1.75f;
            else if (verticalAngle <= 40) vertical = 1.9f;
            else if (verticalAngle >= 43 && verticalAngle <= 45) vertical = 2.26f;
            else vertical = 2.16f;
            horizontal = Math.max(horizontal, vertical);
        } else if (mode.is(REALLY_WORLD_121)) {
            horizontal = horizontalAngle <= 30 && verticalAngle <= 30 ? 1.53f
                : horizontalAngle <= 40 && verticalAngle <= 40 ? 2.04f : 2.059f;
            vertical = verticalAngle <= 30 ? 1.53f : 2.02f;
            horizontal = Math.max(horizontal, vertical);
        } else if (mode.is(BRAVO)) {
            // Unlike the other presets, the donor keeps the two Bravo axes independent.
            horizontal = BRAVO_HORIZONTAL[horizontalBand];
            vertical = BRAVO_VERTICAL[verticalBand];
        } else if (mode.is(CUSTOM)) {
            vertical = verticalBoost[verticalBand].floatValue();
            horizontal = Math.max(horizontalBoost[horizontalBand].floatValue(), vertical);
        }
        return new BoostFactors(horizontal, vertical);
    }

    public static boolean appliesTo(LivingEntity rider) {
        return instance != null && instance.enabled() && rider != null
            && rider == Minecraft.getInstance().player;
    }

    /** Same acceleration and damping as vanilla, with the donor's per-axis target speed. */
    public static Vec3 accelerate(Vec3 velocity, Vec3 direction, BoostFactors factors) {
        return velocity.add(
            direction.x * .1 + (direction.x * factors.horizontal - velocity.x) * .5,
            direction.y * .1 + (direction.y * factors.vertical - velocity.y) * .5,
            direction.z * .1 + (direction.z * factors.horizontal - velocity.z) * .5);
    }

    @Override protected void onDisable() { yawDisplay.clear(); pitchDisplay.clear(); }

    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled() || !showAngles.enabled() || client.player == null || client.level == null
            || GuiVisibility.hidesHud() || client.getDebugOverlay().showDebugScreen()) return;
        long now = System.nanoTime();
        yawDisplay.update((int) foldedAngle(client.player.getYRot()), now);
        pitchDisplay.update((int) foldedAngle(client.player.getXRot()), now);
        float center = client.getWindow().getGuiScaledWidth() / 2f;
        float middle = client.getWindow().getGuiScaledHeight() / 2f;
        drawAngle(graphics, "Yaw: ", yawDisplay, center, middle + 20, now);
        drawAngle(graphics, "Pitch: ", pitchDisplay, center, middle + 35, now);
    }

    private static void drawAngle(GuiGraphicsExtractor graphics, String label, BoosterAngleDisplay display,
                                  float center, float y, long now) {
        float scale = 1f;
        float x = center - Fonts.width(label + display.text() + "°", scale) / 2;
        int textColor = 0xffebebeb;
        Fonts.draw(graphics, label, x, y, textColor, scale);
        x += Fonts.width(label, scale);
        for (BoosterAngleDisplay.Digit digit : display.digits()) {
            float progress = digit.progress(now);
            if (progress < 1) {
                float eased = 1 - (float) Math.pow(1 - progress, 3);
                int oldColor = ColorUtil.alpha(textColor, Math.round(255 * (1 - eased)));
                int newColor = ColorUtil.lerp(textColor, Theme.accent(), (float) Math.sin(progress * Math.PI) * .28f);
                Fonts.draw(graphics, digit.previous(), x, y + eased * 3, oldColor, scale);
                Fonts.draw(graphics, digit.current(), x, y - 3 + eased * 3,
                    ColorUtil.alpha(newColor, Math.round(255 * eased)), scale);
            } else Fonts.draw(graphics, digit.current(), x, y, textColor, scale);
            x += Fonts.width(digit.current(), scale);
        }
        Fonts.draw(graphics, "°", x, y, textColor, scale);
    }

    public record BoostFactors(float horizontal, float vertical) {}
}
