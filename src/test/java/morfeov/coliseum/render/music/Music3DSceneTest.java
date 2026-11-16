package morfeov.coliseum.render.music;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.hud.Lyrics;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.render.Music3D;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public final class Music3DSceneTest {
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(Music3DSceneTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 11 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Music3D verification failed");
    }

    private final Music3DScene scene = new Music3DScene();
    private final AtomicInteger placements = new AtomicInteger();

    private void update(String track, long position, boolean playing, long start, String text, long now) {
        var cue = text == null ? null : new Lyrics.Cue(new Lyrics.Line(start, text), start + 4000,
                Math.clamp((position - start) / 4000f, 0, 1));
        scene.update(track, position, playing, cue, now * 1_000_000, 1.6,
                () -> new Music3DScene.Placement(new Vec3(placements.incrementAndGet(), 2, -4), new Quaternionf(), 4.5));
    }

    @Test void lookingElsewhereDoesNotMoveOrRespawnTheCurrentLine() {
        update("a", 0, true, 0, "Первая строка 🎵", 0);
        var original = scene.captions().getFirst();
        update("a", 500, true, 0, "Первая строка 🎵", 500);
        assertSame(original, scene.captions().getFirst());
        assertEquals(new Vec3(1, 2, -4), original.placement().position());
        assertEquals(1, placements.get());
        assertEquals(.125f, original.progress());
    }

    @Test void repeatedChorusAtANewTimestampCreatesANewWorldAnchor() {
        update("a", 0, true, 0, "Припев", 0);
        update("a", 1000, true, 1000, "Припев", 1000);
        assertEquals(2, scene.captions().size());
        assertTrue(scene.captions().getFirst().retiring());
        assertNotEquals(scene.captions().getFirst().placement().position(), scene.captions().getLast().placement().position());
    }

    @Test void pauseFreezesAnimationAndHighlightAndResumeKeepsTheAnchor() {
        update("a", 0, true, 0, "Строка", 0);
        update("a", 500, true, 0, "Строка", 500);
        var original = scene.captions().getFirst();
        update("a", 500, false, 0, "Строка", 600);
        double frozen = scene.clock();
        update("a", 500, false, 0, "Строка", 10600);
        assertEquals(frozen, scene.clock());
        assertEquals(.125f, original.progress());
        update("a", 500, true, 0, "Строка", 10700);
        update("a", 600, true, 0, "Строка", 10800);
        assertSame(original, scene.captions().getFirst());
        assertEquals(frozen + .1, scene.clock(), .00001);
    }

    @Test void pausedTrackDoesNotSpawnRepeatedCaptions() {
        update("a", 500, false, 0, "Строка", 0);
        update("a", 500, false, 0, "Строка", 1000);
        assertTrue(scene.captions().isEmpty());
        update("a", 500, true, 0, "Строка", 1100);
        assertEquals(1, scene.captions().size());
    }

    @Test void seekingForwardOrBackwardClearsOldLinesImmediately() {
        update("a", 0, true, 0, "Первая", 0);
        update("a", 1000, true, 1000, "Вторая", 1000);
        update("a", 9000, true, 8000, "После перемотки", 1100);
        assertEquals(1, scene.captions().size());
        assertEquals("После перемотки", scene.captions().getFirst().line().text());
        update("a", 500, true, 0, "Первая", 1200);
        assertEquals(1, scene.captions().size());
        assertEquals("Первая", scene.captions().getFirst().line().text());
    }

    @Test void switchingTracksWithIdenticalLyricsDoesNotReuseTheOldCaption() {
        update("a", 0, true, 0, "Одинаково", 0);
        var original = scene.captions().getFirst();
        update("b", 100, true, 0, "Одинаково", 100);
        assertEquals(1, scene.captions().size());
        assertNotSame(original, scene.captions().getFirst());
        assertEquals(0, scene.clock());
    }

    @Test void instrumentalGapRetiresAndThenRemovesThePreviousLine() {
        update("a", 0, true, 0, "Строка", 0);
        update("a", 1000, true, 0, null, 1000);
        assertTrue(scene.captions().getFirst().retiring());
        update("a", 2700, true, 0, null, 2700);
        assertTrue(scene.captions().isEmpty());
    }

    @Test void fastLyricsHaveABoundedNumberOfCaptions() {
        for (int i = 0; i < 30; i++) update("a", i * 50, true, i * 50, "Строка " + i, i * 50);
        assertEquals(4, scene.captions().size());
        assertEquals("Строка 29", scene.captions().getLast().line().text());
    }

    @Test void zeroTrailRemovesThePreviousLineOnTheSameFrame() {
        update("a", 0, true, 0, "Первая", 0);
        scene.update("a", 1000, true, new Lyrics.Cue(new Lyrics.Line(1000, "Вторая"), 4000, 0),
                1_000_000_000, 0, () -> new Music3DScene.Placement(Vec3.ZERO, new Quaternionf(), 4));
        assertEquals(1, scene.captions().size());
        assertEquals("Вторая", scene.captions().getFirst().line().text());
    }

    @Test void resetClearsWorldAnchorsAndAnimationTime() {
        update("a", 0, true, 0, "Строка", 0);
        update("a", 1000, true, 0, "Строка", 1000);
        scene.clear();
        assertTrue(scene.captions().isEmpty());
        assertEquals(0, scene.clock());
        update("a", 1100, true, 0, "Строка", 1100);
        assertEquals(2, placements.get());
    }

    @Test void moduleAndSettingsSurviveSavingAndLoadingAProfile(@TempDir Path directory) throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        Music3D module = registry.register(new Music3D());
        assertFalse(module.enabled());
        module.setEnabled(true);
        var distance = (NumberSetting)module.settings().getFirst();
        distance.setSilently(8.0);
        Path profile = directory.resolve("music3d.json");
        ClientConfig.saveProfile(profile, registry);
        module.setEnabled(false);
        distance.setSilently(distance.defaultValue());
        assertTrue(new ClientConfig(profile, registry).load());
        assertTrue(module.enabled());
        assertEquals(8.0, module.settings().getFirst().get());
    }
}
