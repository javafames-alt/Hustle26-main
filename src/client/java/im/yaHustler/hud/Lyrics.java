package im.yaHustler.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {

    private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Set<String> APP_NOISE = Set.of(
            "google chrome", "chrome", "msedge", "edge", "opera", "opera gx",
            "firefox", "yandex", "yandex browser", "browser", "youtube", "вконтакте", "vk",
            "player", "hustle263 music", "spotify free", "vlc", "windows media player",
            "soundcloud"
    );

    public record Line(long startMs, String text) {
    }

    public record Cue(Line line, long endMs, float progress) {}

    public record TrackInfo(String artist, String title) {
    }

    private static volatile String loadedFor = "";
    private static volatile List<Line> lines = List.of();
    private static volatile long currentFetchId = 0L;
    private static boolean fetching;
    private static long retryAt;

    private Lyrics() {
    }

    public static synchronized void ensure(String artist, String title) {
        if (title == null || title.isBlank()) {
            ++currentFetchId;
            lines = List.of();
            loadedFor = "";
            return;
        }
        String key = (artist == null ? "" : artist.trim()) + " - " + title.trim();

        if (key.equals(loadedFor) && (fetching || !lines.isEmpty() || System.nanoTime() < retryAt)) {
            return;
        }

        // Immediately reset lines so the previous track's lyrics NEVER continue playing!
        lines = List.of();
        loadedFor = key;
        long fetchId = ++currentFetchId;
        fetching = true;

        Thread thread = new Thread(() -> {
            try {
                List<Line> fetched = fetch(artist, title);
                // Ensure user hasn't switched songs while this request was running
                synchronized (Lyrics.class) {
                    if (fetchId == currentFetchId && key.equals(loadedFor)) lines = fetched;
                }
            } catch (Exception error) {
                im.yaHustler.Hustle26.LOGGER.debug("Lyrics lookup failed for {}", key, error);
            } finally {
                synchronized (Lyrics.class) {
                    if (fetchId == currentFetchId) {
                        fetching = false;
                        retryAt = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
                    }
                }
            }
        }, "Hustle 26.3-Lyrics");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean has() {
        return !lines.isEmpty();
    }

    public static Line current(long positionMs) {
        Cue cue=cue(positionMs);
        return cue==null?null:cue.line();
    }

    public static float progress(long positionMs) {
        Cue cue=cue(positionMs);
        return cue==null?0:cue.progress();
    }

    public static Cue cue(long positionMs) { return cue(lines,positionMs); }

    static Cue cue(List<Line> snapshot,long positionMs) {
        int index=-1;
        for(int i=0;i<snapshot.size() && snapshot.get(i).startMs()<=positionMs;i++) index=i;
        if(index<0) return null;
        Line line=snapshot.get(index);
        if(line.text().isBlank()) return null;
        long display=Math.clamp(line.text().codePointCount(0,line.text().length())*100L,4000L,9000L);
        long end=line.startMs()+display;
        if(index+1<snapshot.size()) end=Math.min(end,snapshot.get(index+1).startMs());
        if(positionMs>=end) return null;
        return new Cue(line,end,Math.clamp((positionMs-line.startMs())/(float)Math.max(1,end-line.startMs()),0,1));
    }

    private static List<Line> fetch(String rawArtist, String rawTitle) {
        TrackInfo info = parseTrack(rawArtist, rawTitle);
        String artist = info.artist();
        String title = info.title();
        String firstArtist = getFirstArtist(artist);
        double targetDurSec = MediaSession.durationMs() / 1000.0;

        if (title.isBlank()) {
            return List.of();
        }

        // 1. Try LRCLIB exact get with duration matching
        if (!firstArtist.isBlank()) {
            List<Line> exactFirst = fetchLrcLibGet(firstArtist, title, targetDurSec);
            if (!exactFirst.isEmpty()) {
                return exactFirst;
            }
        }
        if (!artist.isBlank() && !artist.equalsIgnoreCase(firstArtist)) {
            List<Line> exactAll = fetchLrcLibGet(artist, title, targetDurSec);
            if (!exactAll.isEmpty()) {
                return exactAll;
            }
        }

        // 2. Build prioritized search queries
        List<String> queries = new ArrayList<>();

        // (A) Both words together: title + first artist (e.g. "DYING WORLD AQUAKEY", "Hennessy 3umph")
        if (!title.isBlank() && !firstArtist.isBlank()) {
            queries.add(title + " " + firstArtist);
        }

        // (B) First artist + title (e.g. "AQUAKEY DYING WORLD", "3umph Hennessy")
        if (!firstArtist.isBlank() && !title.isBlank()) {
            queries.add(firstArtist + " " + title);
        }

        // (C) Title + full artist (e.g. "DYING WORLD AQUAKEY, KUDOKUSHI")
        if (!title.isBlank() && !artist.isBlank() && !artist.equalsIgnoreCase(firstArtist)) {
            queries.add(title + " " + artist);
        }

        // (D) Full artist + title (e.g. "AQUAKEY, KUDOKUSHI DYING WORLD")
        if (!artist.isBlank() && !artist.equalsIgnoreCase(firstArtist) && !title.isBlank()) {
            queries.add(artist + " " + title);
        }

        // (E) Clean raw title with parentheses stripped (e.g. "AQUAKEY, KUDOKUSHI - DYING WORLD")
        String rawClean = stripParentheses(rawTitle);
        if (!rawClean.isBlank() && !queries.contains(rawClean)) {
            queries.add(rawClean);
        }

        // (F) Full raw title as-is ("иногда вводи полное название мб найдется")
        if (rawTitle != null && !rawTitle.isBlank() && !queries.contains(rawTitle.trim())) {
            queries.add(rawTitle.trim());
        }

        // (G) Clean title only (duration will differentiate)
        if (!title.isBlank() && !queries.contains(title)) {
            queries.add(title);
        }

        // Execute queries sequentially and pick the one with matching duration and lyrics
        for (String q : queries) {
            List<Line> res = fetchLrcLibSearch(q, firstArtist, artist, title, targetDurSec);
            if (!res.isEmpty()) {
                return res;
            }
        }

        return List.of();
    }

    private static List<Line> fetchLrcLibGet(String artist, String title, double targetDurSec) {
        try {
            String durParam = targetDurSec > 0 ? "&duration=" + Math.round(targetDurSec) : "";
            String url = "https://lrclib.net/api/get?artist_name=" + encode(artist) + "&track_name=" + encode(title) + durParam;
            String body = httpGet(url);
            if (body == null || body.isBlank()) {
                // If get with duration failed, try get without duration
                if (!durParam.isEmpty()) {
                    url = "https://lrclib.net/api/get?artist_name=" + encode(artist) + "&track_name=" + encode(title);
                    body = httpGet(url);
                }
            }
            if (body == null || body.isBlank()) {
                return List.of();
            }

            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                return List.of();
            }
            JsonObject obj = element.getAsJsonObject();
            double itemDur = getItemDuration(obj);

            // Check synced lyrics first
            String synced = getStringField(obj, "syncedLyrics");
            if (synced != null && !synced.isBlank()) {
                if (targetDurSec <= 0 || itemDur <= 0 || Math.abs(itemDur - targetDurSec) <= 6.0) {
                    return parseLrc(synced);
                }
            }

            // Fallback to plain lyrics if synced not available
            String plain = getStringField(obj, "plainLyrics");
            if (plain != null && !plain.isBlank()) {
                if (targetDurSec <= 0 || itemDur <= 0 || Math.abs(itemDur - targetDurSec) <= 6.0) {
                    return parsePlain(plain, itemDur);
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private static List<Line> fetchLrcLibSearch(String query, String firstArtist, String fullArtist, String expectedTitle, double targetDurSec) {
        try {
            String url = "https://lrclib.net/api/search?q=" + encode(query);
            String body = httpGet(url);
            if (body == null || body.isBlank()) {
                return List.of();
            }

            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonArray()) {
                return List.of();
            }
            JsonArray array = element.getAsJsonArray();

            JsonObject bestSyncedMatch = null;
            double bestSyncedDiff = 999.0;
            int bestSyncedScore = -100;

            JsonObject bestPlainMatch = null;
            double bestPlainDiff = 999.0;
            int bestPlainScore = -100;

            for (JsonElement item : array) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject obj = item.getAsJsonObject();

                String synced = getStringField(obj, "syncedLyrics");
                String plain = getStringField(obj, "plainLyrics");
                if ((synced == null || synced.isBlank()) && (plain == null || plain.isBlank())) {
                    continue;
                }

                String foundTitle = getStringField(obj, "trackName");
                String foundArtist = getStringField(obj, "artistName");
                double itemDur = getItemDuration(obj);

                int score = 0;
                double diff = targetDurSec > 0 && itemDur > 0 ? Math.abs(itemDur - targetDurSec) : 0;

                // Duration check: if differs by more than 8 seconds, skip
                if (targetDurSec > 0 && itemDur > 0) {
                    if (diff > 8.0) {
                        continue;
                    }
                    if (diff <= 1.0) {
                        score += 80;
                    } else if (diff <= 3.0) {
                        score += 50;
                    } else if (diff <= 6.0) {
                        score += 25;
                    }
                }

                // Title check
                if (matchesTitle(foundTitle, expectedTitle)) {
                    score += 50;
                } else if (norm(foundTitle).contains(norm(expectedTitle)) || norm(expectedTitle).contains(norm(foundTitle))) {
                    score += 20;
                }

                // Artist check (compare with firstArtist or fullArtist)
                if (!firstArtist.isBlank() && !APP_NOISE.contains(firstArtist.toLowerCase(Locale.ROOT))) {
                    if (matchesArtist(foundArtist, firstArtist) || matchesArtist(foundArtist, fullArtist)) {
                        score += 50;
                    }
                }

                if (synced != null && !synced.isBlank()) {
                    if (score > bestSyncedScore || (score == bestSyncedScore && diff < bestSyncedDiff)) {
                        bestSyncedScore = score;
                        bestSyncedDiff = diff;
                        bestSyncedMatch = obj;
                    }
                } else if (plain != null && !plain.isBlank()) {
                    if (score > bestPlainScore || (score == bestPlainScore && diff < bestPlainDiff)) {
                        bestPlainScore = score;
                        bestPlainDiff = diff;
                        bestPlainMatch = obj;
                    }
                }
            }

            // Prefer synced lyrics if score is good
            if (bestSyncedMatch != null && bestSyncedScore >= 20) {
                String synced = getStringField(bestSyncedMatch, "syncedLyrics");
                if (synced != null && !synced.isBlank()) {
                    return parseLrc(synced);
                }
            }

            // Fallback to plain lyrics (like in AQUAKEY - DYING WORLD)
            if (bestPlainMatch != null && bestPlainScore >= 20) {
                String plain = getStringField(bestPlainMatch, "plainLyrics");
                if (plain != null && !plain.isBlank()) {
                    return parsePlain(plain, getItemDuration(bestPlainMatch));
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    /**
     * Extracts the primary/first artist from multiple artists (e.g. "3umph, dope17" -> "3umph", "AQUAKEY, KUDOKUSHI" -> "AQUAKEY")
     */
    public static String getFirstArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            return "";
        }
        String[] split = artist.split("(?i)\\s*(?:,|&|\\bx\\b|feat\\.?|ft\\.?|with)\\s*");
        if (split.length > 0 && !split[0].isBlank()) {
            return split[0].trim();
        }
        return artist.trim();
    }

    /**
     * Strips everything inside parentheses (), brackets [], and curly braces {},
     * as well as common SoundCloud/YouTube tags like (prod. by ...), (slowed), (feat. ...)
     */
    public static String stripParentheses(String text) {
        if (text == null) {
            return "";
        }
        // Remove content in (), [], {}
        String s = text.replaceAll("\\([^)]*\\)", " ")
                       .replaceAll("\\[[^\\]]*\\]", " ")
                       .replaceAll("\\{[^}]*\\}", " ")
                       .replaceAll("[\"\'«»]", "")
                       .replaceAll("(?i)\\s*[-–—]?\\s*(prod\\.?|feat\\.?|ft\\.?)\\b.*$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    public static TrackInfo parseTrack(String rawArtist, String rawTitle) {
        String cleanTitle = stripParentheses(rawTitle);
        String cleanArt = stripParentheses(rawArtist);

        boolean isNoiseArtist = cleanArt.isEmpty() || APP_NOISE.contains(cleanArt.toLowerCase(Locale.ROOT));

        // In SoundCloud/YouTube, the title often has "Artist - Track"
        String[] parts = cleanTitle.split("\\s+[-–—]\\s+", 2);
        if (parts.length == 2 && (isNoiseArtist || parts[0].length() < 35)) {
            String candidateArtist = parts[0].trim();
            String candidateTitle = parts[1].trim();
            if (!candidateArtist.isEmpty() && !candidateTitle.isEmpty()) {
                cleanArt = candidateArtist;
                cleanTitle = candidateTitle;
            }
        }

        // Clean track numbering prefix e.g. "01. "
        cleanTitle = cleanTitle.replaceAll("^\\d+[.\\s-]+", "").trim();

        return new TrackInfo(cleanArt, cleanTitle);
    }

    private static boolean matchesTitle(String found, String expected) {
        if (found == null || expected == null) {
            return false;
        }
        String f = norm(stripParentheses(found));
        String e = norm(stripParentheses(expected));
        if (f.isEmpty() || e.isEmpty()) {
            return false;
        }
        if (f.equals(e)) {
            return true;
        }
        if (f.startsWith(e) || e.startsWith(f)) {
            return Math.abs(f.length() - e.length()) <= 4;
        }
        return false;
    }

    private static boolean matchesArtist(String found, String expected) {
        if (found == null || expected == null) {
            return false;
        }
        String f = norm(stripParentheses(found));
        String e = norm(stripParentheses(expected));
        if (f.isEmpty() || e.isEmpty()) {
            return false;
        }
        if (f.equals(e)) {
            return true;
        }
        return f.contains(e) || e.contains(f);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static double getItemDuration(JsonObject obj) {
        if (obj == null || !obj.has("duration")) {
            return 0.0;
        }
        try {
            return obj.get("duration").getAsDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    static List<Line> parseLrc(String lrc) {
        if (lrc == null || lrc.isBlank()) {
            return List.of();
        }
        List<Line> parsed = new ArrayList<>();
        Matcher offsetTag=Pattern.compile("(?i)\\[offset:([+-]?\\d+)]").matcher(lrc);
        long offset=offsetTag.find()?Long.parseLong(offsetTag.group(1)):0;
        for (String raw : lrc.split("\r?\n")) {
            Matcher matcher = STAMP.matcher(raw);
            List<Long> starts=new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction=matcher.group(3);
                long millis=fraction==null?0:Long.parseLong((fraction+"000").substring(0,3));
                starts.add(Math.max(0,minutes*60_000L+seconds*1000L+millis+offset));
                end = matcher.end();
            }
            if (starts.isEmpty()) {
                continue;
            }
            String text = raw.substring(end).trim();
            // An empty timed line ends the preceding lyric during an instrumental break.
            for(long start:starts) parsed.add(new Line(start,text));
        }
        parsed.sort((first, second) -> Long.compare(first.startMs(), second.startMs()));
        return parsed;
    }

    private static List<Line> parsePlain(String plain, double itemDurSec) {
        if (plain == null || plain.isBlank()) {
            return List.of();
        }
        String[] raw = plain.split("\r?\n");
        List<String> cleaned = new ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ((trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length() < 30) ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("paroles de") ||
                trimmed.toLowerCase(Locale.ROOT).startsWith("lyrics by")) {
                continue;
            }
            cleaned.add(trimmed);
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }

        long duration = MediaSession.durationMs();
        if (duration <= 0L && itemDurSec > 0) {
            duration = (long) (itemDurSec * 1000.0);
        }
        if (duration <= 0L) {
            duration = 180_000L;
        }

        long start = Math.min(8_000L, duration / 14L);
        long end = Math.max(start + 5000L, duration - 8_000L);
        long step = Math.max(1800L, (end - start) / cleaned.size());

        List<Line> result = new ArrayList<>(cleaned.size());
        for (int i = 0; i < cleaned.size(); i++) {
            result.add(new Line(start + i * step, cleaned.get(i)));
        }
        return result;
    }

    private static String httpGet(String url) {
        // Automatic retry for temporary 503 / 429
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() == 503 || response.statusCode() == 429) {
                    try {
                        Thread.sleep(300L);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                break;
            } catch (Exception ignored) {
                break;
            }
        }
        return null;
    }

    private static String getStringField(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) {
            return null;
        }
        JsonElement elem = obj.get(field);
        if (elem == null || elem.isJsonNull()) {
            return null;
        }
        try {
            return elem.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

