package morfeov.coliseum.ui;

import java.nio.file.Path;
import java.lang.reflect.*;
import java.util.*;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.render.Weather;
import morfeov.coliseum.module.impl.movement.Speed;
import morfeov.coliseum.port.TimerSpeed;
import morfeov.coliseum.port.event.*;
import morfeov.coliseum.setting.*;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.fog.FogData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WeatherGeometrics {
    @TempDir Path directory;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(WeatherGeometrics.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out,true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
        if (summary.getTestsSucceededCount() != 7 || summary.getTotalFailureCount() != 0) throw new AssertionError("Weather / Speed port failed");
    }
    @Test void weatherRetainsDonorModesAndConditionalSettings() {
        var weather = new Weather();
        assertEquals(List.of("Снег","Halloween","Дождь","Свой"), weather.mode.modes());
        assertEquals(31,weather.settings().size());
        assertFalse(weather.reflections.visible()); assertFalse(weather.snowDepth.visible()); assertFalse(weather.fogDistance.visible());
        weather.water.set("Выключено"); assertFalse(weather.reflections.visible());
        weather.mode.set("Снег"); assertTrue(weather.snowDepth.visible()); assertTrue(weather.flakeSize.visible()); assertFalse(weather.water.visible());
        assertTrue(weather.winterFog.visible()); assertTrue(weather.winterFogDistance.visible());
        weather.winterFog.set(false); assertFalse(weather.winterFogDistance.visible()); assertFalse(weather.hasFog());
        weather.flakeSize.set(.2); assertEquals(.2,weather.flakeSize.get(),.0001);
        weather.flakeSize.set(5.0); assertEquals(5,weather.flakeSize.get(),.0001);
        weather.mode.set("Свой"); assertTrue(weather.sky.visible()); assertTrue(weather.customFog.visible());
        assertFalse(weather.fogDistance.visible()); assertFalse(weather.intensity.visible()); assertFalse(weather.wind.visible());
        assertFalse(weather.winterFog.visible()); assertFalse(weather.winterFogDistance.visible()); assertTrue(weather.hasFog());
        assertEquals(0,weather.precipitationStrength());
        weather.customFog.set(true); assertTrue(weather.fogDistance.visible());
        weather.sky.set("Голубое небо"); assertFalse(weather.stars.visible()); assertFalse(weather.moonSize.visible());
        weather.sky.set("Северное сияние"); assertTrue(weather.aurora.visible()); assertTrue(weather.stars.visible());
        weather.mode.set("Дождь");weather.intensity.set(35.0);weather.wind.set(180.0);
        assertEquals(.35f,weather.precipitationStrength());assertEquals(1.8f,weather.windStrength());
    }
    @Test void winterFogBlendsTheHorizonAndRespectsRenderDistanceAndToggle() {
        var weather = new Weather();
        weather.mode.set("Снег");
        var fog = new FogData();
        fog.renderDistanceEnd = 160;
        fog.color.set(.1f,.2f,.3f,1f);
        weather.applyFog(fog);
        assertEquals(160,fog.renderDistanceEnd);
        weather.setEnabled(true);
        weather.applyFog(fog);
        assertEquals(96,fog.renderDistanceEnd);
        assertEquals(14.4f,fog.environmentalStart,.001f);
        assertEquals(96,fog.environmentalEnd);
        assertEquals(96,fog.skyEnd); assertEquals(96,fog.cloudEnd);
        assertEquals(.92f,fog.color.x); assertEquals(.95f,fog.color.y); assertEquals(.98f,fog.color.z); assertEquals(1,fog.color.w);
        fog.renderDistanceEnd = 32;
        weather.applyFog(fog); assertEquals(32,fog.renderDistanceEnd);
        weather.winterFog.set(false);
        fog.renderDistanceEnd = 160;
        weather.applyFog(fog); assertEquals(160,fog.renderDistanceEnd);
        weather.mode.set("Дождь");
        weather.applyFog(fog); assertEquals(160,fog.renderDistanceEnd);
        weather.mode.set("Свой"); weather.customFog.set(true);
        fog.color.set(.1f,.2f,.3f,1f);
        weather.applyFog(fog); assertEquals(40,fog.renderDistanceEnd); assertEquals(10,fog.environmentalStart);
        assertEquals(.040f,fog.color.x); assertEquals(.065f,fog.color.y); assertEquals(.150f,fog.color.z);
        weather.setEnabled(false);
    }
    @Test void weatherClockMatchesMinecraftHoursAtDayBoundaries() {
        var weather = new Weather();
        double[] hours={0,6,12,18,24};long[] ticks={18000,0,6000,12000,18000};
        for(int i=0;i<hours.length;i++){weather.time.set(hours[i]);assertEquals(ticks[i],weather.timeOfDay());}
    }
    @Test void halloweenKeepsNightFogAndSettingsWithoutChangingOtherModes() throws Exception {
        var registry = new ModuleRegistry();
        var weather = registry.register(new Weather());
        weather.mode.set("Halloween"); weather.setEnabled(true);
        weather.time.set(12.0);
        assertEquals(18000,weather.timeOfDay());
        assertTrue(weather.hasFog()); assertFalse(weather.hasWetSurface());
        assertEquals(0,weather.precipitationStrength());
        assertTrue(weather.halloweenParticles.visible()); assertTrue(weather.fogDistance.visible());
        assertFalse(weather.time.visible()); assertFalse(weather.intensity.visible());
        var fog = new FogData(); fog.renderDistanceEnd = 128;
        weather.fogDistance.set(64.0); weather.applyFog(fog);
        assertEquals(64,fog.renderDistanceEnd); assertEquals(64,fog.skyEnd);
        assertTrue(fog.color.x < .2f && fog.color.x > fog.color.y && fog.color.z > fog.color.y);
        fog.renderDistanceEnd = 24; weather.applyFog(fog); assertEquals(24,fog.renderDistanceEnd);
        weather.halloweenParticles.set(170.0);
        Path file = directory.resolve("halloween.json"); ClientConfig.saveProfile(file,registry);
        weather.mode.set("Дождь"); weather.halloweenParticles.set(0.0);
        assertTrue(new ClientConfig(file,registry).load());
        assertTrue(weather.isHalloween()); assertEquals(170,weather.halloweenParticles.get());
        weather.mode.set("Снег");
        assertEquals(6000,weather.timeOfDay()); assertTrue(weather.time.visible());
        assertFalse(weather.halloweenParticles.visible());
        weather.setEnabled(false);
    }
    @Test void twoModulesRoundTripTheirModesAndSettings() throws Exception {
        var registry=new ModuleRegistry();var weather=registry.register(new Weather());
        var speed=registry.register(new Speed());
        weather.mode.set("Снег");weather.snowDepth.set(175.0);weather.fallSpeed.set(2.4);weather.time.set(23.75);
        weather.flakeSize.set(4.2);weather.winterFog.set(false);weather.winterFogDistance.set(120.0);
        ((ModeSetting)setting(speed,"Режим")).set("Timer");
        ((NumberSetting)setting(speed,"Скорость в воздухе")).set(2.75);
        double savedAirSpeed=((NumberSetting)setting(speed,"Скорость в воздухе")).get();
        Path file=directory.resolve("ported-modules.json");ClientConfig.saveProfile(file,registry);
        weather.mode.set("Дождь");weather.snowDepth.set(20.0);((ModeSetting)setting(speed,"Режим")).set("Elytra");
        weather.flakeSize.set(1.0);weather.winterFog.set(true);weather.winterFogDistance.set(96.0);
        assertTrue(new ClientConfig(file,registry).load());
        assertEquals("Снег",weather.mode.get());assertEquals(175,weather.snowDepth.get());assertEquals(2.4,weather.fallSpeed.get(),.0001);assertEquals(23.75,weather.time.get());
        assertEquals(4.2,weather.flakeSize.get(),.0001);assertFalse(weather.winterFog.enabled());assertEquals(120,weather.winterFogDistance.get());
        assertEquals("Timer",setting(speed,"Режим").get());assertEquals(savedAirSpeed,((NumberSetting)setting(speed,"Скорость в воздухе")).get());
    }
    @Test void speedRetainsAllModesAndReleasesItsTimerAndTransactionsOnDisconnect() throws Exception {
        var speed=new Speed();var mode=(ModeSetting)setting(speed,"Режим");
        assertEquals(List.of("EntityBoost","SvinNew","Timer","Elytra"),mode.modes());
        speed.setEnabled(true);mode.set("Timer");
        invoke(speed,"beginCharging");assertEquals(.05f,TimerSpeed.multiplier());
        invoke(speed,"beginBoosting");assertEquals(1.7f,TimerSpeed.multiplier());
        field(Speed.class,"timerCycleActive").set(speed,true);
        var event=new EventPacketSend(new ServerboundPongPacket(17));speed.onPacketSend(event);assertTrue(event.cancelled());
        var queue=(Queue<?>)field(Speed.class,"delayedTransactions").get(speed);assertEquals(1,queue.size());
        speed.onDisconnect(new EventDisconnect());assertTrue(queue.isEmpty());assertEquals(1,TimerSpeed.multiplier());
        assertFalse(field(Speed.class,"timerCycleActive").getBoolean(speed));
        speed.setEnabled(false);
        var disabled=new EventPacketSend(new ServerboundPongPacket(18));speed.onPacketSend(disabled);assertFalse(disabled.cancelled());
    }
    @Test void sourceSpritesAndAllNewShaderResourcesResolve() throws Exception {
        for(String asset:List.of("textures/weather/flake.png"))
            try(var in=getClass().getResourceAsStream("/assets/coliseum/"+asset)){assertNotNull(in);assertNotNull(javax.imageio.ImageIO.read(in));}
        for(var pipeline:VisualPipelineAudit.collect()) if(pipeline.getLocation().getPath().startsWith("pipeline/weather/"))
            for(var shader:pipeline.getShaders().entrySet()) {
                var id=shader.getValue();String suffix=shader.getKey()==com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX?".vsh":".fsh";
                try(var in=getClass().getResourceAsStream("/assets/"+id.getNamespace()+"/shaders/"+id.getPath()+suffix)){assertNotNull(in);}
            }
    }
    private static Setting<?> setting(morfeov.coliseum.module.Module m,String name){return m.settings().stream().filter(s->s.name().equals(name)).findFirst().orElseThrow();}
    private static Field field(Class<?> type,String name)throws Exception{var field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void invoke(Object o,String name)throws Exception{var method=o.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(o);}
}
