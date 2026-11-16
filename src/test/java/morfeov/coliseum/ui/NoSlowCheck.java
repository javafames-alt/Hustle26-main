package morfeov.coliseum.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.movement.NoSlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class NoSlowCheck {
    @TempDir Path directory;

    @Test void allModesPersistThroughProfiles() throws Exception {
        var registry = new ModuleRegistry();
        var module = registry.register(new NoSlow());
        assertEquals(List.of("Vanilla", "Grim Ticks", "Grim New", "Reallyworld", "Funtime арбалет", "Coliseum Grim New", "Coliseum Grim Old"), module.mode.modes());
        for (String mode : module.mode.modes()) {
            module.mode.set(mode);
            var profile = directory.resolve("noslow.json");
            ClientConfig.saveProfile(profile, registry);
            module.mode.set(mode.equals(NoSlow.VANILLA) ? NoSlow.GRIM_NEW : NoSlow.VANILLA);
            assertTrue(new ClientConfig(profile, registry).load());
            assertEquals(mode, module.mode.get());
        }
    }

    @Test void reallyworldKeepsTheTwoThreeThreeCadenceAndRestartsAfterUseStops() {
        var cycle = new NoSlow.UseCycle();
        cycle.reset(0);
        var pulses = new ArrayList<Integer>();
        for (int tick = 1; tick <= 16; tick++) {
            cycle.advance(NoSlow.REALLYWORLD, true, tick * 50L);
            if (cycle.pulse()) pulses.add(tick);
        }
        assertEquals(List.of(2, 5, 8, 10, 13, 16), pulses);
        cycle.advance(NoSlow.REALLYWORLD, false, 850);
        cycle.advance(NoSlow.REALLYWORLD, true, 900);
        assertFalse(cycle.pulse());
        cycle.advance(NoSlow.REALLYWORLD, true, 950);
        assertTrue(cycle.pulse());
    }

    @Test void grimTicksUsesElapsedTimeAndCannotPulseWhileIdle() {
        var cycle = new NoSlow.UseCycle();
        cycle.reset(1000);
        cycle.advance(NoSlow.GRIM_TICKS, true, 1124);
        assertFalse(cycle.pulse());
        cycle.advance(NoSlow.GRIM_TICKS, true, 1125);
        assertTrue(cycle.pulse());
        cycle.advance(NoSlow.GRIM_TICKS, true, 1126);
        assertFalse(cycle.pulse());
        cycle.advance(NoSlow.GRIM_TICKS, false, 1400);
        assertFalse(cycle.pulse());
        cycle.advance(NoSlow.GRIM_TICKS, true, 1450);
        assertTrue(cycle.pulse());
        cycle.reset(1500);
        cycle.advance(NoSlow.GRIM_TICKS, true, 1500);
        assertFalse(cycle.pulse());
    }

    @Test void modesKeepTheirUseHandCrossbowWarmupAndGlidingRestrictions() {
        assertTrue(NoSlow.cancelSlowdown(NoSlow.VANILLA, true, false, false, 0, false, false));
        assertFalse(NoSlow.cancelSlowdown(NoSlow.GRIM_NEW, true, false, true, 1, false, false));
        assertTrue(NoSlow.cancelSlowdown(NoSlow.GRIM_NEW, true, false, true, 2, false, false));
        assertFalse(NoSlow.cancelSlowdown(NoSlow.GRIM_NEW, true, false, false, 20, false, false));
        assertTrue(NoSlow.cancelSlowdown(NoSlow.FUNTIME_CROSSBOW, true, false, false, 1, true, false));
        assertFalse(NoSlow.cancelSlowdown(NoSlow.FUNTIME_CROSSBOW, true, false, true, 20, false, true));
        for (String mode : new NoSlow().mode.modes()) {
            assertFalse(NoSlow.cancelSlowdown(mode, false, false, true, 20, true, true));
            assertFalse(NoSlow.cancelSlowdown(mode, true, true, true, 20, true, true));
        }
    }
}
