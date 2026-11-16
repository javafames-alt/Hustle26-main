package morfeov.coliseum.combat;

import morfeov.coliseum.module.impl.combat.crystal.CrystalProfile;
import morfeov.coliseum.module.impl.combat.crystal.CrystalProfile.Swap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class CrystalProfileTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(CrystalProfileTest.class),
                        org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(SilentRotationTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsFoundCount() == 0 || summary.getTotalFailureCount() != 0) throw new AssertionError("CrystalAura verification failed");
    }
    @Test void donorPresetsRetainTheirCombatLimits() {
        var grim = CrystalProfile.preset("Grim");
        assertEquals(4.5f, grim.placeRange()); assertEquals(3, grim.breakRange());
        assertEquals(1, grim.actions()); assertEquals(Swap.KEEP, grim.swap());
        assertTrue(grim.strictReach()); assertFalse(grim.instantBreak());
        var donut = CrystalProfile.preset("DonutSMP");
        assertEquals(50, donut.placeDelay()); assertEquals(50, donut.breakDelay()); assertEquals(35, donut.rotationSpeed());
        var unlimited = CrystalProfile.preset("Без ограничений");
        assertEquals(5.5f, unlimited.placeRange()); assertEquals(5, unlimited.breakRange());
        assertEquals(3, unlimited.actions()); assertTrue(unlimited.throughWalls()); assertTrue(unlimited.instantPlace());
        assertEquals(Swap.SILENT, unlimited.swap()); assertFalse(unlimited.strictReach());
        assertEquals(grim, CrystalProfile.preset("ReallyWorld"));
        assertEquals(grim, CrystalProfile.preset("Свой"));
    }

    @Test void firstActionHasNoDelayAndLaterActionsRespectServerTicks() {
        assertTrue(CrystalProfile.delayReady(0, Integer.MIN_VALUE, 500));
        assertFalse(CrystalProfile.delayReady(10, 10, 50));
        assertTrue(CrystalProfile.delayReady(11, 10, 50));
        assertFalse(CrystalProfile.delayReady(19, 10, 500));
        assertTrue(CrystalProfile.delayReady(20, 10, 500));
        assertTrue(CrystalProfile.delayReady(10, 10, 0));
    }

    @Test void lethalEnemyCannotOverrideSuicideMargin() {
        assertFalse(CrystalProfile.safe(8, 10, 4, true, true));
        assertTrue(CrystalProfile.safe(7.9f, 10, 4, true, true));
        assertFalse(CrystalProfile.safe(7.9f, 10, 4, true, false));
        assertFalse(CrystalProfile.safe(Float.NaN, 20, 8, false, true));
        assertFalse(CrystalProfile.safe(Float.POSITIVE_INFINITY, 20, 8, false, true));
        assertFalse(CrystalProfile.safe(0, 2, 8, true, true));
    }

    @Test void faceplaceOnlyLowersThresholdWhenHealthOrArmorWarrantsIt() {
        assertEquals(6, CrystalProfile.minimumDamage(6, 20, 8, false));
        assertEquals(2, CrystalProfile.minimumDamage(6, 8, 8, false));
        assertEquals(2, CrystalProfile.minimumDamage(6, 20, 8, true));
        assertEquals(1, CrystalProfile.minimumDamage(1, 8, 8, true));
    }

    @Test void scoreProtectsLowHealthAndRewardsConfirmedTotemWindows() {
        float healthy = CrystalProfile.score(8, 2, 1, 10, 20, false, false, 50, true);
        float low = CrystalProfile.score(8, 2, 1, 10, 4, false, false, 50, true);
        assertEquals(4.6f, healthy, .001f); assertTrue(low < healthy);
        float recentPop = CrystalProfile.score(12, 0, 0, 10, 20, true, true, 3, true);
        float oldPop = CrystalProfile.score(12, 0, 0, 10, 20, true, true, 30, true);
        assertEquals(4, recentPop - oldPop, .001f);
        assertTrue(CrystalProfile.score(12, 0, 0, 10, 20, true, false, 30, true) > recentPop);
    }

    @Test void presetAndActionBindSurviveAProfileRoundTrip() throws Exception {
        var registry = new morfeov.coliseum.module.ModuleRegistry();
        var aura = registry.register(new morfeov.coliseum.module.impl.combat.CrystalAura());
        var preset = (morfeov.coliseum.setting.ModeSetting) aura.settings().stream().filter(s -> s.name().equals("Пресет")).findFirst().orElseThrow();
        var bind = (morfeov.coliseum.setting.KeySetting) aura.settings().stream().filter(s -> s.name().equals("Клавиша Surround")).findFirst().orElseThrow();
        assertFalse(aura.enabled()); assertEquals("crystal_aura", aura.id());
        assertEquals(java.util.List.of("Grim", "DonutSMP", "Без ограничений"), preset.modes());
        preset.set("DonutSMP"); bind.set(-102); aura.setFavorite(true);
        java.nio.file.Path profile = directory.resolve("crystal.json");
        morfeov.coliseum.config.ClientConfig.saveProfile(profile, registry);
        preset.set("Grim"); bind.set(-1); aura.setFavorite(false);
        assertTrue(new morfeov.coliseum.config.ClientConfig(profile, registry).load());
        assertEquals("DonutSMP", preset.get()); assertEquals(-102, bind.get());
        assertTrue(aura.favorite()); assertFalse(aura.enabled());
    }

    @Test void spawnHookMatchesTheCurrentMinecraftMethod() throws Exception {
        try (var stream = getClass().getResourceAsStream("/net/minecraft/client/multiplayer/ClientLevel.class")) {
            assertNotNull(stream);
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(stream).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
            assertTrue(node.methods.stream().anyMatch(method -> method.name.equals("addEntity") && method.desc.equals("(Lnet/minecraft/world/entity/Entity;)V")));
        }
        try (var stream = getClass().getResourceAsStream("/coliseum.mixins.json")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.getAsJsonArray("client").asList().stream().anyMatch(value -> value.getAsString().equals("CrystalAuraLevelMixin")));
        }
    }
}
