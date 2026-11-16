package im.yaHustler.ui;

import java.nio.file.Path;
import java.util.List;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.module.impl.movement.BoosterAngleDisplay;
import im.yaHustler.module.impl.movement.ElytraBooster;
import im.yaHustler.module.impl.movement.ElytraBooster.BoostFactors;
import im.yaHustler.setting.Setting;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ElytraBoosterCheck {
    @TempDir Path directory;

    @Test void donorPresetsKeepTheirAngleBoundariesAndSeparateBravoAxes() {
        var booster = new ElytraBooster();
        assertFactors(booster.factors(0, 0), 1.6f, 1.6f);
        assertFactors(booster.factors(10, 0), 1.63f, 1.6f);
        assertFactors(booster.factors(0, 10), 1.63f, 1.62f);
        assertFactors(booster.factors(0, 40), 1.9f, 1.9f);
        assertFactors(booster.factors(0, 42.999f), 2.16f, 2.16f);
        assertFactors(booster.factors(0, 43), 2.26f, 2.26f);
        assertFactors(booster.factors(0, 45), 2.26f, 2.26f);
        booster.mode.set(ElytraBooster.REALLY_WORLD_121);
        assertFactors(booster.factors(30, 30), 1.53f, 1.53f);
        assertFactors(booster.factors(30.001f, 0), 2.04f, 1.53f);
        assertFactors(booster.factors(40, 40), 2.04f, 2.02f);
        assertFactors(booster.factors(40.001f, 0), 2.059f, 1.53f);
        booster.mode.set(ElytraBooster.BRAVO);
        assertFactors(booster.factors(0, 0), 1.64f, 1.65f);
        assertFactors(booster.factors(10, 10), 1.67f, 1.7f);
        assertFactors(booster.factors(30, 30), 1.87f, 1.91f);
        assertFactors(booster.factors(45, 0), 2.05f, 1.65f);
    }

    @Test void boostingIsSymmetricAcrossNegativeAnglesAndFullRotations() {
        var booster = new ElytraBooster();
        for (String mode : booster.mode.modes()) {
            booster.mode.set(mode);
            for (float angle : new float[]{0, 5, 10, 20, 30, 40, 42, 43, 45}) {
                BoostFactors expected = booster.factors(angle, angle);
                assertEquals(expected, booster.factors(-angle, -angle));
                assertEquals(expected, booster.factors(180 + angle, -180 - angle));
                assertEquals(expected, booster.factors(360 + angle, -360 - angle));
                assertEquals(expected, booster.factors(90 - angle, 90 - angle));
            }
        }
        assertEquals(-1, ElytraBooster.foldedAngle(179));
        assertEquals(1, ElytraBooster.foldedAngle(-179));
        assertEquals(90, ElytraBooster.foldedAngle(90));
        assertEquals(-90, ElytraBooster.foldedAngle(-90));
    }

    @Test void customBandsAndAngleOverlayRoundTripThroughProfiles() throws Exception {
        var registry = new ModuleRegistry();
        var booster = registry.register(new ElytraBooster());
        assertEquals(List.of(ElytraBooster.REALLY_WORLD_116, ElytraBooster.REALLY_WORLD_121,
            ElytraBooster.BRAVO, ElytraBooster.CUSTOM), booster.mode.modes());
        assertFalse(booster.horizontalBoost[0].visible());
        booster.mode.set(ElytraBooster.CUSTOM);
        for (int band = 0; band < 9; band++) {
            booster.horizontalBoost[band].set(1.5 + band * .1);
            booster.verticalBoost[band].set(1.6 + band * .1);
            assertTrue(booster.horizontalBoost[band].visible());
            assertTrue(booster.verticalBoost[band].visible());
        }
        booster.showAngles.set(true);
        Path profile = directory.resolve("booster.json");
        ClientConfig.saveProfile(profile, registry);
        booster.horizontalBoost[8].set(1.5);
        booster.verticalBoost[0].set(1.5);
        booster.mode.set(ElytraBooster.BRAVO);
        booster.showAngles.set(false);
        assertTrue(new ClientConfig(profile, registry).load());
        assertEquals(ElytraBooster.CUSTOM, booster.mode.get());
        assertTrue(booster.showAngles.enabled());
        assertFactors(booster.factors(45, 0), 2.3f, 1.6f);
        assertFactors(booster.factors(0, 45), 2.4f, 2.4f);
        assertEquals(20, booster.settings().size());
        assertEquals(booster.settings().size(), booster.settings().stream().map(Setting::name).distinct().count());
    }

    @Test void rocketFormulaRetainsVanillaDampingAndUsesHorizontalForXAndZ() {
        Vec3 velocity = new Vec3(.3, -.2, .7);
        Vec3 direction = new Vec3(.2, -.4, .8);
        Vec3 vanilla = ElytraBooster.accelerate(velocity, direction, new BoostFactors(1.5f, 1.5f));
        assertEquals(velocity.x + direction.x * .1 + (direction.x * 1.5 - velocity.x) * .5, vanilla.x, 1e-12);
        assertEquals(velocity.y + direction.y * .1 + (direction.y * 1.5 - velocity.y) * .5, vanilla.y, 1e-12);
        assertEquals(velocity.z + direction.z * .1 + (direction.z * 1.5 - velocity.z) * .5, vanilla.z, 1e-12);
        Vec3 boosted = ElytraBooster.accelerate(velocity, direction, new BoostFactors(2, 2.5f));
        assertEquals(direction.x * .25, boosted.x - vanilla.x, 1e-12);
        assertEquals(direction.y * .5, boosted.y - vanilla.y, 1e-12);
        assertEquals(direction.z * .25, boosted.z - vanilla.z, 1e-12);
        var booster = new ElytraBooster();
        assertFalse(ElytraBooster.appliesTo(null));
        booster.setEnabled(true);
        assertFalse(ElytraBooster.appliesTo(null));
        booster.setEnabled(false);
    }

    @Test void angleDigitsWaitForTheCurrentRollAndUseTheLatestPendingAngle() {
        var display = new BoosterAngleDisplay();
        long start = 1_000_000_000;
        display.update(10, start);
        display.update(12, start + 1);
        assertEquals("12", display.text());
        assertEquals("0", display.digits().get(1).previous());
        assertEquals("2", display.digits().get(1).current());
        assertEquals(0, display.digits().get(1).progress(start + 1));
        display.update(17, start + BoosterAngleDisplay.ROLL_NANOS / 2);
        display.update(19, start + BoosterAngleDisplay.ROLL_NANOS - 1);
        assertEquals("12", display.text());
        assertTrue(display.digits().get(1).progress(start + BoosterAngleDisplay.ROLL_NANOS) < 1);
        display.update(19, start + BoosterAngleDisplay.ROLL_NANOS);
        assertEquals("12", display.text(), "The last nanosecond must not open the next roll");
        display.update(19, start + BoosterAngleDisplay.ROLL_NANOS + 1);
        assertEquals("19", display.text());
        assertEquals("2", display.digits().get(1).previous());
        display.update(-90, start + BoosterAngleDisplay.ROLL_NANOS * 2 + 1);
        assertEquals("-90", display.text());
        assertEquals(1, display.digits().get(0).progress(start + BoosterAngleDisplay.ROLL_NANOS * 2 + 1));
        display.clear();
        assertEquals("", display.text());
        assertTrue(display.digits().isEmpty());
        for (int frames : new int[]{1, 12, 60, 144}) {
            var sampled = new BoosterAngleDisplay();
            sampled.update(10, start);
            sampled.update(12, start + 1);
            for (int frame = 1; frame < frames; frame++) {
                long now = start + 1 + BoosterAngleDisplay.ROLL_NANOS * frame / frames;
                sampled.update(19, now);
                assertEquals("12", sampled.text());
            }
            sampled.update(19, start + BoosterAngleDisplay.ROLL_NANOS);
            assertEquals("12", sampled.text());
            sampled.update(19, start + BoosterAngleDisplay.ROLL_NANOS + 1);
            assertEquals("19", sampled.text());
        }
    }

    private static void assertFactors(BoostFactors factors, float horizontal, float vertical) {
        assertEquals(horizontal, factors.horizontal(), .000001f);
        assertEquals(vertical, factors.vertical(), .000001f);
    }
}
