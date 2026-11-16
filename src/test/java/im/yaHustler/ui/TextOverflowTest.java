package im.yaHustler.ui;

import im.yaHustler.font.FigmaFont;
import im.yaHustler.hud.TextMarquee;
import im.yaHustler.setting.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TextOverflowTest {
    @Test void settingsKeepFullHelpAndPlaceItBelowBothTheLabelAndControl() {
        var setting = new ModeSetting("Move Correction", "Crosshair", "Crosshair");
        var row = SettingRowLayout.measure(setting, 156);
        assertEquals("Как учитывать движение при поворотах", String.join(" ", row.help()));
        for (String line : row.help()) assertTrue(FigmaFont.width(line, 6.6f/11) <= 156);
        assertTrue(row.helpY() >= row.controlY() + row.controlHeight());
        assertTrue(row.height() > row.helpY() + (row.help().size()-1) * SettingRowLayout.HELP_STEP);
        var narrow = SettingRowLayout.measure(setting, 110);
        assertTrue(narrow.labels().size() > 1);
        assertTrue(narrow.height() > row.height());
    }
    @Test void longValuesWrapAndSlidersStayBelowAllDescriptionLines() {
        var mode = new ModeSetting("Режим", "Очень длинное название режима", "Очень длинное название режима");
        var row = SettingRowLayout.measure(mode, 156);
        assertEquals(mode.get(), String.join(" ", row.values()));
        assertTrue(row.values().size() > 1);
        assertTrue(row.controlHeight() > 13);
        var number = SettingRowLayout.measure(new NumberSetting("Extra Range", .5, 0, 2, .1), 110);
        assertTrue(number.sliderY() > number.helpY() + (number.help().size()-1)*SettingRowLayout.HELP_STEP + 5);
        assertTrue(number.height() >= number.sliderY() + 9);
    }
    @Test void wrappingPreservesUnicodeAndBreaksLongUnspacedWords() {
        var rows = TextWrap.lines("АБ😀ВГД", 2, s -> s.codePointCount(0, s.length()));
        assertEquals("АБ😀ВГД", String.join("", rows));
        for (String line : rows) assertTrue(line.codePointCount(0, line.length()) <= 2);
        assertEquals(java.util.List.of("один", "два"), TextWrap.lines("один\nдва", 100, String::length));
    }
    @Test void marqueeShowsTheWholeTitlePausesAtBothEndsAndResetsForTheNextSong() {
        var scroll = new TextMarquee();
        long start = 1_000_000_000L;
        assertEquals(0, scroll.offset("Track", 90, start));
        assertEquals(0, scroll.offset("Track", 90, start + 1_000_000_000L));
        float middle = scroll.offset("Track", 90, start + 3_800_000_000L);
        assertEquals(45, middle, .01);
        assertEquals(90, scroll.offset("Track", 90, start + 6_600_000_000L), .01);
        assertTrue(scroll.offset("Track", 90, start + 9_000_000_000L) < 90);
        assertEquals(0, scroll.offset("Next", 90, start + 10_000_000_000L));
        assertEquals(0, scroll.offset("Short", -10, start + 11_000_000_000L));
    }
}
