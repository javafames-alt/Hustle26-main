package im.yaHustler.hud;

import java.util.List;
import java.util.LinkedHashSet;
import im.yaHustler.font.FigmaFont;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LyricsLayoutTest {
    @Test void playerClockHonorsShortSeeksPausesAndTrackEndWithoutDelay() {
        long now=10_000_000_000L;
        var playing=new MediaSession.Playback(5000,20000,true,now);
        assertEquals(5500,playing.positionAt(now+500_000_000L));
        var seekBackward=new MediaSession.Playback(4000,20000,true,now+500_000_000L);
        assertEquals(4100,seekBackward.positionAt(now+600_000_000L));
        var seekForward=new MediaSession.Playback(4800,20000,true,now+600_000_000L);
        assertEquals(4800,seekForward.positionAt(now+600_000_000L));
        var paused=new MediaSession.Playback(4800,20000,false,now);
        assertEquals(4800,paused.positionAt(now+5_000_000_000L));
        var ending=new MediaSession.Playback(19950,20000,true,now);
        assertEquals(20000,ending.positionAt(now+500_000_000L));
    }
    @Test void russianLyricsWrapIntoTwoRowsWithoutEllipsisOrLostWords() {
        String text="Длинная строка песни должна целиком помещаться и оставаться читаемой";
        var layout=LyricLayout.measure(text,160);
        assertEquals(2,layout.rows().size());
        assertEquals(text,String.join(" ",layout.rows().stream().map(LyricLayout.Row::text).toList()));
        assertTrue(layout.font()>=7.25f);
        for(var row:layout.rows()) assertTrue(FigmaFont.mediumWidth(row.text(),layout.font()/11)<=160.001);
    }
    @Test void unusuallyLongLyricsUseReadableTwoRowPagesAndKeepEveryWord() {
        var layout=LyricLayout.measure("Очень длинный текст песни который не помещается в обычные две строки ".repeat(8),160);
        assertTrue(layout.rows().size()>2);
        assertEquals(7.25f,layout.font());
        var seen=new LinkedHashSet<LyricLayout.Row>();
        for(int i=0;i<=1000;i++) {
            var page=layout.page(i/1000f);
            assertTrue(page.size()<=2);
            seen.addAll(page);
        }
        assertEquals(layout.rows(),List.copyOf(seen));
    }
    @Test void highlightingContinuesAtTheRightSourceOffsetAfterWrapping() {
        var layout=LyricLayout.measure("Первая половина строки и продолжение на следующей строке",160);
        var second=layout.rows().get(1);
        assertEquals("",second.sung(second.start()));
        assertEquals(second.text().substring(0,second.text().offsetByCodePoints(0,2)),second.sung(second.start()+2));
        assertEquals(second.text(),second.sung(layout.characters()));
        var unicode=LyricLayout.measure("Музыка 🎵🎶 продолжается",35);
        for(var row:unicode.rows()) {
            assertTrue(FigmaFont.mediumWidth(row.text(),unicode.font()/11)<=35.001);
            assertFalse(row.text().endsWith("\ud83c"));
        }
    }
    @Test void fractionalAndRepeatedLrcTimestampsKeepTheirRealTimes() {
        var lines=Lyrics.parseLrc("[00:01.5]Раз\n[00:02.05]Два\n[00:03.125][00:09.125]Припев");
        assertEquals(List.of(1500L,2050L,3125L,9125L),lines.stream().map(Lyrics.Line::startMs).toList());
        assertEquals("Припев",lines.getLast().text());
    }
    @Test void emptyTimedLinesClearLyricsDuringInstrumentalBreaks() {
        var lines=Lyrics.parseLrc("[00:01]Начало\n[00:03]\n[00:10]Продолжение");
        assertNull(Lyrics.cue(lines,999));
        assertEquals("Начало",Lyrics.cue(lines,1000).line().text());
        assertEquals(3000,Lyrics.cue(lines,2500).endMs());
        assertNull(Lyrics.cue(lines,3000));
        assertNull(Lyrics.cue(lines,9999));
        assertEquals("Продолжение",Lyrics.cue(lines,10000).line().text());
    }
    @Test void lastLyricAndLongSilentGapsExpireInsteadOfStickingForever() {
        var lines=Lyrics.parseLrc("[00:01]Коротко\n[00:20]Конец");
        assertEquals(5000,Lyrics.cue(lines,2000).endMs());
        assertNull(Lyrics.cue(lines,5000));
        assertNull(Lyrics.cue(lines,19000));
        assertNotNull(Lyrics.cue(lines,23999));
        assertNull(Lyrics.cue(lines,24000));
    }
    @Test void seekingSelectsTheNewCueAndResetsItsProgressImmediately() {
        var lines=Lyrics.parseLrc("[00:01]Первая\n[00:04]Вторая\n[00:07]Третья");
        assertEquals("Третья",Lyrics.cue(lines,7500).line().text());
        var rewound=Lyrics.cue(lines,1500);
        assertEquals("Первая",rewound.line().text());
        assertEquals(1/6f,rewound.progress(),.0001);
        assertNull(Lyrics.cue(lines,0));
    }
}
