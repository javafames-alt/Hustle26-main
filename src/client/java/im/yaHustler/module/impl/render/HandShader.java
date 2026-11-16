package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.render.hands.HandFireRenderer;
import im.yaHustler.render.hands.HandFlameRenderer;
import im.yaHustler.render.hands.HandNoiseRenderer;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ColorSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

@ModuleInfo(id = "hand_shader", name = "Hands", category = Category.RENDER,
    description = "Шейдер на руку и предметы от первого лица")
public final class HandShader extends Module implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger("hustle263-handshader");
    private static HandShader instance;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Flame", "Noise", "Caustic", "Glow", "Fire", "Flame"));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", 0.65, 0.0, 1.0, 0.05));

    private final NumberSetting glowStrength = add(new NumberSetting("Glow Strength", 1.25, 0.4, 2.0, 0.05));
    private final NumberSetting glowWidth = add(new NumberSetting("Glow Width", 1.0, 0.45, 2.0, 0.05));
    private final NumberSetting glowWave = add(new NumberSetting("Wave", 0.75, 0.0, 1.5, 0.05));
    private final NumberSetting glowWaveSpeed = add(new NumberSetting("Wave Speed", 1.0, 0.0, 3.0, 0.05));

    private final BooleanSetting themeColor = add(new BooleanSetting("Theme Color", true));
    private final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));
    private final ColorSetting customColor = add(new ColorSetting("Color", 0xff5ef2ff));
    private final ColorSetting flameColor = add(new ColorSetting("Flame Color", 0xffff8fe1));

    private final BooleanSetting fireItemColors = add(new BooleanSetting("Item Colors", true));
    private final NumberSetting fireHeight = add(new NumberSetting("Flame Height", 0.9, 0.0, 2.0, 0.05));
    private final NumberSetting fireSpeed = add(new NumberSetting("Fire Speed", 1.0, 0.2, 3.0, 0.1));
    private final NumberSetting fireGlow = add(new NumberSetting("Fire Glow", 1.1, 0.0, 2.0, 0.05));
    private final NumberSetting fireFade = add(new NumberSetting("Fire Fade", .04, 0, .2, .01));
    private final NumberSetting fireRise = add(new NumberSetting("Fire Rise", .2, 0, 2, .05));
    private final NumberSetting fireTurbulence = add(new NumberSetting("Fire Turbulence", .02, 0, .1, .005));
    private final NumberSetting fireFlicker = add(new NumberSetting("Fire Flicker", .2, 0, 1, .05));

    private final HandNoiseRenderer noise = new HandNoiseRenderer();
    private final HandFireRenderer fire = new HandFireRenderer();
    private final HandFlameRenderer flame = new HandFlameRenderer();
    private boolean failed;

    public HandShader() {
        instance = this;
        glowStrength.visibleWhen(() -> mode.is("Glow"));
        glowWidth.visibleWhen(() -> mode.is("Glow"));
        glowWave.visibleWhen(() -> mode.is("Glow") || mode.is("Flame"));
        glowWaveSpeed.visibleWhen(() -> mode.is("Glow"));
        themeColor.visibleWhen(() -> !mode.is("Flame") && !usesItemColors());
        rainbow.visibleWhen(() -> !mode.is("Flame") && !usesItemColors());
        customColor.visibleWhen(() -> !mode.is("Flame") && !usesItemColors() && !themeColor.enabled() && !rainbow.enabled());
        flameColor.visibleWhen(() -> mode.is("Flame"));
        fireItemColors.visibleWhen(() -> mode.is("Fire"));
        fireFade.visibleWhen(() -> mode.is("Fire"));
        fireRise.visibleWhen(() -> mode.is("Fire"));
        fireTurbulence.visibleWhen(() -> mode.is("Fire"));
        fireFlicker.visibleWhen(() -> mode.is("Fire"));
        fireHeight.visibleWhen(() -> mode.is("Flame"));
        fireSpeed.visibleWhen(() -> mode.is("Flame"));
        fireGlow.visibleWhen(() -> mode.is("Fire") || mode.is("Flame"));
    }

    @Override
    protected void onEnable() {
        failed = false;
    }

    @Override
    protected void onDisable() {
        close();
    }

    @Override public void close() {
        fire.close();
        noise.close();
        flame.close();
    }

    private boolean usesItemColors() {
        return mode.is("Fire") && fireItemColors.enabled();
    }

    private float[] color() {
        int argb;
        if (mode.is("Flame")) {
            argb = flameColor.color();
        } else if (rainbow.enabled()) {
            argb = Mth.hsvToRgb((System.currentTimeMillis() % 4000L) / 4000.0f, 0.8f, 1.0f);
        } else if (themeColor.enabled()) {
            argb = Theme.accent();
        } else {
            argb = customColor.color();
        }
        return new float[] {
            (argb >> 16 & 0xFF) / 255.0f,
            (argb >> 8 & 0xFF) / 255.0f,
            (argb & 0xFF) / 255.0f,
            opacity.floatValue()
        };
    }

    public static void afterHands(GpuTextureView handDepth) {
        HandShader self = instance;
        if (self == null || !self.enabled() || self.failed || handDepth == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            self.close();
            return;
        }
        RenderTarget output = mc.gameRenderer.mainRenderTarget();
        if (output == null || output.getColorTextureView() == null) return;
        try {
            float[] color = self.color();
            String current = self.mode.get();
            if (current.equals("Flame")) {
                self.fire.close();
                self.noise.close();
                self.flame.render(output, handDepth, color, self.fireHeight.floatValue(), self.fireSpeed.floatValue(),
                    self.glowWave.floatValue(), self.fireGlow.floatValue());
                return;
            }
            self.flame.close();
            if (current.equals("Fire")) {
                self.fire.render(output, handDepth, color, self.fireItemColors.enabled(),
                    self.fireFade.floatValue(), self.fireRise.floatValue(), self.fireTurbulence.floatValue(),
                    self.fireFlicker.floatValue(), self.fireGlow.floatValue());
            } else {
                self.fire.close();
                if (current.equals("Glow")) {
                    self.noise.render(output, handDepth, current, color, self.glowStrength.floatValue(),
                        self.glowWaveSpeed.floatValue(), self.glowWave.floatValue(), self.glowWidth.floatValue());
                } else {
                    self.noise.render(output, handDepth, current, color, 0.8f, 1.0f, 0.55f, 1.55f);
                }
            }
        } catch (Throwable t) {
            self.failed = true;
            LOGGER.log(System.Logger.Level.ERROR, "HandShader failed, effect disabled until re-enabled", t);
        }
    }
}
