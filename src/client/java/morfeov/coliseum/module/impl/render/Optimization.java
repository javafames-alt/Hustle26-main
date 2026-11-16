package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.performance.OptionOverride;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.Coliseum;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * Реальная оптимизация рендера: кэширование, обратимые снижения графики (всё восстанавливается
 * при выключении) и адаптивный режим, который включает тяжёлые снижения только когда FPS проседает.
 */
@ModuleInfo(id = "optimization", name = "Optimization", category = Category.RENDER,
    description = "Реальная оптимизация: меньше частиц, облаков, теней и дальних сущностей, адаптивный режим и счётчик FPS")
public final class Optimization extends Module {
    private final BooleanSetting showFps = add(new BooleanSetting("Показывать FPS", true));
    private final BooleanSetting cacheGeometry = add(new BooleanSetting("Кэш геометрии снега", true));
    private final BooleanSetting fewerParticles = add(new BooleanSetting("Меньше частиц", true));
    private final BooleanSetting noClouds = add(new BooleanSetting("Без облаков", false));
    private final BooleanSetting noShadows = add(new BooleanSetting("Без теней существ", true));
    private final BooleanSetting entityRange = add(new BooleanSetting("Меньше дальность сущностей", true));
    private final NumberSetting entityScale = add(new NumberSetting("Дальность сущностей %", 75, 50, 100, 5)
        .visibleWhen(entityRange::enabled));
    private final BooleanSetting noBiomeBlend = add(new BooleanSetting("Без смешивания биомов", true));
    private final BooleanSetting distantItems = add(new BooleanSetting("Скрывать дальние предметы", true));
    private final NumberSetting itemRange = add(new NumberSetting("Дальность предметов", 48, 16, 128, 8)
        .visibleWhen(distantItems::enabled));
    private final BooleanSetting fastBlur = add(new BooleanSetting("Лёгкое размытие GUI", true));
    private final BooleanSetting adaptive = add(new BooleanSetting("Адаптивный режим", false));
    private final NumberSetting targetFps = add(new NumberSetting("Целевой FPS", 60, 30, 240, 10)
        .visibleWhen(adaptive::enabled));
    private int lastFps = -1;
    private String fpsText = "";
    private int fpsWidth;
    private Options options;
    private OptionOverride<ParticleStatus> particles;
    private OptionOverride<CloudStatus> clouds;
    private OptionOverride<Boolean> shadows;
    private OptionOverride<Double> entities;
    private OptionOverride<Integer> biomeBlend;
    /** Адаптивный режим: true, пока FPS ниже цели (с гистерезисом, чтобы не мигало). */
    private boolean struggling;
    private long lowSince, highSince;

    @Override protected void onEnable() { apply(Minecraft.getInstance()); }
    @Override protected void onDisable() { struggling = false; lowSince = highSince = 0; restoreOptions(); }
    @Override public void tick(Minecraft mc) { apply(mc); }

    private void apply(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        if (options != mc.options) {
            restoreOptions();
            options = mc.options;
            particles = new OptionOverride<>(options.particles()::get, options.particles()::set);
            clouds = new OptionOverride<>(options.cloudStatus()::get, options.cloudStatus()::set);
            shadows = new OptionOverride<>(options.entityShadows()::get, options.entityShadows()::set);
            entities = new OptionOverride<>(options.entityDistanceScaling()::get, options.entityDistanceScaling()::set);
            biomeBlend = new OptionOverride<>(options.biomeBlendRadius()::get, options.biomeBlendRadius()::set);
        }
        boolean heavy = !adaptive.enabled() || updateAdaptive(mc);
        // Keep MINIMAL if the user already uses it; enabling optimization never raises quality.
        ParticleStatus desired = options.particles().get() == ParticleStatus.MINIMAL || adaptive.enabled() && struggling
            ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED;
        particles.update(fewerParticles.enabled(), desired);
        clouds.update(noClouds.enabled() && heavy, CloudStatus.OFF);
        shadows.update(noShadows.enabled() && heavy, false);
        double scale = Math.min(options.entityDistanceScaling().get(), entityScale.get() / 100.0);
        entities.update(entityRange.enabled() && heavy, scale);
        biomeBlend.update(noBiomeBlend.enabled(), 0);
    }

    /** Hysteresis: 2 s below the target turns reductions on, 5 s above target+15% turns them off. */
    private boolean updateAdaptive(Minecraft mc) {
        long now = System.nanoTime();
        int fps = mc.getFps(), target = (int) targetFps.floatValue();
        if (fps > 0 && fps < target) {
            highSince = 0;
            if (lowSince == 0) lowSince = now;
            if (now - lowSince > 2_000_000_000L) struggling = true;
        } else if (fps > target * 1.15) {
            lowSince = 0;
            if (highSince == 0) highSince = now;
            if (now - highSince > 5_000_000_000L) struggling = false;
        }
        return struggling;
    }

    public boolean cullItem(double distanceSquared) {
        if (!enabled() || !distantItems.enabled()) return false;
        double range = itemRange.get() * (adaptive.enabled() && struggling ? .6 : 1);
        return distanceSquared > range * range;
    }
    public boolean fastGuiBlur() { return enabled() && fastBlur.enabled(); }
    public boolean cacheSnowGeometry() { return enabled() && cacheGeometry.enabled(); }

    /** FPS показывается честно — без множителей. */
    public int displayFps(int actual) { return actual; }
    /** Оставлено для совместимости с ColiseumVisuals. */
    public void recordWorldFrame() { }

    public static int displayedFps(Minecraft mc) { return mc.getFps(); }

    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || !showFps.enabled() || mc.player == null
                || mc.getDebugOverlay().showDebugScreen() || morfeov.coliseum.ui.GuiVisibility.hidesHud()) return;
        int fps = mc.getFps();
        if (fps != lastFps) {
            lastFps = fps;
            fpsText = fps + " FPS";
            fpsWidth = mc.font.width(fpsText);
        }
        int x = Math.max(4, g.guiWidth() - fpsWidth - 6);
        int color = fps >= 60 ? 0xffaaffaa : fps >= 30 ? 0xffffe08a : 0xffff8a8a;
        g.text(mc.font, fpsText, x, 6, color, true);
    }

    /** Also called by the client shutdown callback. */
    public void restoreOptions() {
        if (particles != null) particles.restore();
        if (clouds != null) clouds.restore();
        if (shadows != null) shadows.restore();
        if (entities != null) entities.restore();
        if (biomeBlend != null) biomeBlend.restore();
        options = null;
        particles = null; clouds = null; shadows = null; entities = null; biomeBlend = null;
    }
}
