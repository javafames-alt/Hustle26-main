package morfeov.coliseum.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BackdropRegionTest {
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(BackdropRegionTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 4 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Backdrop region verification failed");
    }

    @Test void fullyOffscreenTagsProduceEmptyRegions() {
        for (float[] rectangle : new float[][]{
            {100, -100, 60, 12}, {100, 600, 60, 12},
            {-100, 100, 60, 12}, {1000, 100, 60, 12}
        }) {
            for (int divisor : new int[]{1, 2, 4}) {
                var region = BackdropRegion.of(960, 540, new float[][]{rectangle}, 1920 / divisor, 1080 / divisor, 8);
                assertTrue(region.isEmpty(), region.toString());
            }
        }
    }

    @Test void tagsCrossingScreenEdgesRetainPositiveClippedBounds() {
        for (float[] rectangle : new float[][]{
            {100, -6, 60, 12}, {100, 534, 60, 12},
            {-30, 100, 60, 12}, {930, 100, 60, 12},
            {-2, -2, 4, 4}, {958, 538, 4, 4}
        }) {
            for (int divisor : new int[]{1, 2, 4}) {
                int width = 1920 / divisor, height = 1080 / divisor;
                var region = BackdropRegion.of(960, 540, new float[][]{rectangle}, width, height, 4);
                assertFalse(region.isEmpty(), region.toString());
                assertTrue(region.x() >= 0 && region.y() >= 0);
                assertTrue(region.x() + region.width() <= width);
                assertTrue(region.y() + region.height() <= height);
            }
        }
    }

    @Test void emptyBatchesAndZeroHeightScissorsAreEmpty() {
        assertTrue(BackdropRegion.of(960, 540, new float[0][], 1920, 1080, 20).isEmpty());
        assertTrue(new BackdropRegion(100, 0, 379, 0).isEmpty());
        assertTrue(new BackdropRegion(0, 100, 0, 379).isEmpty());
        assertFalse(new BackdropRegion(0, 0, 1, 1).isEmpty());
    }

    @Test void anOffscreenTagDoesNotHideAnOnscreenTagInTheSameBatch() {
        var region = BackdropRegion.of(960, 540,
            new float[][]{{100, -100, 60, 12}, {400, 200, 60, 12}}, 1920, 1080, 4);
        assertFalse(region.isEmpty());
        assertTrue(region.x() <= 800 && region.x() + region.width() >= 920);
        assertTrue(region.y() <= 400 && region.y() + region.height() >= 424);
    }
}
