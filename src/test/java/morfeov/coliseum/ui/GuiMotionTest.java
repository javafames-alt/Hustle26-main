package morfeov.coliseum.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GuiMotionTest {
    /** Use the normal JVM, matching the other Loom client tests in this project. */
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(GuiMotionTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 9 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("GUI motion verification failed");
    }

    @Test void firstFrameLoadingStallCannotSkipTheWindowReveal() {
        var motion = new GuiMotion();
        assertEquals(GuiMotion.initialFrame(), motion.frame(1_000_000_000L));
        var afterLoading = motion.frame(3_000_000_000L);
        assertTrue(afterLoading.opacity() > 0 && afterLoading.opacity() < .1f);
        assertTrue(afterLoading.scale() > GuiMotion.initialFrame().scale() && afterLoading.scale() < 1);
        assertTrue(afterLoading.offsetY() > 15);
        long now = 3_000_000_000L;
        for (int i = 0; i < 30; i++) motion.frame(now += 16_666_667L);
        assertEquals(new GuiMotion.Frame(1, 1, 0, false), motion.frame(now));
    }

    @Test void repeatedOpeningsEachStartWithTheWholeWindowHidden() {
        for (long now : new long[]{0, 2_000_000_000L, 9_000_000_000L}) {
            var motion = new GuiMotion();
            assertEquals(GuiMotion.initialFrame(), motion.frame(now));
            assertTrue(motion.frame(now + 20_000_000L).scale() < 1);
        }
    }

    @Test void closingBeforeTheFirstDrawDoesNotMixClockOrigins() {
        var motion = new GuiMotion();
        long now = 9_000_000_000L;
        motion.closeFrame(now);
        assertEquals(0, motion.frame(now).opacity());
        for (int i = 0; i < 8; i++) motion.frame(now += 40_000_000L);
        assertTrue(motion.frame(now).finished());
    }

    @Test void closingAfterALoadingStallReversesFromTheLastVisibleFrame() {
        var motion = new GuiMotion();
        motion.frame(0);
        var displayed = motion.frame(40_000_000L);
        motion.close(2_000_000_000L);
        assertEquals(displayed, motion.frame(2_000_000_000L));
        assertFalse(motion.frame(4_000_000_000L).finished());
        long now = 4_000_000_000L;
        for (int i = 0; i < 20; i++) motion.frame(now += 16_666_667L);
        assertTrue(motion.frame(now).finished());
    }
    @Test void openingSettlesWithoutOvershootAtDifferentFrameRates() {
        for (int fps : new int[]{30, 60, 144, 360}) {
            var motion = new GuiMotion();
            float previousOpacity = 0, previousScale = GuiMotion.initialFrame().scale();
            float previousOffset = GuiMotion.initialFrame().offsetY();
            for (long now = 0; now <= 500_000_000L; now += 1_000_000_000L / fps) {
                var frame = motion.frame(now);
                assertTrue(frame.opacity() >= previousOpacity && frame.opacity() <= 1);
                assertTrue(frame.scale() >= previousScale && frame.scale() <= 1);
                assertTrue(frame.offsetY() >= 0 && frame.offsetY() <= previousOffset);
                previousOpacity = frame.opacity(); previousScale = frame.scale();
                previousOffset = frame.offsetY();
            }
            var settled = motion.frame(500_000_000L);
            assertEquals(1, settled.scale()); assertEquals(1, settled.opacity());
            assertEquals(0, settled.offsetY());
        }
    }

    @Test void cinematicCurveStartsAtZeroAndNeverMovesOutsideItsEndpoints() {
        assertEquals(0, GuiMotion.cinematic(0));
        assertEquals(1, GuiMotion.cinematic(1));
        assertEquals(0, GuiMotion.cinematic(-1));
        assertEquals(1, GuiMotion.cinematic(2));
        float previous = 0;
        for (int i = 0; i <= 1000; i++) {
            float value = GuiMotion.cinematic(i / 1000f);
            assertTrue(value >= previous && value <= 1);
            previous = value;
        }
    }

    @Test void closingDuringOpeningPreservesTheDisplayedPose() {
        for (long now : new long[]{0, 40_000_000L, 150_000_000L, 500_000_000L}) {
            var motion = new GuiMotion(); motion.sample(0);
            var displayed = motion.sample(now);
            motion.close(now);
            assertEquals(displayed, motion.sample(now));
            var closed = motion.sample(now + 300_000_000L);
            assertTrue(closed.finished()); assertEquals(0, closed.opacity());
        }
    }

    @Test void rapidNavigationKeepsTheOutgoingPoseAndDoesNotPostponeTheSwap() {
        var motion = new GuiPageMotion();
        motion.start(0, 1);
        var displayed = motion.sample(80_000_000L);
        motion.leave(80_000_000L, -1);
        assertEquals(displayed, motion.sample(80_000_000L));
        var outgoing = motion.sample(120_000_000L);
        motion.leave(120_000_000L, 1);
        assertEquals(outgoing, motion.sample(120_000_000L));
        assertFalse(motion.readyToEnter(179_000_000L));
        assertTrue(motion.readyToEnter(180_000_000L));
        assertEquals(0, motion.sample(180_000_000L).opacity());
        motion.start(180_000_000L, 1);
        assertFalse(motion.readyToEnter(180_000_000L));
        assertEquals(0, motion.sample(180_000_000L).opacity());
        assertEquals(new GuiPageMotion.Frame(1, 0, 0), motion.sample(500_000_000L));
    }

    @Test void animatedWindowAndPageKeepPointerCoordinatesAligned() {
        var window = new GuiMotion(); window.sample(0);
        var page = new GuiPageMotion(); page.start(0, -1);
        for (long now : new long[]{0, 50_000_000L, 160_000_000L, 500_000_000L}) {
            var frame = window.sample(now);
            var transform = GuiTransform.centered(.82f, 960, 540, 496, 324)
                .animated(frame.scale(), frame.offsetY(), 480, 270);
            var content = page.sample(now);
            assertEquals(380, content.localX(transform.uiX(transform.screenX(380 + content.x()))), .0002);
            assertEquals(150, content.localY(transform.uiY(transform.screenY(150 + content.y()))), .0002);
        }
    }
}
