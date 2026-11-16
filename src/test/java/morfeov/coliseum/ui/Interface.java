package morfeov.coliseum.ui;

import morfeov.coliseum.font.AtlasFont;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Interface {
    @Test void relatedFlagsShareAMultiSelectWithoutLosingOriginalSettings(){
        var players=new morfeov.coliseum.setting.BooleanSetting("Цели: игроки",true);
        var mobs=new morfeov.coliseum.setting.BooleanSetting("Цели: мобы",false);
        var unrelated=new morfeov.coliseum.setting.BooleanSetting("Проверка",true);
        int[] saved={0};mobs.onChanged(()->saved[0]++);
        var combined=BooleanGroup.combine(java.util.List.of(players,unrelated,mobs));
        assertEquals(2,combined.size());assertSame(unrelated,combined.get(1));
        var group=(BooleanGroup)combined.getFirst();assertEquals("Цели",group.name());
        assertEquals("Выбрано 1/2",group.get());assertTrue(group.is("игроки"));
        group.set("мобы");assertTrue(players.enabled());assertTrue(mobs.enabled());assertEquals(1,saved[0]);
        assertEquals("Выбрано 2/2",group.get());group.set("игроки");assertFalse(players.enabled());assertTrue(mobs.enabled());
        mobs.set(false);group.refresh();assertEquals("Выбрано 0/2",group.get());
    }
    @Test void hudAndGuiTransparencyAndShadowControlsPersistIndependently() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var registry = new morfeov.coliseum.module.ModuleRegistry();
        var hud = registry.register(new morfeov.coliseum.module.impl.render.Hud());
        var gui = registry.register(new morfeov.coliseum.module.impl.render.ClickGui());
        hud.opacity.set(0.0); hud.shadows.set(true); hud.shadowStrength.set(38.0); hud.shadowSize.set(9.5);
        gui.opacity.set(.23); gui.shadow.set(false); gui.shadowStrength.set(81.0); gui.shadowSize.set(12.0);
        var folder = java.nio.file.Files.createTempDirectory("hud-style-");
        var profile = folder.resolve("settings.json");
        try {
            morfeov.coliseum.config.ClientConfig.saveProfile(profile, registry);
            hud.opacity.set(100.0); hud.shadows.set(false); hud.shadowStrength.set(0.0); hud.shadowSize.set(2.0);
            gui.opacity.set(1.0); gui.shadow.set(true); gui.shadowStrength.set(0.0); gui.shadowSize.set(2.0);
            assertTrue(new morfeov.coliseum.config.ClientConfig(profile, registry).load());
            assertEquals(0, hud.opacity.get()); assertTrue(hud.shadows.enabled());
            assertEquals(38, hud.shadowStrength.get()); assertEquals(9.5, hud.shadowSize.get());
            assertEquals(.23, gui.opacity.get()); assertFalse(gui.shadow.enabled());
            assertEquals(81, gui.shadowStrength.get()); assertEquals(12, gui.shadowSize.get());
        } finally { java.nio.file.Files.deleteIfExists(profile); java.nio.file.Files.deleteIfExists(folder); }
    }
    @Test void coordinateMetricsKeepRightPaddingWithLongAndNegativeValues() {
        var labels = new AtlasFont("coliseum_bold", true);
        var values = new AtlasFont("coliseum_semi", true);
        for (String[] row : new String[][]{{"9 14 15", "0.0", "20.0"}, {"-29999999 -64 29999999", "1234.5", "—"}}) {
            float[] lw = new float[3];
            String[] names = {"XYZ", "BPS", "TPS"};
            for (int i = 0; i < 3; i++) lw[i] = labels.width(names[i]) * 5.76f / 11;
            float[] vw = {values.width(row[0]) * 6.6f / 11, values.width(row[1]) * 6.6f / 11, values.width(row[2]) * 6.6f / 11};
            var layout = morfeov.coliseum.hud.InlineHudLayout.measure(lw, vw, new boolean[]{false, true, true});
            assertEquals(8, layout.metrics()[0].label());
            for (int i = 0; i < 3; i++) {
                var metric = layout.metrics()[i];
                assertTrue(metric.value() >= metric.label() + lw[i] + 3.99f);
                assertTrue(metric.end() <= layout.width() - 7.99f);
                if (i > 0) assertTrue(metric.divider() >= layout.metrics()[i - 1].end() + 6.99f);
            }
            float lastRight = layout.metrics()[2].end();
            assertTrue(layout.width() - lastRight >= 8 && layout.width() - lastRight < 9);
        }
    }
    @Test void windowCanCloseDuringOpeningWithoutAJump() {
        var motion = new GuiMotion();
        assertEquals(0, motion.sample(0).opacity());
        var opening = motion.sample(100_000_000L);
        motion.close(100_000_000L);
        assertEquals(opening, motion.sample(100_000_000L));
        var closed = motion.sample(500_000_000L);
        assertTrue(closed.finished()); assertEquals(0, closed.opacity());
        var complete = new GuiMotion(); complete.sample(0);
        var settled = complete.sample(500_000_000L);
        assertEquals(1, settled.opacity()); assertEquals(1, settled.scale()); assertEquals(0, settled.offsetY());
    }
    @Test void switchingPagesPreservesInputCoordinatesAndSettlesAfterRapidChanges() {
        var motion = new GuiPageMotion();
        motion.start(0, 1);
        for (long now : new long[]{0, 30_000_000L, 120_000_000L, 300_000_000L}) {
            var frame = motion.sample(now);
            assertEquals(160, frame.localX(160 + frame.x()), .0001);
            assertEquals(90, frame.localY(90 + frame.y()), .0001);
            assertTrue(frame.opacity() >= 0 && frame.opacity() <= 1);
        }
        motion.start(120_000_000L, -1);
        assertTrue(motion.sample(120_000_000L).x() < 0);
        var settled = motion.sample(500_000_000L);
        assertEquals(1, settled.opacity()); assertEquals(0, settled.x()); assertEquals(0, settled.y());
    }
    @Test void nestedPageFadeDoesNotLeakIntoTheHud() {
        int color = 0xc0abcdef;
        float previous = GuiOpacity.push(.5f);
        try {
            assertEquals(0x60abcdef, GuiOpacity.color(color));
            float parent = GuiOpacity.push(.5f);
            try { assertEquals(0x30abcdef, GuiOpacity.color(color)); }
            finally { GuiOpacity.restore(parent); }
            assertEquals(0x60abcdef, GuiOpacity.color(color));
        } finally { GuiOpacity.restore(previous); }
        assertEquals(color, GuiOpacity.color(color));
    }
    @Test void draggingLeansWithMovementAndReturnsAfterRelease() {
        var right = new morfeov.coliseum.hud.HudDragMotion();
        var left = new morfeov.coliseum.hud.HudDragMotion();
        float r = 0, l = 0;
        for (int i = 0; i < 30; i++) {
            right.move(8, 0, 1d / 60); left.move(-8, 0, 1d / 60);
            r = right.step(1d / 60, true, true); l = left.step(1d / 60, true, true);
        }
        assertTrue(r > .04f && r <= Math.toRadians(8));
        assertEquals(-r, l, .0001);
        for (int i = 0; i < 180; i++) r = right.step(1d / 60, false, true);
        assertEquals(0, r, .00001);
        assertEquals(0, left.step(1d / 60, true, false));
    }
    @Test void dragResponseDoesNotDependOnMousePollingRate() {
        var normal = new morfeov.coliseum.hud.HudDragMotion();
        var fast = new morfeov.coliseum.hud.HudDragMotion();
        float a = 0, b = 0;
        for (int i = 0; i < 60; i++) { normal.move(4, 0, 1d / 60); a = normal.step(1d / 60, true, true); }
        for (int i = 0; i < 240; i++) { fast.move(1, 0, 1d / 240); b = fast.step(1d / 240, true, true); }
        assertEquals(a, b, .0001);
        var highPolling = new morfeov.coliseum.hud.HudDragMotion();
        for (int frame = 0; frame < 60; frame++) {
            // 1,200 Hz mouse with integer GUI coordinates, rendering at 60 FPS.
            for (int event = 0; event < 20; event++) highPolling.move(event % 5 == 0 ? 1 : 0, 0, 1d / 1200);
            b = highPolling.step(1d / 60, true, true);
        }
        assertEquals(a, b, .0001);
    }
    @Test void rotatingAndScalingHudKeepsBlurAtTheSameCentre() {
        var pose = new org.joml.Matrix3x2f().translate(300, 100).rotate((float)Math.PI / 2).scale(2);
        var surface = BackdropStyle.transformedSurface(10, 20, 100, 20, 4, pose);
        assertArrayEquals(new float[]{140, 200, 200, 40, 8, (float)Math.PI / 2}, surface, .0001f);
        var packed = BackdropStyle.pack(960, 540, new float[][]{surface, {10, 10, 20, 20}}, 5, 0xffffffff, 0);
        assertEquals(surface[5], packed[8 + BackdropStyle.LIMIT * 5]);
        assertEquals(0, packed[9 + BackdropStyle.LIMIT * 5]);
        // This point is inside the rotated long edge, outside the unrotated rectangle.
        double dx = 0, dy = 90, c = Math.cos(surface[5]), s = Math.sin(surface[5]);
        assertTrue(Math.abs(c * dx + s * dy) < surface[2] / 2);
        assertTrue(Math.abs(-s * dx + c * dy) < surface[3] / 2);
        assertTrue(dy > surface[3] / 2);
    }
    @Test void expandingLeftCardDoesNotMoveRightColumn() {
        var closed = ModuleGridLayout.pack(new int[]{20, 20, 20, 20, 20}, 3);
        var expanded = ModuleGridLayout.pack(new int[]{188, 20, 20, 20, 20}, 3);
        assertEquals(closed[1], expanded[1]);
        assertEquals(closed[3], expanded[3]);
        assertEquals(168, expanded[2].top() - closed[2].top());
        assertEquals(234, ModuleGridLayout.contentHeight(expanded));
        for (int i = 2; i < expanded.length; i++) assertTrue(expanded[i].top() >= expanded[i-2].top() + expanded[i-2].height() + 3);
        assertEquals(0, ModuleGridLayout.contentHeight(ModuleGridLayout.pack(new int[0], 3)));
    }
    @Test void menuAndHudMasksKeepIndependentRadiiAndStd140Alignment() throws Exception {
        var data = BackdropStyle.pack(960, 540, new float[][]{{10, 20, 146, 34, 7}, {200, 100, 442, 312}}, 14, 0xffa855f7, .12f);
        assertEquals(BackdropStyle.BYTES, data.length * Float.BYTES);
        assertArrayEquals(new float[]{960, 540, 14, 2}, java.util.Arrays.copyOf(data, 4));
        assertArrayEquals(new float[]{10, 20, 146, 34, 200, 100, 442, 312}, java.util.Arrays.copyOfRange(data, 8, 16));
        assertEquals(7, data[8 + BackdropStyle.LIMIT * 4]);
        assertEquals(14, data[9 + BackdropStyle.LIMIT * 4]);
        try (var stream = getClass().getResourceAsStream("/assets/coliseum/shaders/core/figma_backdrop.fsh")) {
            assertNotNull(stream);
            String shader = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(shader.contains("Rectangles[" + BackdropStyle.LIMIT + "]"));
            assertTrue(shader.contains("Radii[" + BackdropStyle.LIMIT / 4 + "]"));
        }
    }
    @Test void transferredInterFacesContainRussianAndVariableWidthMetrics() {
        for (String name : new String[]{"coliseum_regular", "coliseum_medium", "coliseum_semi", "coliseum_bold"}) {
            var font = new AtlasFont(name, true);
            for (int cp : "Настройки Размер Coliseum".codePoints().toArray()) assertTrue(font.hasGlyph(cp), name + ": " + cp);
            assertTrue(font.width("WWW") > font.width("iii") * 2);
            assertTrue(font.cap() > .4f && font.cap() < 1);
            if (!font.hasGlyph(0x00b7)) assertEquals(font.width("-"),font.width("·"),.001f);
            if (!font.hasGlyph(0x00ab)) assertEquals(font.width("\"Aura\""),font.width("«Aura»"),.001f);
            if (!font.hasGlyph(0x2014)) assertEquals(font.width("-"),font.width("—"),.001f);
            assertEquals(font.width(" "),font.width("\uD83E\uDD84"),.001f);
        }
    }
    @Test void originalIconsAndBrandAreAvailableToNativeRenderer() throws Exception {
        for (String path : new String[]{"gui/icons/combat", "gui/icons/movement", "gui/icons/visuals", "gui/icons/player", "gui/icons/misc", "gui/icons/config", "gui/icons/theme", "figma/icons/fps", "figma/icons/keybinds", "figma/icons/effects", "coliseum/coliseum_c_glass"}) {
            try (var stream = getClass().getResourceAsStream("/assets/coliseum/textures/" + path + ".png")) {
                assertNotNull(stream, path);
                assertNotNull(javax.imageio.ImageIO.read(stream), path);
            }
        }
    }
}
