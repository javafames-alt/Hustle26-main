package morfeov.coliseum.ui;

import java.nio.file.Path;
import java.util.List;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.movement.NoPush;
import morfeov.coliseum.module.impl.movement.NoWeb;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class WebPush {
    @TempDir Path directory;

    @Test void donorModesAndAllPushTogglesSurviveProfiles() throws Exception {
        var registry = new ModuleRegistry();
        var web = registry.register(new NoWeb()); var push = registry.register(new NoPush());
        assertEquals(List.of("ReallyWorld", "Break", "Custom"), web.mode.modes());
        assertFalse(web.speed.visible()); assertFalse(web.verticalSpeed.visible());
        web.mode.set("Custom"); web.speed.set(.63); web.verticalSpeed.set(1.4);
        push.players.set(false); push.blocks.set(true); push.water.set(true); push.fishingRod.set(true);
        Path profile = directory.resolve("web-push.json"); ClientConfig.saveProfile(profile, registry);
        web.mode.set("Break"); web.speed.set(.1); web.verticalSpeed.set(.1);
        push.players.set(true); push.blocks.set(false); push.water.set(false); push.fishingRod.set(false);
        assertTrue(new ClientConfig(profile, registry).load());
        assertEquals("Custom", web.mode.get()); assertEquals(.63, web.speed.get()); assertEquals(1.4, web.verticalSpeed.get());
        assertTrue(web.speed.visible()); assertTrue(web.verticalSpeed.visible());
        assertFalse(push.players.enabled()); assertTrue(push.blocks.enabled());
        assertTrue(push.water.enabled()); assertTrue(push.fishingRod.enabled());
    }

    @Test void pushFiltersAreIndependentAndNeverApplyWhileDisabled() {
        var push = new NoPush();
        assertFalse(NoPush.blocksEntityPush()); assertFalse(NoPush.blocksFluidPush());
        push.setEnabled(true);
        assertTrue(NoPush.blocksEntityPush()); assertFalse(NoPush.blocksSuffocationPush());
        assertFalse(NoPush.blocksFluidPush()); assertFalse(NoPush.blocksFishingPull());
        push.water.set(true); assertTrue(NoPush.blocksFluidPush()); assertFalse(NoPush.blocksFishingPull());
        push.players.set(false); assertFalse(NoPush.blocksEntityPush());
        push.blocks.set(true); push.fishingRod.set(true); assertTrue(NoPush.blocksSuffocationPush()); assertTrue(NoPush.blocksFishingPull());
        push.setEnabled(false);
        assertFalse(NoPush.blocksEntityPush()); assertFalse(NoPush.blocksSuffocationPush());
        assertFalse(NoPush.blocksFluidPush()); assertFalse(NoPush.blocksFishingPull());
    }

    @Test void webMovementKeepsDirectionalSpeedForEveryDiagonalAndYaw() {
        for (float yaw : new float[]{0, 45, 90, 180, -180, 720}) {
            for (int forward = -1; forward <= 1; forward++) for (int strafe = -1; strafe <= 1; strafe++) {
                if (forward == 0 && strafe == 0) continue;
                Vec3 velocity = NoWeb.webVelocity(Vec3.ZERO, yaw, forward, strafe, false, false, .63, 1.4);
                assertEquals(.63, velocity.horizontalDistance(), 1e-12); assertEquals(0, velocity.y);
            }
        }
        Vec3 forward = NoWeb.webVelocity(Vec3.ZERO, 0, 1, 0, false, false, .21, .9);
        assertEquals(0, forward.x, 1e-12); assertEquals(.21, forward.z, 1e-12);
        Vec3 left = NoWeb.webVelocity(Vec3.ZERO, 0, 0, 1, false, false, .21, .9);
        assertEquals(.21, left.x, 1e-12); assertEquals(0, left.z, 1e-12);
        Vec3 backwards = NoWeb.webVelocity(Vec3.ZERO, 0, -1, 0, false, false, .21, .9);
        assertEquals(-.21, backwards.z, 1e-12);
    }

    @Test void webVerticalControlsRetainSneakPriorityAndIdleHorizontalMotion() {
        Vec3 original = new Vec3(.1, -.8, .2);
        assertEquals(new Vec3(.1, 0, .2), NoWeb.webVelocity(original, 0, 0, 0, false, false, .21, .9));
        assertEquals(new Vec3(.1, .9, .2), NoWeb.webVelocity(original, 0, 0, 0, true, false, .21, .9));
        assertEquals(new Vec3(.1, -.9, .2), NoWeb.webVelocity(original, 0, 0, 0, true, true, .21, .9));
    }
}
