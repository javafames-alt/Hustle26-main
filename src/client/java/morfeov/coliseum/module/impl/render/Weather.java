package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.render.weather.*;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.performance.OptionOverride;
import morfeov.coliseum.render.coliseimvisual.WorldRenderMatrices;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.fog.FogData;
import org.joml.Matrix4f;

/**
 * Единый модуль атмосферы: готовые режимы «Снег», «Halloween», «Дождь» и режим «Свой»
 * с собственным шейдерным небом ({@link SkyDomeRenderer}): звёздная ночь с падающими звёздами и луной,
 * голубое небо с шейдерными облаками, закат и северное сияние.
 * Ambience остаётся зарегистрированным ради своих миксинов, но скрыт и всегда выключен.
 */
@ModuleInfo(id="weather", name="Ambience", category=Category.RENDER, description="Атмосфера: снег, Halloween, дождь с мокрым полом или своё шейдерное небо")
public final class Weather extends PortModule implements AutoCloseable {
    public static final String SNOW = "Снег", HALLOWEEN = "Halloween", RAIN = "Дождь", CUSTOM = "Свой";
    public final ModeSetting mode = add(new ModeSetting("Режим", RAIN, SNOW, HALLOWEEN, RAIN, CUSTOM));
    public final NumberSetting intensity = add(new NumberSetting("Интенсивность осадков",100,10,100,5).visibleWhen(() -> isRain() || isSnow()));
    public static final String SKY_NIGHT = "Звёздная ночь", SKY_DAY = "Голубое небо", SKY_SUNSET = "Закат", SKY_AURORA = "Северное сияние";
    public final ModeSetting sky = add(new ModeSetting("Небо", SKY_NIGHT, SKY_NIGHT, SKY_DAY, SKY_SUNSET, SKY_AURORA).visibleWhen(this::isCustom));
    public final NumberSetting clouds = add(new NumberSetting("Облака",50,0,100,5).visibleWhen(this::isCustom));
    public final NumberSetting cloudSpeed = add(new NumberSetting("Скорость облаков",1,0,5,.1).visibleWhen(() -> isCustom() && clouds.floatValue() > 0));
    public final NumberSetting stars = add(new NumberSetting("Звёзды",100,0,200,10).visibleWhen(() -> isCustom() && !sky.is(SKY_DAY)));
    public final NumberSetting meteors = add(new NumberSetting("Падающие звёзды",100,0,300,10).visibleWhen(() -> isCustom() && !sky.is(SKY_DAY)));
    public final NumberSetting moonSize = add(new NumberSetting("Размер луны",1,0,2.5,.1).visibleWhen(() -> isCustom() && (sky.is(SKY_NIGHT) || sky.is(SKY_AURORA))));
    public final NumberSetting milkyWay = add(new NumberSetting("Млечный путь",100,0,200,10).visibleWhen(() -> isCustom() && (sky.is(SKY_NIGHT) || sky.is(SKY_AURORA))));
    public final NumberSetting aurora = add(new NumberSetting("Сила сияния",100,0,200,10).visibleWhen(() -> isCustom() && sky.is(SKY_AURORA)));
    public final BooleanSetting customFog = add(new BooleanSetting("Туман",false).visibleWhen(this::isCustom));
    public final NumberSetting fogDistance = add(new NumberSetting("Дальность тумана",40,12,160,4).visibleWhen(() -> isHalloween() || isCustom() && customFog.enabled()));
    public final NumberSetting wind = add(new NumberSetting("Ветер",100,0,250,10).visibleWhen(() -> isRain() || isSnow() || isHalloween()));
    public final ModeSetting water = add(new ModeSetting("Вода на поверхности","Лужи","Лужи","Плёнка","Выключено").visibleWhen(this::isRain));
    public final NumberSetting reflections = add(new NumberSetting("Отражения",100,0,200,5).visibleWhen(() -> false));
    public final NumberSetting wetness = add(new NumberSetting("Влажность пола",80,0,100,5).visibleWhen(() -> isRain() && !water.is("Выключено")));
    public final NumberSetting wetDistance = add(new NumberSetting("Дистанция мокрого пола",32,8,256,4).visibleWhen(() -> isRain() && !water.is("Выключено")));
    public final ModeSetting wetQuality = add(new ModeSetting("Качество мокрого пола","Среднее","Низкое","Среднее","Высокое").visibleWhen(() -> false));
    public final NumberSetting snowDepth = add(new NumberSetting("Снежное покрытие",100,20,250,5).visibleWhen(this::isSnow));
    public final NumberSetting snowSheen = add(new NumberSetting("Блеск снега",100,0,200,5).visibleWhen(this::isSnow));
    public final NumberSetting flakeSize = add(new NumberSetting("Размер снежинок",1,.2,5,.1).visibleWhen(this::isSnow));
    public final NumberSetting fallSpeed = add(new NumberSetting("Скорость снежинок",1,.2,3,.1).visibleWhen(this::isSnow));
    public final BooleanSetting winterFog = add(new BooleanSetting("Зимний туман",true).visibleWhen(this::isSnow));
    public final NumberSetting winterFogDistance = add(new NumberSetting("Дальность зимнего тумана",96,24,192,4).visibleWhen(() -> isSnow() && winterFog.enabled()));
    public final NumberSetting halloweenParticles = add(new NumberSetting("Частицы Halloween",100,0,200,10).visibleWhen(this::isHalloween));
    public final NumberSetting pumpkins = add(new NumberSetting("Тыквы",100,0,200,10).visibleWhen(this::isHalloween));
    public final NumberSetting pumpkinSize = add(new NumberSetting("Размер тыкв",1,.5,2.5,.1).visibleWhen(this::isHalloween));
    public final NumberSetting candleGlow = add(new NumberSetting("Свечение свечей",100,0,200,10).visibleWhen(this::isHalloween));
    public final BooleanSetting bats = add(new BooleanSetting("Летучие мыши",true).visibleWhen(this::isHalloween));
    public final BooleanSetting wisps = add(new BooleanSetting("Блуждающие огни",true).visibleWhen(this::isHalloween));
    public final NumberSetting time = add(new NumberSetting("Время суток",12,0,24,.25).visibleWhen(() -> isRain() || isSnow()));
    private final Ambience ambience;
    private final HalloweenRenderer halloween = new HalloweenRenderer();
    private final ColiseumSnowRenderer snow = new ColiseumSnowRenderer();
    private final FallingSnowRenderer flakes = new FallingSnowRenderer();
    private final WetSurfaceRenderer wet = new WetSurfaceRenderer();
    private final WetSurfaceRenderer.Parameters wetSettings = new WetSurfaceRenderer.Parameters();
    private final HalloweenRenderer.Parameters halloweenSettings = new HalloweenRenderer.Parameters();
    private final SkyDomeRenderer skyDome = new SkyDomeRenderer();
    private final SkyDomeRenderer.Parameters skySettings = new SkyDomeRenderer.Parameters();
    private OptionOverride<CloudStatus> vanillaClouds;
    private Options cloudOptions;
    private Object world;
    private String renderedMode;

    public Weather() { this(null); }
    public Weather(Ambience ambience) {
        this.ambience = ambience;
    }

    public boolean isRain() { return mode.is(RAIN); }
    public boolean isSnow() { return mode.is(SNOW); }
    public boolean snowStep(net.minecraft.core.BlockPos pos) {
        return enabled() && isSnow() && !NoRender.noWeather() && snow.covers(mc.level, pos);
    }
    /** Отдельного режима тумана больше нет: туман настраивается в режиме «Свой». */
    public boolean isFog() { return false; }
    public boolean isHalloween() { return mode.is(HALLOWEEN); }
    public boolean isCustom() { return mode.is(CUSTOM); }
    /** Weather рисует сам (а не через Ambience). Используется миксинами вместо enabled(). */
    public boolean active() { return enabled(); }
    /** Своё небо рисуется вместо ванильного только в обычном мире (в Незере/Энде неба нет). */
    public boolean drawsSky() { return enabled() && isCustom() && mc.level != null && mc.level.dimension() == Level.OVERWORLD && !NoRender.noWeather(); }
    private int skyPreset() { return sky.is(SKY_DAY) ? SkyDomeRenderer.DAY : sky.is(SKY_SUNSET) ? SkyDomeRenderer.SUNSET : sky.is(SKY_AURORA) ? SkyDomeRenderer.AURORA : SkyDomeRenderer.NIGHT; }
    /** Вызывается из SkyRendererMixin вместо ванильного неба. */
    public void renderSky() {
        skySettings.preset = skyPreset();
        skySettings.clouds = clouds.floatValue()/100;
        skySettings.cloudSpeed = cloudSpeed.floatValue();
        skySettings.stars = stars.floatValue()/100;
        skySettings.meteors = meteors.floatValue()/100;
        skySettings.moonSize = moonSize.floatValue();
        skySettings.milkyWay = milkyWay.floatValue()/100;
        skySettings.aurora = aurora.floatValue()/100;
        skyDome.render(WorldRenderMatrices.projectionMatrix(), WorldRenderMatrices.viewMatrix(), skySettings);
    }
    public float precipitationStrength() { return isRain() || isSnow() ? intensity.floatValue()/100 : 0; }
    public float windStrength() { return wind.floatValue()/100; }
    public float fogRange() { return fogDistance.floatValue(); }
    public boolean hasFog() { return isCustom() || isHalloween() || isSnow() && winterFog.enabled(); }
    /** Called only for air fog; fluid and status-effect fog retain vanilla behavior. */
    public void applyFog(FogData fog) {
        if (!active() || !hasFog()) return;
        if (isCustom()) {
            // Горизонт мира подкрашивается под цвет выбранного неба; дальность — только если включён «Туман».
            switch (skyPreset()) {
                case SkyDomeRenderer.DAY -> fog.color.set(.62f, .79f, .96f, 1f);
                case SkyDomeRenderer.SUNSET -> fog.color.set(1f, .52f, .22f, 1f);
                case SkyDomeRenderer.AURORA -> fog.color.set(.030f, .070f, .090f, 1f);
                default -> fog.color.set(.040f, .065f, .150f, 1f);
            }
            if (customFog.enabled()) {
                float end = Math.min(fogRange(), fog.renderDistanceEnd);
                fog.environmentalStart = fog.renderDistanceStart = end * .25f;
                fog.environmentalEnd = fog.renderDistanceEnd = end;
                fog.skyEnd = fog.cloudEnd = end;
            }
            return;
        }
        float range = isSnow() ? winterFogDistance.floatValue() : fogRange();
        float end = Math.min(range, fog.renderDistanceEnd);
        fog.environmentalStart = fog.renderDistanceStart = end * (isSnow() ? .15f : .2f);
        fog.environmentalEnd = fog.renderDistanceEnd = end;
        if (isSnow()) {
            // A cool white horizon blends the snow-covered terrain into the sky.
            fog.color.set(.92f, .95f, .98f, 1f);
            fog.skyEnd = fog.cloudEnd = end;
        } else if (isHalloween()) {
            // Low, smoky violet-orange haze: dark near the ground, warmer toward the horizon.
            fog.environmentalStart = fog.renderDistanceStart = end * .06f;
            fog.color.set(.10f, .040f, .055f, 1f);
            fog.skyEnd = fog.cloudEnd = end;
        }
    }
    public long timeOfDay() {
        if (isCustom()) return switch (skyPreset()) { case SkyDomeRenderer.DAY -> 6000; case SkyDomeRenderer.SUNSET -> 12000; default -> 18000; };
        return isHalloween() ? 18000 : Math.floorMod(Math.round((time.floatValue()-6)*1000),24000); }
    public boolean hasWetSurface() { return active() && !NoRender.noWeather() && (isSnow() || isRain() && !water.is("Выключено")); }

    /** Ambience работает только при включённом Weather в режиме «Свой». Вызывается каждый тик клиента. */
    public void syncAmbience() {
        if (ambience != null && ambience.enabled()) ambience.setEnabled(false);
    }
    /** В режиме «Свой» облака рисует шейдер неба, поэтому ванильные облака временно выключаются. */
    private void syncClouds(Minecraft client) {
        if (client.options == null) return;
        if (cloudOptions != client.options) {
            if (vanillaClouds != null) vanillaClouds.restore();
            cloudOptions = client.options;
            vanillaClouds = new OptionOverride<>(cloudOptions.cloudStatus()::get, cloudOptions.cloudStatus()::set);
        }
        vanillaClouds.update(enabled() && isCustom(), CloudStatus.OFF);
    }
    private void restoreClouds() {
        if (vanillaClouds != null) vanillaClouds.restore();
        vanillaClouds = null; cloudOptions = null;
    }
    @Override public void tick(Minecraft client) {
        syncAmbience();
        syncClouds(client);
        if (world != client.level || !mode.get().equals(renderedMode)) {
            close(); world = client.level; renderedMode = mode.get();
        }
    }
    public void render(Matrix4f projection, Matrix4f view) {
        if (!active() || isCustom() || NoRender.noWeather() || mc.level == null || mc.player == null || !projection.isFinite() || !view.isFinite()) return;
        if (isHalloween()) {
            halloweenSettings.embers = halloweenParticles.floatValue()/100;
            halloweenSettings.pumpkins = pumpkins.floatValue()/100;
            halloweenSettings.pumpkinScale = pumpkinSize.floatValue();
            halloweenSettings.glow = candleGlow.floatValue()/100;
            halloweenSettings.wind = windStrength();
            halloweenSettings.bats = bats.enabled();
            halloweenSettings.wisps = wisps.enabled();
            halloween.render(mc.gameRenderer.mainCamera(), view, projection, halloweenSettings);
        } else if (isSnow()) {
            snow.render(mc.gameRenderer.mainCamera(), view, projection, snowDepth.floatValue()/100, snowSheen.floatValue()/100, timeOfDay());
            flakes.render(mc.gameRenderer.mainCamera(), view, projection, precipitationStrength(),windStrength(),flakeSize.floatValue(),fallSpeed.floatValue());
        } else if (isRain()) {
            if (hasWetSurface()) {
                wetSettings.puddleCoverage = water.is("Плёнка") ? 1 : .75f;
                wetSettings.wetness = wetness.floatValue()/100;
                wetSettings.maxDistance = wetDistance.floatValue();
                wet.render(projection, view, wetSettings);
            }
        }
    }
    @Override public void onDisable() { close(); restoreClouds(); if (ambience != null) ambience.setEnabled(false); }
    @Override public void onDisconnect(EventDisconnect event) { close(); }
    @Override public void close() { snow.close(); flakes.close(); wet.close(); halloween.close(); skyDome.close(); world=null; renderedMode=null; }
}
