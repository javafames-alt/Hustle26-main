package im.yaHustler.ui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GuiEventsPage {
    private record Snapshot(List<EventsFeed.Entry> entries, String error, long updated) {}
    private static final class FeedState {
        volatile Snapshot snapshot = new Snapshot(List.of(), "", 0);
        volatile boolean loading;
        long attempted;
    }
    private record Hit(EventsFeed.Entry entry, int x, int y, int w) {}
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final EnumMap<EventsFeed.Source, FeedState> feeds = new EnumMap<>(EventsFeed.Source.class);
    private final List<Hit> hits = new ArrayList<>();
    private EventsFeed.Source source = EventsFeed.Source.FUN_TIME_EVENTS;
    private boolean mines;
    private String rarity = "Все редкости", lastQuery = "";
    private List<String> rarities = List.of("Все редкости");
    private List<EventsFeed.Entry> visible = List.of();
    private Snapshot displayed;
    private int scroll, total, x, y, w, h;

    GuiEventsPage() {
        for (EventsFeed.Source feed : EventsFeed.Source.values()) feeds.put(feed, new FeedState());
    }

    void open() {
        FeedState state = feeds.get(source);
        if (System.currentTimeMillis() - state.attempted >= 30_000) refresh();
    }

    private void refresh() {
        EventsFeed.Source requested = source;
        FeedState state = feeds.get(requested);
        if (state.loading) return;
        state.loading = true;
        state.attempted = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(requested.endpoint))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", "Hustle 26.3/26.3")
                    .header("Accept", "application/json").GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
                long now = System.currentTimeMillis();
                state.snapshot = new Snapshot(EventsFeed.parse(requested, response.body(), now), "", now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed(state);
            } catch (Exception e) {
                failed(state);
            } finally {
                state.loading = false;
            }
        });
    }

    private static void failed(FeedState state) {
        Snapshot previous = state.snapshot;
        state.snapshot = new Snapshot(previous.entries, "Ошибка загрузки. Нажмите «Обновить»", previous.updated);
    }

    private void select(EventsFeed.Source next) {
        if (source == next) return;
        source = next;
        rarity = "Все редкости";
        scroll = 0;
        hits.clear();
        displayed = null;
        open();
    }

    private static final int ROW = 20, GAP = 6, TOP = 56, CARD = 64, STEP = 70, REFRESH = 64;
    private String copiedCoords = "";
    private long copiedAt;

    private int col() { return (w - GAP) / 2; }

    void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, String query, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        open();
        FeedState state = feeds.get(source);
        Snapshot snapshot = state.snapshot;
        boolean holyWorld = source == EventsFeed.Source.HOLY_WORLD;
        int col = col(), right = x + col + GAP;
        // Ряд 1: сервер | редкость + обновить. Ряд 2: тип | статус. Колонки совпадают с сеткой карточек.
        segmented(g, x, y + 4, col, new String[]{"FunTime", "HolyWorld"}, holyWorld ? 1 : 0, mx, my);
        button(g, right, y + 4, col - REFRESH - GAP, "Редкость: " + rarity, false, mx, my);
        button(g, right + col - REFRESH, y + 4, REFRESH, state.loading ? "Загрузка" : "Обновить", true, mx, my);
        if (!holyWorld) segmented(g, x, y + 28, col, new String[]{"Ивенты", "Шахты"}, mines ? 1 : 0, mx, my);
        else segmented(g, x, y + 28, col, new String[]{"Ивенты"}, 0, mx, my);
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (snapshot != displayed || !normalized.equals(lastQuery)) {
            if (!normalized.equals(lastQuery)) scroll = 0;
            displayed = snapshot;
            lastQuery = normalized;
            List<String> options = new ArrayList<>();
            options.add("Все редкости");
            snapshot.entries.stream().map(EventsFeed.Entry::rarity).distinct().sorted().forEach(options::add);
            rarities = List.copyOf(options);
            if (!rarities.contains(rarity)) rarity = "Все редкости";
            visible = snapshot.entries.stream().filter(e -> (rarity.equals("Все редкости") || e.rarity().equals(rarity))
                && (e.name() + " " + e.server() + " " + e.rarity() + " " + e.coords()).toLowerCase(Locale.ROOT).contains(normalized)).toList();
        }
        boolean error = !snapshot.error.isEmpty();
        String status = error ? snapshot.error : state.loading ? "Обновление..." : "Найдено: " + visible.size() + " · обновление каждые 30 с";
        Draw.rounded(g, right, y + 28, col, ROW, 5, 0x0cffffff);
        Draw.circle(g, right + 9, y + 38, 2.2f, error ? 0xffff6b6b : state.loading ? 0xffffd36b : 0xff6bff9a);
        FigmaFont.draw(g, FigmaFont.fit(status, col - 22, 4.8f / 11), right + 16, y + 35, error ? 0xffff9a9a : 0xff9aa5ba, 4.8f / 11);

        total = ((visible.size() + 1) / 2) * STEP;
        scroll = Math.clamp(scroll, 0, Math.max(0, total - (h - TOP)));
        hits.clear();
        g.enableScissor(x, y + TOP, x + w, y + h);
        long now = System.currentTimeMillis();
        for (int i = 0; i < visible.size(); i++) {
            EventsFeed.Entry e = visible.get(i);
            int bx = x + i % 2 * (col + GAP), by = y + TOP + i / 2 * STEP - scroll;
            if (by + CARD <= y + TOP || by >= y + h) continue;
            card(g, e, bx, by, col, holyWorld, now, Draw.inside(mx, my, bx, by, col, CARD) && my >= y + TOP);
            hits.add(new Hit(e, bx, by, col));
        }
        if (visible.isEmpty()) {
            String message = state.loading ? "Загрузка..." : error ? "Данные недоступны"
                : snapshot.entries.isEmpty() ? "Нет активных событий" : "Ничего не найдено";
            float mw = FigmaFont.width(message, 6f / 11);
            FigmaFont.draw(g, message, x + (w - mw) / 2f, y + TOP + 30, 0xff8591a7, 6f / 11);
        }
        g.disableScissor();
    }

    private void card(GuiGraphicsExtractor g, EventsFeed.Entry e, int bx, int by, int bw, boolean holyWorld, long now, boolean hover) {
        int color = EventsFeed.rarityColor(e.rarity());
        Draw.rounded(g, bx, by, bw, CARD, 7, InterfaceTheme.tint(hover ? 0xd61a2130 : 0xb8111520, true, .14f));
        Draw.horizontalGradient(g, bx + 3, by + 3, bw * .55f, CARD - 6, ColorUtil.alpha(color, hover ? 34 : 22), 0x00000000);
        Draw.rounded(g, bx + 6, by + 9, 2, CARD - 18, 1, ColorUtil.alpha(color, 220));
        if (hover) Draw.roundedOutline(g, bx, by, bw, CARD, 7, 1, ColorUtil.alpha(color, 130));
        // Бейдж редкости справа сверху.
        float bs = 4.5f / 11;
        String badge = FigmaFont.fit(e.rarity(), bw / 3, bs);
        int badgeW = (int) Math.ceil(FigmaFont.width(badge, bs)) + 10;
        Draw.rounded(g, bx + bw - badgeW - 7, by + 7, badgeW, 11, 5, ColorUtil.alpha(color, 50));
        FigmaFont.draw(g, badge, bx + bw - badgeW - 2, by + 10, color, bs);
        FigmaFont.drawMedium(g, FigmaFont.fitMedium(e.name(), bw - badgeW - 26, 6.5f / 11), bx + 13, by + 8, 0xffffffff, 6.5f / 11);
        line(g, (holyWorld ? "HolyWorld" : "FunTime") + " · " + e.server(), bx, by + 21, bw, 0xff8591a7);
        int seconds = e.remaining(now);
        String timer = seconds < 0 ? "Время не указано" : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        boolean mine = source == EventsFeed.Source.FUN_TIME_MINES;
        line(g, timer + (e.detail().isEmpty() || mine ? "" : " · " + e.detail()), bx, by + 32, bw, FigmaGuiRenderer.accent());
        boolean copied = !e.coords().isEmpty() && e.coords().equals(copiedCoords) && now - copiedAt < 1500;
        String last = mine ? e.detail() : copied ? "Скопировано!" : e.coords().isEmpty() ? "Координаты не объявлены"
            : (hover ? "Нажмите, чтобы скопировать: " : "Координаты: ") + e.coords();
        line(g, last, bx, by + 46, bw, copied ? 0xff7dffa8 : hover && !e.coords().isEmpty() ? 0xffffffff : 0xffcdd0de);
    }

    private static void line(GuiGraphicsExtractor g, String text, int x, int y, int w, int color) {
        FigmaFont.draw(g, FigmaFont.fit(text, w - 21, 5f / 11), x + 13, y, color, 5f / 11);
    }

    private static void centered(GuiGraphicsExtractor g, String label, float x, int y, float w, int color) {
        float s = 5.5f / 11;
        String text = FigmaFont.fit(label, (int) w - 10, s);
        FigmaFont.draw(g, text, x + (w - FigmaFont.width(text, s)) / 2f, y + 7, color, s);
    }

    private static void button(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean accent, int mx, int my) {
        boolean hover = Draw.inside(mx, my, x, y, w, ROW);
        int fill = accent ? ColorUtil.alpha(FigmaGuiRenderer.accent(), hover ? 90 : 60) : hover ? 0x22ffffff : 0x14ffffff;
        Draw.rounded(g, x, y, w, ROW, 5, fill);
        centered(g, label, x, y, w, hover || accent ? 0xffffffff : 0xffcdd0de);
    }

    /** Сегментированный переключатель: общий фон и подсвеченный активный сегмент одинаковой ширины. */
    private static void segmented(GuiGraphicsExtractor g, int x, int y, int w, String[] labels, int active, int mx, int my) {
        Draw.rounded(g, x, y, w, ROW, 5, 0x14ffffff);
        float sw = (float) w / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float sx = x + i * sw;
            boolean hover = mx >= sx && mx < sx + sw && my >= y && my < y + ROW;
            if (i == active) Draw.roundedF(g, sx + 2, y + 2, sw - 4, ROW - 4, 4, ColorUtil.alpha(FigmaGuiRenderer.accent(), 80));
            else if (hover) Draw.roundedF(g, sx + 2, y + 2, sw - 4, ROW - 4, 4, 0x10ffffff);
            centered(g, labels[i], sx, y, sw, i == active ? 0xffffffff : hover ? 0xffe6e8f0 : 0xff9aa5ba);
        }
    }

    boolean click(double mx, double my) {
        int col = col(), right = x + col + GAP, half = col / 2;
        if (Draw.inside(mx, my, x, y + 4, half, ROW)) {
            select(mines ? EventsFeed.Source.FUN_TIME_MINES : EventsFeed.Source.FUN_TIME_EVENTS); return true;
        }
        if (Draw.inside(mx, my, x + half, y + 4, col - half, ROW)) { select(EventsFeed.Source.HOLY_WORLD); return true; }
        if (Draw.inside(mx, my, right, y + 4, col - REFRESH - GAP, ROW)) {
            rarity = rarities.get((rarities.indexOf(rarity) + 1) % rarities.size());
            scroll = 0; displayed = null; return true;
        }
        if (Draw.inside(mx, my, right + col - REFRESH, y + 4, REFRESH, ROW)) { refresh(); return true; }
        if (source != EventsFeed.Source.HOLY_WORLD) {
            if (Draw.inside(mx, my, x, y + 28, half, ROW)) { mines = false; select(EventsFeed.Source.FUN_TIME_EVENTS); return true; }
            if (Draw.inside(mx, my, x + half, y + 28, col - half, ROW)) { mines = true; select(EventsFeed.Source.FUN_TIME_MINES); return true; }
        }
        if (my >= y + TOP && my < y + h) for (Hit hit : hits) {
            if (Draw.inside(mx, my, hit.x, hit.y, hit.w, CARD) && !hit.entry.coords().isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(hit.entry.coords());
                copiedCoords = hit.entry.coords(); copiedAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    void scroll(double amount) { scroll = Math.clamp(scroll - (int) (amount * 18), 0, Math.max(0, total - (h - TOP))); }
}
