package morfeov.coliseum.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EventsFeedTest {
    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(EventsFeedTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 8 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("Events feed verification failed");
    }

    @Test void minesKeepCurrentAndNextRarityAndServerName() {
        var entries = EventsFeed.parse(EventsFeed.Source.FUN_TIME_MINES, """
            {"servers":{"anarchy311":[{"server-ru-name":"Анархия-311","mine-name":"mine-1",
            "mine-rarity":"legendary","next-mine-rarity":"mythical","reset-seconds-left":23}]}}
            """, 10_000);
        assertEquals(1, entries.size());
        var mine = entries.getFirst();
        assertEquals("Анархия-311", mine.server());
        assertEquals("Легендарная", mine.rarity());
        assertEquals("Следующая: Мифическая", mine.detail());
        assertEquals(20, mine.remaining(13_000));
        assertEquals(0, mine.remaining(40_000));
    }

    @Test void eventsPreserveAllLootTiersAndUnknownRarity() {
        var entries = EventsFeed.parse(EventsFeed.Source.FUN_TIME_EVENTS, """
            {"response":[{"server":"anarchy107","events":[
            {"id":"airdrop","phase":"LOOTING","loot":"Элитный","time-seconds-left":0},
            {"id":"airdrop","loot":"Богатый"},
            {"id":"airdrop","loot":"Солидный"},
            {"id":"deathchest","loot":"Легендарный"},
            {"id":"deathchest","loot":"Проклятые сокровища"},
            {"id":"myst_beacon","loot":"null"}]}]}
            """, 0);
        assertEquals(6, entries.size());
        assertEquals(java.util.Set.of("Элитная", "Богатая", "Солидная", "Легендарная", "Проклятые сокровища", "Неизвестна"),
            entries.stream().map(EventsFeed.Entry::rarity).collect(java.util.stream.Collectors.toSet()));
        assertTrue(entries.stream().noneMatch(e -> e.rarity().equals("Обычная")));
    }

    @Test void keepsScheduledSystemEventsButOmitsFinishedOnes() {
        var entries = EventsFeed.parse(EventsFeed.Source.FUN_TIME_EVENTS, """
            {"response":[{"server":"anarchy225","events":[
            {"event-type":"system","time-seconds-left":1524,"location-announced":false},
            {"id":"airdrop","phase":"CLOSED"},
            {"id":"altarundead","phase":"STOPPING"}]}]}
            """, 0);
        assertEquals(1, entries.size());
        assertEquals("Следующее событие", entries.getFirst().name());
        assertEquals(1524, entries.getFirst().seconds());
        assertEquals("", entries.getFirst().coords());
    }

    @Test void copiesOnlyAnnouncedCoordinates() {
        var entries = EventsFeed.parse(EventsFeed.Source.FUN_TIME_EVENTS, """
            {"response":[{"server":"anarchy107","events":[
            {"id":"airdrop","location-announced":true,"location-event":{"x":1192,"y":126,"z":-843}},
            {"id":"airdrop","location-announced":false,"location-event":{"x":1,"y":2,"z":3}}]}]}
            """, 0);
        assertEquals("1192 126 -843", entries.getFirst().coords());
        assertEquals("", entries.getLast().coords());
    }

    @Test void emptyFeedsAreSuccessfulEmptyResults() {
        assertTrue(EventsFeed.parse(EventsFeed.Source.HOLY_WORLD, "{}", 0).isEmpty());
        assertTrue(EventsFeed.parse(EventsFeed.Source.HOLY_WORLD, "[]", 0).isEmpty());
        assertTrue(EventsFeed.parse(EventsFeed.Source.FUN_TIME_EVENTS, "{\"response\":[]}", 0).isEmpty());
        assertTrue(EventsFeed.parse(EventsFeed.Source.FUN_TIME_MINES, "{\"servers\":{}}", 0).isEmpty());
    }

    @Test void groupedHolyWorldEventsKeepServerRarityAndCoordinates() {
        var entries = EventsFeed.parse(EventsFeed.Source.HOLY_WORLD, """
            {"LITE_ANARCHY_40":[{"name":"Аирдроп","rarity":"§6Легендарный",
            "secondsLeft":120,"location":{"x":10,"y":64,"z":-20}}]}
            """, 1000);
        assertEquals("LITE_ANARCHY_40", entries.getFirst().server());
        assertEquals("Легендарная", entries.getFirst().rarity());
        assertEquals("10 64 -20", entries.getFirst().coords());
        assertEquals(118, entries.getFirst().remaining(3000));
    }

    @Test void missingTimesAndRaritiesAreNotInvented() {
        var entry = EventsFeed.parse(EventsFeed.Source.HOLY_WORLD,
            "{\"LITE_ANARCHY_1\":[{\"name\":\"Событие\"}]}", 0).getFirst();
        assertEquals(-1, entry.remaining(100_000));
        assertEquals("Неизвестна", entry.rarity());
        assertEquals("Новая редкость", EventsFeed.rarity("Новая редкость"));
    }

    @Test void malformedFunTimeFeedsDoNotMasqueradeAsEmptyLists() {
        assertThrows(IllegalArgumentException.class, () -> EventsFeed.parse(EventsFeed.Source.FUN_TIME_EVENTS, "{}", 0));
        assertThrows(IllegalArgumentException.class, () -> EventsFeed.parse(EventsFeed.Source.FUN_TIME_MINES, "{}", 0));
        assertThrows(RuntimeException.class, () -> EventsFeed.parse(EventsFeed.Source.HOLY_WORLD, "<html>Error</html>", 0));
    }
}
