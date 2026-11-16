package im.yaHustler.ui;

import java.nio.file.Path;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.module.impl.movement.GrimGlide;
import im.yaHustler.module.impl.movement.GrimGlideFlightState;
import im.yaHustler.module.impl.movement.GrimGlidePhysics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class GrimGlideCheck {
    @TempDir Path directory;

    @Test void bothDonorSettingsSurviveProfileReloadIncludingZeroReset() throws Exception {
        var registry = new ModuleRegistry();
        var glide = registry.register(new GrimGlide());
        assertEquals(1.5, glide.timerBoost.get());
        assertEquals(0.5, glide.timerReset.get());
        glide.timerBoost.set(4.7);
        glide.timerReset.set(0.0);
        glide.setEnabled(true);
        Path profile = directory.resolve("grim-glide.json");
        ClientConfig.saveProfile(profile, registry);
        glide.timerBoost.set(1.0);
        glide.timerReset.set(0.9);
        glide.setEnabled(false);
        assertTrue(new ClientConfig(profile, registry).load());
        assertTrue(glide.enabled());
        assertEquals(4.7, glide.timerBoost.get());
        assertEquals(0.0, glide.timerReset.get());
        glide.setEnabled(false);
        assertEquals(1.0f, GrimGlide.timerMultiplier());
    }

    @Test void alternatingTimerReleasesOnLandingAndResetRestartsBoostPhase() {
        var state = new GrimGlideFlightState();
        assertEquals(1.0f, state.timerMultiplier());
        state.beforeTick(true, 1.5f, 0.5f);
        assertEquals(1.5f, state.timerMultiplier());
        state.beforeTick(false, 1.5f, 0.5f);
        assertEquals(1.0f, state.timerMultiplier());
        state.beforeTick(true, 1.5f, 0.5f);
        assertEquals(0.5f, state.timerMultiplier());
        state.beforeTick(true, 2.0f, 0.0f);
        assertEquals(2.0f, state.timerMultiplier());
        state.beforeTick(true, 2.0f, 0.0f);
        assertEquals(0.0f, state.timerMultiplier());
        assertEquals(1000.0f, 50.0f / Math.max(GrimGlide.MIN_TIMER_MULTIPLIER, state.timerMultiplier()));
        state.reset();
        assertEquals(1.0f, state.timerMultiplier());
        state.beforeTick(true, 1.5f, 0.5f);
        assertEquals(1.5f, state.timerMultiplier());
    }

    @Test void correctionAllowsItsFirstMovementAndTwoRecoveryTicksThenResumesSpoofing() {
        var state = new GrimGlideFlightState();
        state.beforeTick(true, 1.5f, 0.5f);
        assertTrue(state.replaceMovementPacket(true));
        state.positionCorrected();
        assertTrue(state.recoveringFromCorrection());
        assertFalse(state.replaceMovementPacket(true));
        state.beforeTick(true, 1.5f, 0.5f);
        assertEquals(1.5f, state.timerMultiplier(), "A correction aligns the next timer phase to boost");
        assertTrue(state.recoveringFromCorrection());
        assertFalse(state.replaceMovementPacket(true));
        state.beforeTick(true, 1.5f, 0.5f);
        assertFalse(state.recoveringFromCorrection());
        assertTrue(state.replaceMovementPacket(true));
        assertFalse(state.replaceMovementPacket(false));
        state.positionCorrected();
        state.beforeTick(true, 1.5f, 0.5f);
        state.beforeTick(true, 1.5f, 0.5f);
        assertFalse(state.replaceMovementPacket(true), "The first movement is preserved even if no packets were sent during recovery");
        assertTrue(state.replaceMovementPacket(true));
        state.reset();
        assertFalse(state.recoveringFromCorrection());
        assertTrue(state.replaceMovementPacket(true));
    }

    @Test void stationaryLevelFlightHasTheDonorDescentAndCorrectionImpulse() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 normal = GrimGlidePhysics.velocity(Vec3.ZERO, forward, 0, 0, 0.08, false, false);
        assertEquals(0.0, normal.x, 1e-12);
        assertEquals(-0.00540000021457672, normal.y, 1e-12);
        assertEquals(0.0005400000214576721, normal.z, 1e-12);
        Vec3 recovery = GrimGlidePhysics.velocity(Vec3.ZERO, forward, 0, 0, 0.08, false, true);
        assertEquals(0.012359998986124995, recovery.y, 1e-12);
        assertEquals(0.09178200359344482, recovery.z, 1e-12);
    }

    @Test void slowFallingCapsGravityAndVerticalLookNeverDividesByZero() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 slow = GrimGlidePhysics.velocity(Vec3.ZERO, forward, 0, 0, 0.08, true, false);
        Vec3 capped = GrimGlidePhysics.velocity(Vec3.ZERO, forward, 0, 0, 0.01, false, false);
        assertEquals(capped, slow);
        Vec3 upward = new Vec3(0.3, 0.2, 0.4);
        assertEquals(GrimGlidePhysics.velocity(upward, forward, -30, 90, 0.08, false, true),
            GrimGlidePhysics.velocity(upward, forward, -30, 90, 0.08, true, true));
        for (int pitch = -90; pitch <= 90; pitch++) {
            Vec3 result = GrimGlidePhysics.velocity(upward, new Vec3(0, 1, 0), pitch, 125, 0.08, true, true);
            assertTrue(Double.isFinite(result.x) && Double.isFinite(result.y) && Double.isFinite(result.z));
        }
    }
}
