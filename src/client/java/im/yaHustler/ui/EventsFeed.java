package im.yaHustler.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EventsFeed {
    enum Source {
        FUN_TIME_EVENTS("https://funtime.me/api/backend/api/v1/events"),
        FUN_TIME_MINES("https://funtime.me/api/backend/api/v1/plugin/mines?server-types=all"),
        HOLY_WORLD("https://api.holyworld.me/v1/events");

        final String endpoint;

        Source(String endpoint) { this.endpoint = endpoint; }
    }

    record Entry(String server, String name, String rarity, String detail, String coords, int seconds, long fetched) {
        int remaining(long now) {
            return seconds < 0 ? -1 : (int) Math.max(0, seconds - Math.max(0, now - fetched) / 1000);
        }
    }

    static List<Entry> parse(Source source, String body, long now) {
        JsonElement root = JsonParser.parseString(body);
        List<Entry> entries = new ArrayList<>();
        switch (source) {
            case FUN_TIME_MINES -> parseMines(root.getAsJsonObject(), entries, now);
            case FUN_TIME_EVENTS -> parseFunTime(root.getAsJsonObject(), entries, now);
            case HOLY_WORLD -> parseHolyWorld(root, "", entries, now);
        }
        entries.sort(Comparator.comparing(Entry::server).thenComparingInt(e -> e.seconds < 0 ? Integer.MAX_VALUE : e.seconds));
        return List.copyOf(entries);
    }

    private static void parseMines(JsonObject root, List<Entry> entries, long now) {
        JsonElement servers = root.get("servers");
        if (servers == null || !servers.isJsonObject()) throw new IllegalArgumentException("Missing servers");
        for (var server : servers.getAsJsonObject().entrySet()) {
            for (JsonElement item : server.getValue().getAsJsonArray()) {
                JsonObject mine = item.getAsJsonObject();
                entries.add(new Entry(text(mine, server.getKey(), "server-ru-name"), "Авто-Шахта",
                    rarity(text(mine, "", "mine-rarity")),
                    "Следующая: " + rarity(text(mine, "", "next-mine-rarity")), "",
                    number(mine, "reset-seconds-left"), now));
            }
        }
    }

    private static void parseFunTime(JsonObject root, List<Entry> entries, long now) {
        JsonElement response = root.get("response");
        if (response == null || !response.isJsonArray()) throw new IllegalArgumentException("Missing response");
        for (JsonElement item : response.getAsJsonArray()) {
            JsonObject server = item.getAsJsonObject();
            if (!server.has("events") || !server.get("events").isJsonArray()) continue;
            for (JsonElement event : server.getAsJsonArray("events")) {
                JsonObject ev = event.getAsJsonObject();
                String id = text(ev, "", "id"), phase = text(ev, "", "phase");
                if (phase.equals("CLOSED") || phase.equals("STOPPING")) continue;
                entries.add(new Entry(text(server, "FunTime", "server"), eventName(id),
                    rarity(text(ev, "", "loot")), phase(phase), coordinates(ev, "location-event"),
                    number(ev, "time-seconds-left"), now));
            }
        }
    }

    private static void parseHolyWorld(JsonElement value, String server, List<Entry> entries, long now) {
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) parseHolyWorld(item, server, entries, now);
            return;
        }
        if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid events response");
        JsonObject object = value.getAsJsonObject();
        String name = text(object, "", "name", "event", "type", "id");
        if (!name.isEmpty()) {
            entries.add(new Entry(text(object, server, "server"), eventName(name),
                rarity(text(object, "", "rarity", "loot")), phase(text(object, "", "phase", "status")),
                coordinates(object, "location"), number(object, "time-seconds-left", "secondsLeft", "timeLeft"), now));
            return;
        }
        if (object.has("events")) {
            parseHolyWorld(object.get("events"), text(object, server, "server"), entries, now);
            return;
        }
        for (var group : object.entrySet()) parseHolyWorld(group.getValue(), group.getKey(), entries, now);
    }

    static String rarity(String raw) {
        String clean = clean(raw);
        return switch (clean.toLowerCase(Locale.ROOT)) {
            case "", "null", "unknown", "none" -> "Неизвестна";
            case "default", "common", "normal", "обычная", "обычный" -> "Обычная";
            case "rare", "редкий", "редкая" -> "Редкая";
            case "epic", "эпический", "эпическая" -> "Эпическая";
            case "legendary", "легендарный", "легендарная" -> "Легендарная";
            case "mythic", "mythical", "мифический", "мифическая" -> "Мифическая";
            case "elite", "элитный", "элитная" -> "Элитная";
            case "rich", "богатый", "богатая" -> "Богатая";
            case "solid", "солидный", "солидная" -> "Солидная";
            default -> clean;
        };
    }

    static int rarityColor(String rarity) {
        return switch (rarity) {
            case "Легендарная" -> 0xffffc45c;
            case "Мифическая" -> 0xffff7fb8;
            case "Эпическая", "Элитная" -> 0xffbb94ff;
            case "Редкая", "Солидная" -> 0xff78bfff;
            case "Богатая" -> 0xff75dfa3;
            case "Проклятые сокровища" -> 0xffff7d7d;
            default -> 0xffbac5d2;
        };
    }

    private static String eventName(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "" -> "Следующее событие";
            case "altarundead" -> "Алтарь нежити";
            case "airdrop", "air_drop" -> "Аирдроп";
            case "myst_beacon" -> "Мистический маяк";
            case "meteor_rain" -> "Метеоритный дождь";
            case "vulkan", "volcano" -> "Вулкан";
            case "deathchest" -> "Сундук смерти";
            case "hellm" -> "Адская шахта";
            default -> clean(id);
        };
    }

    private static String phase(String phase) {
        return switch (phase.toUpperCase(Locale.ROOT)) {
            case "WAITING" -> "Ожидание";
            case "ACTIVATING" -> "Активация";
            case "RUNNING" -> "Активен";
            case "LOOTING" -> "Можно забирать лут";
            case "CLOSED" -> "Завершён";
            case "STOPPING" -> "Завершается";
            default -> clean(phase);
        };
    }

    private static String coordinates(JsonObject object, String key) {
        if (object.has("location-announced") && !object.get("location-announced").getAsBoolean()) return "";
        JsonElement location = object.get(key);
        JsonObject coords = location != null && location.isJsonObject() ? location.getAsJsonObject() : object;
        if (!coords.has("x") || !coords.has("y") || !coords.has("z")) return "";
        return number(coords, "x") + " " + number(coords, "y") + " " + number(coords, "z");
    }

    private static int number(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()) continue;
            try { return value.getAsInt(); } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    private static String text(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) return clean(value.getAsString());
        }
        return fallback;
    }

    private static String clean(String text) { return text.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").strip(); }
}
