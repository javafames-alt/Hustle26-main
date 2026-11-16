package im.yaHustler.config;

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
                DiscoverySelectors.selectClass(im.yaHustler.ui.GuiTransformTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.Interface.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.HustleFeaturesTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.NewModulesTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.CosmeticsInteractionTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.NoSlowCheck.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.WebPush.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.ElytraBoosterCheck.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.GrimGlideCheck.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.TextOverflowTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.ui.CameraMotionHistoryTest.class),
                DiscoverySelectors.selectClass(im.yaHustler.hud.LyricsLayoutTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 74 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Profile persistence or interface port tests failed");
    }
}
