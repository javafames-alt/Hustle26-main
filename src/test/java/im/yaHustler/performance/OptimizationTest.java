package im.yaHustler.performance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.module.impl.render.Optimization;
import im.yaHustler.setting.BooleanSetting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class OptimizationTest {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(OptimizationTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 24 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Optimization verification failed");
    }

    @Test void megaFpsOnlyChangesDisplayWhileBothTogglesAreEnabled() {
        var optimization = new Optimization();
        var mega = (BooleanSetting)optimization.settings().stream()
                .filter(s -> s.name().equals("MEGA FPS")).findFirst().orElseThrow();
        assertEquals(144, optimization.displayFps(144));
        mega.set(true);
        assertEquals(144, optimization.displayFps(144));
        optimization.setEnabled(true);
        int decorative = optimization.displayFps(144);
        assertEquals(14400, decorative);
        int zeroActual = optimization.displayFps(0);
        assertEquals(0, zeroActual);
        mega.set(false);
        assertEquals(144, optimization.displayFps(144));
        mega.set(true);
        optimization.setEnabled(false);
        assertEquals(144, optimization.displayFps(144));
    }

    @Test void megaFpsRemainsStableInAStableSceneAndIsSharedAcrossCounters() {
        var counter = new MegaFpsCounter();
        int previous = counter.sample(0, 144), minimum = previous, maximum = previous;
        for (int i = 1; i <= 1000; i++) {
            long now = i * MegaFpsCounter.INTERVAL_NANOS;
            assertEquals(previous, counter.sample(now - 1, 144));
            int next = counter.sample(now, 144);
            assertTrue(Math.abs(next - previous) <= 2);
            assertEquals(next, counter.sample(now, 144));
            assertTrue(next >= 1500 && next <= 3000);
            minimum = Math.min(minimum, next); maximum = Math.max(maximum, next);
            previous = next;
        }
        assertTrue(maximum - minimum <= 5);
        assertTrue(maximum > minimum);
    }

    @Test void megaFpsGraduallyFallsUnderLoadAndRecoversWithoutJumps() {
        var counter = new MegaFpsCounter();
        int start = counter.sample(0, 144), previous = start;
        for (int i = 1; i <= 40; i++) {
            int next = counter.sample(i * MegaFpsCounter.INTERVAL_NANOS, 50);
            assertTrue(Math.abs(next - previous) <= 10);
            if (i == 1) assertTrue(start - next <= 10);
            previous = next;
        }
        assertTrue(previous < start - 250);
        int low = previous;
        for (int i = 41; i <= 100; i++) {
            int next = counter.sample(i * MegaFpsCounter.INTERVAL_NANOS, 144);
            assertTrue(Math.abs(next - previous) <= 10);
            previous = next;
        }
        assertTrue(previous > low + 250);
        assertTrue(Math.abs(previous - start) < 10);
    }

    @Test void measuredFrameLoadWorksEvenWhenTheVanillaFpsCounterHasNotUpdated() {
        var counter = new MegaFpsCounter();
        long now = 0;
        counter.recordFrame(now);
        for (int i = 0; i < 300; i++) { now += 7_000_000; counter.recordFrame(now); counter.sample(now, 144); }
        int before = counter.sample(now, 144);
        for (int i = 0; i < 300; i++) { now += 25_000_000; counter.recordFrame(now); counter.sample(now, 144); }
        int after = counter.sample(now, 144);
        assertTrue(after < before - 200);
        // A long pause must not cause a jump when the display resumes.
        assertTrue(Math.abs(counter.sample(now + 10_000_000_000L, 1) - after) <= 13);
    }

    @Test void blurBoundsContainEveryRotatedCornerAtFractionalScales() {
        for (float angle : new float[]{0, .4f, -.7f, 1.57f}) {
            float[][] panels = {{30, 20, 40, 18, 5, angle}};
            var bounds = im.yaHustler.ui.BackdropRegion.of(120, 80, panels, 301, 199, 0);
            for (int dx : new int[]{-1, 1}) for (int dy : new int[]{-1, 1}) {
                double x = (50 + Math.cos(angle) * 20 * dx - Math.sin(angle) * 9 * dy) * 301 / 120;
                double y = (29 + Math.sin(angle) * 20 * dx + Math.cos(angle) * 9 * dy) * 199 / 80;
                assertTrue(x >= bounds.x() && x <= bounds.x() + bounds.width());
                assertTrue(y >= bounds.y() && y <= bounds.y() + bounds.height());
            }
        }
    }

    @Test void blurBoundsClampOffscreenPanelsAndAvoidFullScreenWorkForSmallHud() {
        var bounds = im.yaHustler.ui.BackdropRegion.of(960, 540,
                new float[][]{{6, 6, 172, 21, 6}}, 1920, 1080, 2);
        assertTrue((long)bounds.width() * bounds.height() < 1920L * 1080 / 20);
        var offscreen = im.yaHustler.ui.BackdropRegion.of(960, 540,
                new float[][]{{-100, -100, 2000, 2000, 6}}, 1920, 1080, 20);
        assertEquals(new im.yaHustler.ui.BackdropRegion(0, 0, 1920, 1080), offscreen);
        var empty = im.yaHustler.ui.BackdropRegion.of(960, 540, new float[0][], 1920, 1080, 20);
        assertEquals(0, empty.width() * empty.height());
    }

    @Test void weatherCullingPreservesVisibleFlakeCornersWithReversedAndInfiniteDepth() {
        var random = new java.util.Random(81);
        int visibleCorners = 0;
        for (boolean zeroToOne : new boolean[]{false, true})
            for (float[] depth : new float[][]{{.05f, 1000}, {.05f, Float.POSITIVE_INFINITY}, {1000, .05f}, {Float.POSITIVE_INFINITY, .05f}}) {
                var projection = new org.joml.Matrix4f().perspective((float)Math.toRadians(90), 16f / 9, depth[0], depth[1], zeroToOne);
                var frustum = new im.yaHustler.render.weather.WeatherFrustum();
                frustum.update(projection, new org.joml.Matrix4f());
                assertTrue(frustum.box(-1, -1, -10, 1, 1, -5));
                for (int i = 0; i < 1000; i++) {
                    float x = random.nextFloat() * 50 - 25, y = random.nextFloat() * 30 - 15, z = random.nextFloat() * 40 - 20;
                    float half = .01f + random.nextFloat() * .5f, stretch = i % 2 == 0 ? 1 : 22;
                    float angle = stretch == 1 ? random.nextFloat() * 6.28f : 0;
                    float c = (float)Math.cos(angle) * half, s = (float)Math.sin(angle) * half;
                    // Include skewed rain direction as well as ordinary camera billboards.
                    var up = new org.joml.Vector3f(stretch == 1 ? 0 : -.3f, 1, 0).normalize();
                    for (int a : new int[]{-1, 1}) for (int b : new int[]{-1, 1}) {
                        float px = x + (c + up.x * s) * a + (up.x * c - s) * stretch * b;
                        float py = y + up.y * s * a + up.y * c * stretch * b;
                        var clip = projection.transform(new org.joml.Vector4f(px, py, z, 1));
                        if (clip.w > 0 && Math.abs(clip.x) <= clip.w && Math.abs(clip.y) <= clip.w
                                && clip.z >= (zeroToOne ? 0 : -clip.w) && clip.z <= clip.w) {
                            assertTrue(frustum.particle(x, y, z, half, stretch), "Visible flake corner was culled");
                            visibleCorners++;
                        }
                    }
                }
            }
        assertTrue(visibleCorners > 100);
    }

    @Test void weatherCullingRejectsWorkBehindCameraAndKeepsIntersectingLargeFlakes() {
        var frustum = new im.yaHustler.render.weather.WeatherFrustum();
        frustum.update(new org.joml.Matrix4f().perspective((float)Math.toRadians(90), 1, .05f, 100), new org.joml.Matrix4f());
        assertFalse(frustum.particle(0, 0, 10, .1f, 1));
        assertFalse(frustum.particle(100, 0, -5, .1f, 1));
        assertTrue(frustum.particle(5.5f, 0, -5, .5f, 1));
        assertTrue(frustum.box(-1, -1, -10, 1, 1, -5));
    }

    @Test void defaultsPreserveGraphicsAndGeometryCacheFollowsModuleLifecycle() {
        var optimization = new Optimization();
        for (String name : List.of("Меньше частиц", "Без облаков", "Без теней существ",
                "Скрывать дальние предметы", "Лёгкое размытие GUI")) {
            assertFalse(((BooleanSetting)optimization.settings().stream()
                    .filter(s -> s.name().equals(name)).findFirst().orElseThrow()).enabled(), name);
        }
        assertFalse(optimization.cacheSnowGeometry());
        optimization.setEnabled(true);
        assertTrue(optimization.cacheSnowGeometry());
        assertFalse(optimization.fastGuiBlur());
        assertFalse(optimization.cullItem(1_000_000));
        optimization.setEnabled(false);
        assertFalse(optimization.cacheSnowGeometry());
    }

    @Test void cachedSnowProducesExactlyTheSameVerticesAndColors() throws Exception {
        var type = im.yaHustler.render.weather.HustleSnowRenderer.class;
        var renderer = new im.yaHustler.render.weather.HustleSnowRenderer();
        var surfaceType = Class.forName(type.getName() + "$Surface");
        var constructor = surfaceType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var height = type.getDeclaredMethod("height", int.class, int.class); height.setAccessible(true);
        var append = type.getDeclaredMethod("appendSurface", surfaceType, net.minecraft.world.phys.Vec3.class,
                float.class, float.class, boolean.class); append.setAccessible(true);
        var vertices = type.getDeclaredField("vertices"); vertices.setAccessible(true);
        var elevationsField = type.getDeclaredField("elevations"); elevationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var elevations = (java.util.Map<Long, Double>)elevationsField.get(renderer);
        var buffer = java.nio.ByteBuffer.allocate(4096);
        vertices.set(renderer, buffer);
        for (int x : new int[]{-10000, -1, 0, 12345}) for (int edges = 0; edges < 16; edges++) {
            int z = -x; double y = 64.5;
            elevations.clear();
            int[][] neighbours = {{x,z-1}, {x,z+1}, {x-1,z}, {x+1,z}};
            for (int i = 0; i < 4; i++) if ((edges & (1 << i)) == 0)
                elevations.put(((long)neighbours[i][0] << 32) ^ (neighbours[i][1] & 0xffffffffL), y);
            var surface = constructor.newInstance(x, y, z, height.invoke(null,x,z), height.invoke(null,x+1,z),
                    height.invoke(null,x+1,z+1), height.invoke(null,x,z+1), edges);
            for (float depth : new float[]{0, .2f, 2}) for (float alpha : new float[]{0, .35f, 1}) {
                var origin = new net.minecraft.world.phys.Vec3(x + .123, 65.1, z - .321);
                buffer.clear(); append.invoke(renderer, surface, origin, depth, alpha, false);
                byte[] original = java.util.Arrays.copyOf(buffer.array(), buffer.position());
                buffer.clear(); append.invoke(renderer, surface, origin, depth, alpha, true);
                assertArrayEquals(original, java.util.Arrays.copyOf(buffer.array(), buffer.position()));
            }
        }
        // The test uses a heap buffer; no native allocation or graphics device is needed.
        vertices.set(renderer, null);
        renderer.close();
    }

    @Test void restoresOriginalGraphicsValueOnce() {
        var value = new AtomicReference<>("fancy");
        var writes = new AtomicInteger();
        var override = new OptionOverride<>(value::get, v -> { value.set(v); writes.incrementAndGet(); });
        for (int i = 0; i < 100; i++) override.update(true, "off");
        assertEquals("off", value.get()); assertEquals(1, writes.get());
        override.restore(); override.restore();
        assertEquals("fancy", value.get()); assertEquals(2, writes.get());
    }

    @Test void preservesManualChangesDuringOptimization() {
        var value = new AtomicReference<>("all");
        var override = new OptionOverride<>(value::get, value::set);
        override.update(true, "decreased");
        value.set("minimal");
        override.update(true, "decreased"); override.restore();
        assertEquals("minimal", value.get());
    }

    @Test void individualToggleRestoresAndRecapturesThePreference() {
        var value = new AtomicReference<>(true);
        var override = new OptionOverride<>(value::get, value::set);
        override.update(true, false); override.update(false, false);
        assertTrue(value.get());
        value.set(false);
        override.update(true, false); override.restore();
        assertFalse(value.get());
    }

    @Test void restorationUsesTheActualAcceptedOptionValue() {
        var value = new AtomicInteger(8);
        var override = new OptionOverride<>(value::get, v -> value.set(Math.clamp(v, 0, 16)));
        override.update(true, 24); assertEquals(16, value.get());
        override.restore(); assertEquals(8, value.get());
    }

    @Test void cachedMissIsInvalidatedWhenAModuleIsRegistered() {
        var registry = new ModuleRegistry();
        assertNull(registry.get(Other.class)); assertNull(registry.get(Other.class));
        var module = registry.register(new Other());
        assertSame(module, registry.get(Other.class));
    }

    @Test void cachedLookupPreservesFirstAssignableModuleSemantics() {
        var registry = new ModuleRegistry();
        var first = registry.register(new Derived());
        assertSame(first, registry.get(Base.class));
        registry.register(new Base());
        assertSame(first, registry.get(Base.class)); assertSame(first, registry.get(Module.class));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Base()));
    }

    @Test void changesInvalidateGuiSearchWithoutReplacingPersistenceListeners() {
        var registry = new ModuleRegistry();
        var module = registry.register(new Base());
        long before = registry.revision();
        var saves = new AtomicInteger(); registry.onChanged(saves::incrementAndGet);
        module.setFavorite(true);
        assertTrue(registry.revision() > before); assertEquals(1, saves.get());
        assertEquals(List.of(module), registry.search("BASE", true));
        module.setFavorite(false);
        assertTrue(registry.search("BASE", true).isEmpty()); assertEquals(2, saves.get());
    }

    @Test void searchStillGroupsByCategoryAndFindsDescriptions() {
        var registry = new ModuleRegistry();
        var other = registry.register(new Other()); var base = registry.register(new Base());
        assertEquals(List.of(base, other), registry.search("", false));
        assertEquals(List.of(other), registry.visible(Category.MISC, "test description", false));
    }

    @Test void weatherPatternPreservesEverySampleAcrossMovementAndDensityChanges() {
        var pattern=new im.yaHustler.render.weather.PrecipitationPattern();
        for(int x:new int[]{-10000,-1,0,314})for(int layers:new int[]{1,6,12}) {
            int z=-x;
            assertTrue(pattern.update(x,z,layers));
            assertFalse(pattern.update(x,z,layers));
            int i=0;
            for(int gx=x-6;gx<=x+6;gx++)for(int gz=z-6;gz<=z+6;gz++)for(int l=0;l<layers;l++,i++) {
                assertEquals(originalRandom(gx,gz,l),pattern.seed(i));
                assertEquals(originalRandom(gx,gz,l+17)*Math.PI*2,pattern.phase(i));
                assertEquals(originalRandom(gz,gx,l+9)*2.5,pattern.offset(i));
                assertEquals(Math.min(3,(int)(originalRandom(gx,gz,l+31)*4)),pattern.sprite(i));
            }
        }
        pattern.clear();assertTrue(pattern.update(314,-314,12));
    }
    private static double originalRandom(int x,int z,int layer) {
        long h=x*374761393L+z*668265263L+layer*1274126177L;h=(h^(h>>13))*1274126177L;
        return ((h^(h>>16))&0xffffff)/(double)0xffffff;
    }
    @Test void subscriptionsKeepInheritedAndLegacyHooksWithoutEmptyHandlersOrDuplicateCalls() {
        var registry=new ModuleRegistry();registry.register(new Base());
        var subscriptions=new im.yaHustler.port.PortSubscriptions(registry);
        var receive=im.yaHustler.port.PortSubscriptions.Hook.RECEIVE;
        assertEquals(0,subscriptions.handlers(receive).length);
        var inherited=registry.register(new ChildReceiver());var direct=registry.register(new DirectReceiver());
        assertArrayEquals(new Object[]{inherited,direct},subscriptions.handlers(receive));
        var cached=subscriptions.handlers(receive);
        inherited.setEnabled(true);direct.setEnabled(true);direct.setFavorite(true);
        assertSame(cached,subscriptions.handlers(receive));
        for(var port:cached)if(port.enabled())port.onPacket(new im.yaHustler.port.event.EventPacketReceive(null));
        assertEquals(1,inherited.packets);assertEquals(10,direct.packets);
        inherited.setEnabled(false);
        for(var port:cached)if(port.enabled())port.onPacket(new im.yaHustler.port.event.EventPacketReceive(null));
        assertEquals(1,inherited.packets);assertEquals(20,direct.packets);
        assertEquals(0,subscriptions.handlers(im.yaHustler.port.PortSubscriptions.Hook.SEND).length);
        // Disconnect must retain disabled subscribers too.
        for(var port:subscriptions.handlers(im.yaHustler.port.PortSubscriptions.Hook.DISCONNECT))port.onDisconnect(null);
        assertEquals(1,inherited.disconnects);assertEquals(1,direct.disconnects);
    }
    @Test void tickCacheKeepsInheritedMethodsAndLiveEnabledStateAfterRegistration() {
        var registry=new ModuleRegistry();registry.register(new Base()).setEnabled(true);
        var child=registry.register(new ChildTicker());
        registry.tick(null);assertEquals(0,child.ticks);
        child.setEnabled(true);registry.tick(null);assertEquals(1,child.ticks);
        var later=registry.register(new Ticker());later.setEnabled(true);
        registry.tick(null);assertEquals(2,child.ticks);assertEquals(1,later.ticks);
        child.setEnabled(false);registry.tick(null);assertEquals(2,child.ticks);assertEquals(2,later.ticks);
    }
    @Test void pumpkinsHaveRealVolumeOutwardWindingAndConservativeRotationBounds() {
        int count=im.yaHustler.render.weather.PumpkinMesh.VERTEX_COUNT;
        assertTrue(count>36&&count<=1600);
        var data=java.nio.ByteBuffer.allocate(count*24).order(java.nio.ByteOrder.nativeOrder());
        im.yaHustler.render.weather.PumpkinMesh.append(data,0,0,0,1,0,0,1,1);
        assertEquals(count*24,data.position());
        for(int i=0;i<count;i+=3) {
            var a=new org.joml.Vector3f(data.getFloat(i*24),data.getFloat(i*24+4),data.getFloat(i*24+8));
            var b=new org.joml.Vector3f(data.getFloat((i+1)*24),data.getFloat((i+1)*24+4),data.getFloat((i+1)*24+8));
            var c=new org.joml.Vector3f(data.getFloat((i+2)*24),data.getFloat((i+2)*24+4),data.getFloat((i+2)*24+8));
            var normal=new org.joml.Vector3f(b).sub(a).cross(new org.joml.Vector3f(c).sub(a));
            assertTrue(normal.lengthSquared()>1e-8);
            var center=new org.joml.Vector3f(a).add(b).add(c).div(3);
            if((data.get(i*24+22)&255)>100)center.sub(.045f,.975f,0);
            assertTrue(normal.dot(center)>0,"Inward pumpkin/stem triangle "+i);
        }
        for(float yaw:new float[]{0,.4f,1.7f,3.14f})for(float tilt:new float[]{-.2f,0,.2f}) {
            data.clear();im.yaHustler.render.weather.PumpkinMesh.append(data,0,0,0,1,yaw,tilt,.5f,.8f);
            for(int i=0;i<count;i++) {
                float x=data.getFloat(i*24),y=data.getFloat(i*24+4),z=data.getFloat(i*24+8);
                assertTrue(x*x+y*y+z*z<1.4f*1.4f);
                assertEquals(128,data.get(i*24+23)&255);
            }
        }
    }
    public static class Receiver extends im.yaHustler.port.PortModule {
        int packets,disconnects;
        @Override public void onPacketReceive(im.yaHustler.port.event.EventPacketReceive e){packets++;}
        @Override public void onDisconnect(im.yaHustler.port.event.EventDisconnect e){disconnects++;}
    }
    @ModuleInfo(id="child_receive",name="Child",description="",category=Category.MISC)
    public static final class ChildReceiver extends Receiver {}
    @ModuleInfo(id="direct_receive",name="Direct",description="",category=Category.MISC)
    public static final class DirectReceiver extends Receiver {
        @Override public void onPacket(im.yaHustler.port.event.EventPacketReceive e){packets+=10;}
    }
    @ModuleInfo(id="ticker",name="Ticker",description="",category=Category.MISC)
    public static class Ticker extends Module {
        int ticks;
        @Override public void tick(net.minecraft.client.Minecraft mc){ticks++;}
    }
    @ModuleInfo(id="child_ticker",name="ChildTicker",description="",category=Category.MISC)
    public static final class ChildTicker extends Ticker {}

    @Test void noRenderDefaultsAndParticleFiltersOnlyApplyWhileEnabled() {
        var module=new im.yaHustler.module.impl.render.NoRender();
        assertFalse(im.yaHustler.module.impl.render.NoRender.noFireOverlay());
        module.setEnabled(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.noFireOverlay());
        assertTrue(im.yaHustler.module.impl.render.NoRender.noHurtCam());
        assertFalse(im.yaHustler.module.impl.render.NoRender.noWeather());
        module.hitParticles.set(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.hideParticle(net.minecraft.core.particles.ParticleTypes.CRIT));
        assertFalse(im.yaHustler.module.impl.render.NoRender.hideParticle(net.minecraft.core.particles.ParticleTypes.FLAME));
        module.explosionParticles.set(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.hideParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION));
        module.allParticles.set(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.hideParticle(net.minecraft.core.particles.ParticleTypes.FLAME));
        module.setEnabled(false);
        assertFalse(im.yaHustler.module.impl.render.NoRender.hideParticle(net.minecraft.core.particles.ParticleTypes.CRIT));
    }
    @Test void noRenderHiddenHandsAndBadEffectsRemainIndependentOfGameplayStatus() {
        var module=new im.yaHustler.module.impl.render.NoRender();
        module.setEnabled(true);
        var main=(BooleanSetting)module.settings().stream().filter(s->s.name().equals("Скрывать: Главная рука")).findFirst().orElseThrow();
        main.set(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.noHand(net.minecraft.world.InteractionHand.MAIN_HAND));
        assertFalse(im.yaHustler.module.impl.render.NoRender.noHands());
        module.badEffects.set(true);
        assertTrue(im.yaHustler.module.impl.render.NoRender.noDarkness());
        assertTrue(im.yaHustler.module.impl.render.NoRender.noNausea());
        module.setEnabled(false);
        assertFalse(im.yaHustler.module.impl.render.NoRender.noDarkness());
        assertFalse(im.yaHustler.module.impl.render.NoRender.noMainHand());
    }
    @ModuleInfo(id="test_base", name="Base", description="", category=Category.COMBAT)
    public static class Base extends Module {}
    @ModuleInfo(id="test_derived", name="Derived", description="", category=Category.RENDER)
    public static final class Derived extends Base {}
    @ModuleInfo(id="test_other", name="Other", description="Test description", category=Category.MISC)
    public static final class Other extends Module {}
}


