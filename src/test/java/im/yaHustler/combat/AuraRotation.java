package im.yaHustler.combat;

import java.util.List;
import java.util.Random;
import im.yaHustler.module.impl.combat.aura.Rotation;
import im.yaHustler.module.impl.combat.aura.LastAimAngles;
import im.yaHustler.module.impl.combat.aura.rotation.SpookyMotion;
import im.yaHustler.port.setting.SelectSetting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class AuraRotation {
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(AuraRotation.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsFoundCount() == 0 || summary.getTotalFailureCount() != 0) throw new AssertionError("Aura rotation port failed");
    }

    @Test void onlyRequestedRotationsRemainAndOldProfilesFallBackSafely() {
        assertEquals(List.of("ReallyWorld", "SpookyTime", "DataSet", "HvH"), Rotation.getModeNames());
        assertEquals(4, Rotation.values().length);
        assertEquals(Rotation.HvH, Rotation.fromString("hvh"));
        var setting = new SelectSetting("Режим наводки", "ReallyWorld", Rotation.getModeNames())
            .normalizeWith(value -> Rotation.fromString(value).getName());
        for (String removed : List.of("HolyWorld", "Плавная отводка", "Grim", "TriggerBot", "ShardAi", "SkyTime", "FT-New")) {
            setting.setSilently("SpookyTime");
            setting.setSilently(removed);
            assertEquals("ReallyWorld", setting.getValue());
            assertEquals(Rotation.ReallyWorld, Rotation.fromString(removed));
        }
        assertEquals(Rotation.SpookyTime, Rotation.fromString("Spooky_Time"));
        for (String name : List.of("DataSet", "Neuro", "NeuroTest")) {
            setting.setSilently(name);
            assertEquals("DataSet", setting.getValue());
            assertEquals(Rotation.DataSet, Rotation.fromString(name));
        }
    }

    @Test void lastAuraDirectionIsTransferredOnceWithoutLockingTheMouse() {
        var last = new LastAimAngles(); var player = new Object();
        last.remember(player, 179, 25);
        last.remember(player, 183.5f, -34.25f);
        assertEquals(new im.yaHustler.rotation.Rotation(183.5f, -34.25f), last.consume(player));
        assertNull(last.consume(player));
    }

    @Test void lastAuraDirectionCannotLeakAcrossRespawnsOrInvalidAngles() {
        var last = new LastAimAngles(); var oldPlayer = new Object(); var respawned = new Object();
        last.remember(oldPlayer, 30, 20);
        assertNull(last.consume(respawned));
        assertNull(last.consume(oldPlayer));
        last.remember(oldPlayer, 50, 200);
        assertEquals(90, last.consume(oldPlayer).pitch());
        last.remember(oldPlayer, Float.NaN, 0);
        assertNull(last.consume(oldPlayer));
        last.remember(oldPlayer, 45, 35); last.clear();
        assertNull(last.consume(oldPlayer));
    }

    @Test void spookyTurnsAcrossYawSeamByTheShortPath() {
        var motion = new SpookyMotion(new Random(1));
        float[] step = motion.step(1000, 179, 0, -179, 0, false, false, false, false, 0, .15f);
        assertTrue(step[0] > 0 && step[0] <= 2.1f);
        assertEquals(Math.round(step[0] / .15f), step[0] / .15f, .001f);
    }

    @Test void donorAttackPulseHoldsItsAimForTwoHundredMilliseconds() {
        var motion = new SpookyMotion(new Random(2));
        motion.step(1000, 0, 0, 60, 15, true, false, false, false, 0, .15f);
        assertTrue(motion.height() >= .26f && motion.height() <= .69f);
        assertTrue(Math.abs(motion.offsetX()) <= .12f && Math.abs(motion.offsetZ()) <= .12f);
        assertTrue(motion.step(1050, 0, 0, -60, -15, false, false, false, false, 0, .15f)[0] > 0);
        assertTrue(motion.step(1300, 0, 0, -60, -15, false, false, false, false, 0, .15f)[0] < 0);
    }

    @Test void prolongedRayContactSlowsTheDonorRotation() {
        var motion = new SpookyMotion(new Random(3));
        float[] step = null;
        for (int tick = 0; tick < 40; tick++) step = motion.step(1000 + tick * 50, 0, 0, 45, 20, false, false, true, false, 0, .15f);
        assertArrayEquals(new float[]{0, 0}, step, .001f);
    }

    @Test void movingTargetsAndMaceModeKeepAnglesFiniteAndPitchInBounds() {
        var motion = new SpookyMotion(new Random(4));
        var random = new Random(5);
        float yaw = 0, pitch = 89;
        for (int tick = 0; tick < 1000; tick++) {
            float[] step = motion.step(1000 + tick * 50L, yaw, pitch, random.nextFloat() * 720 - 360,
                random.nextFloat() * 180 - 90, tick % 10 == 0, tick % 3 == 0, false, tick % 7 == 0, tick % 6, .15f);
            assertTrue(Float.isFinite(step[0]) && Float.isFinite(step[1]));
            assertTrue(Math.abs(step[0]) <= 180.15f);
            yaw += step[0]; pitch += step[1];
            assertTrue(pitch >= -90 && pitch <= 90);
        }
    }

    @Test void resetClearsAttackOffsetsAndHeldTargetState() {
        var motion = new SpookyMotion(new Random(6));
        motion.step(1000, 0, 0, 60, 20, true, false, false, false, 0, .15f);
        motion.reset();
        assertEquals(0, motion.offsetX()); assertEquals(0, motion.offsetZ());
        assertTrue(motion.height() >= .26f && motion.height() <= .69f);
        assertTrue(motion.step(1050, 0, 0, -60, -20, false, false, false, false, 0, .15f)[0] < 0);
    }

    @Test void attackAimMovesSmoothlyIntoRequestedHeightRange() {
        var motion = new SpookyMotion(new Random(42));
        float initial = motion.height();
        motion.step(1000, 0, 0, 45, 10, true, false, false, false, 0, .15f);
        float first = motion.height();
        for (int tick = 1; tick < 100; tick++) {
            float before = motion.height();
            motion.step(1000 + tick * 50L, 0, 0, 45, 10, true, false, false, false, 0, .15f);
            assertTrue(Math.abs(motion.height() - before) <= .1f);
        }
        assertTrue(Math.abs(first - initial) <= .1f);
        assertTrue(motion.height() >= .37f && motion.height() <= .67f);
    }

    @Test void spookySprintResetFollowsAirRangeAndCooldownWindows() {
        assertTrue(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, .16, false, false, false, 0, .9f, 7));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, true, false, 2, 3, 0, false, false, false, 0, 1, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, true, 2, 3, 0, false, false, false, 0, 1, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 3, 3, 0, false, false, false, 0, 1, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, .17, false, false, false, 0, 1, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, 0, true, false, true, 0, 1, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, 0, false, false, false, 0, .89f, 10));
        assertFalse(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, 0, false, false, false, 0, 1, 6));
        assertTrue(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, 0, false, false, true, 0, 1, 0));
        assertTrue(im.yaHustler.module.impl.combat.aura.sprint.SpookySprintReset.shouldRelease(true, false, false, 2, 3, 0, false, false, false, 2, 0, 1));
    }

    @Test void spookyRotationOverridesAndHidesSprintChoice() {
        var aura = new im.yaHustler.module.impl.combat.aura.AttackAura();
        var aim = (SelectSetting) aura.settings().stream().filter(setting -> setting.name().equals("Режим наводки")).findFirst().orElseThrow();
        var sprint = aura.typeSprint;
        assertEquals(List.of("Packet", "Legit", "RW", "None"), sprint.modes());
        sprint.setSilently("Legit");
        assertTrue(sprint.visible());
        assertTrue(aura.isSprintTypeLegit());
        aim.setSilently("SpookyTime");
        assertFalse(sprint.visible());
        assertTrue(aura.isSprintTypeSpooky());
        assertFalse(aura.isSprintTypeLegit());
        assertFalse(aura.isSprintTypePacket());
        assertFalse(aura.isSprintTypeNone());
        aim.setSilently("ReallyWorld");
        assertTrue(sprint.visible());
        assertTrue(aura.isSprintTypeLegit());
    }
}
