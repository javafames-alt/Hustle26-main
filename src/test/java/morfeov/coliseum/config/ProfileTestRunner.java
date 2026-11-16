package morfeov.coliseum.config;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Runs persistence tests with the complete Loom client classpath in a normal JVM. */
public final class ProfileTestRunner {
    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(ClientConfigProfileTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.GuiTransformTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.Interface.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.ColiseumFeaturesTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.NewModulesTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.CosmeticsInteractionTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.NoSlowCheck.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.WebPush.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.ElytraBoosterCheck.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.GrimGlideCheck.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.TextOverflowTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.ui.CameraMotionHistoryTest.class),
                DiscoverySelectors.selectClass(morfeov.coliseum.hud.LyricsLayoutTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 74 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Profile persistence or interface port tests failed");
    }
}
