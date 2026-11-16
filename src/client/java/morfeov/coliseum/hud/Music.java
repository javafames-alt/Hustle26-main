package morfeov.coliseum.hud;

import com.mojang.blaze3d.platform.NativeImage;
import morfeov.coliseum.font.FigmaFont;
import morfeov.coliseum.font.MsdfPipeline;
import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.ui.Draw;
import morfeov.coliseum.ui.InterfaceAssets;
import morfeov.coliseum.ui.InterfaceTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Live system media card: artwork, metadata, synced lyrics and a timed progress rail. */
public final class Music extends HudElement {
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("coliseum", "media/cover");
    private static final int WIDTH = 172, COVER_SIZE = 32;
    private String title, artist, lastCover = "";
    private boolean media, coverReady, showCover, showLyrics;
    private int coverW, coverH, height;
    private long coverCheck, duration, position;
    private Lyrics.Cue lyric;
    private String layoutText="";
    private LyricLayout lyricLayout;
    private final AnimatedValue progress = new AnimatedValue();
    private final TextMarquee titleScroll = new TextMarquee(), artistScroll = new TextMarquee();

    public Music(NumberSetting x, NumberSetting y) { super(x, y); }
    @Override protected float radius() { return 8; }

    @Override protected void measure(Minecraft mc, boolean editing) {
        MediaSession.keepAlive();
        media = MediaSession.present();
        if (!media && !editing) return;
        var options = HudStyle.options();
        showCover = options == null || options.musicCover.enabled();
        showLyrics = options == null || options.musicLyrics.enabled();
        title = media ? MediaSession.title() : "Music";
        artist = media ? MediaSession.artist() : "Музыка не играет";
        if (media && options != null && options.musicSource.enabled() && !MediaSession.source().isEmpty())
            artist += (artist.isEmpty() ? "" : " • ") + MediaSession.source();
        duration = media ? Math.max(0, MediaSession.durationMs()) : 0;
        position = media ? Math.max(0, MediaSession.positionMs()) : 0;
        if (duration > 0) position = Math.min(position, duration);
        lyric = null;
        if (media && showLyrics) {
            Lyrics.ensure(MediaSession.artist(), MediaSession.title());
            lyric = duration>0 && position>=duration ? null : Lyrics.cue(position);
            if(lyric!=null && !lyric.line().text().equals(layoutText)) {
                layoutText=lyric.line().text();
                lyricLayout=LyricLayout.measure(layoutText,WIDTH-12);
            }
        }
        height = showLyrics ? 82 : 52;
        size(WIDTH, height);
        updateCover(mc);
    }

    private void updateCover(Minecraft mc) {
        String key = MediaSession.coverKey();
        long now = System.currentTimeMillis();
        if (!media || !showCover || key.isBlank() || key.equals(lastCover) || now - coverCheck < 500) return;
        coverCheck = now;
        try {
            byte[] bytes = MediaSession.artwork();
            if (bytes == null || bytes.length == 0) {
                var file = MediaSession.coverFile();
                if (!file.isFile()) return;
                bytes = java.nio.file.Files.readAllBytes(file.toPath());
            }
            NativeImage image = NativeImage.read(bytes);
            coverW = image.getWidth(); coverH = image.getHeight();
            if (coverReady) mc.getTextureManager().release(COVER);
            mc.getTextureManager().register(COVER, new DynamicTexture(() -> "Coliseum media cover", image));
            coverReady = true; lastCover = key;
        } catch (Exception ignored) { /* Retry next poll; a player may still be updating its thumbnail. */ }
    }

    @Override protected void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        HudStyle.panel(g, x, y, WIDTH, height, 1, 8);
        if (showCover) drawCover(g, x + 6, y + 7);
        int textX = x + (showCover ? 44 : 7), maxWidth = x + WIDTH - 23 - textX;
        scrollingText(g, title, textX, y + 12, maxWidth, HudStyle.TEXT, 8.2f, true, titleScroll);
        scrollingText(g, artist, textX, y + 22, maxWidth, HudStyle.MUTED, 6.75f, false, artistScroll);
        drawEqualizer(g, x + WIDTH - 8, y + 16);
        if (showLyrics) drawLyrics(g, x + 6, y + 46);
        drawTimeline(g, x, y + height - 11);
    }

    private static void scrollingText(GuiGraphicsExtractor g, String text, float x, float cy, int width,
                                      int color, float font, boolean bold, TextMarquee marquee) {
        float measured = bold ? FigmaFont.mediumWidth(text, font / 11) : FigmaFont.width(text, font / 11);
        float offset = marquee.offset(text, measured - width, System.nanoTime());
        g.enableScissor((int)Math.floor(x), (int)Math.floor(cy - 5), (int)Math.ceil(x + width), (int)Math.ceil(cy + 5));
        try { HudStyle.text(g, text, x - offset, cy, color, font, bold); }
        finally { g.disableScissor(); }
    }

    private void drawCover(GuiGraphicsExtractor g, int x, int y) {
        Draw.roundedF(g, x, y, COVER_SIZE, COVER_SIZE, 5, InterfaceTheme.alpha(HudStyle.accent(), .25f));
        if (media && coverReady && lastCover.equals(MediaSession.coverKey())) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.blit(MsdfPipeline.cover(), COVER, 0, 0, 0, 0, COVER_SIZE, COVER_SIZE,
                coverW, coverH, coverW, coverH, 0xffffffff);
            g.pose().popMatrix();
        } else InterfaceAssets.icon(g, "hud/activity", x + 9, y + 9, 14, HudStyle.TEXT);
    }

    private void drawEqualizer(GuiGraphicsExtractor g, float right, float bottom) {
        double seconds = System.nanoTime() / 1e9;
        for (int i = 0; i < 4; i++) {
            float h = media && MediaSession.playing()
                ? (float)(2.5 + 6 * (.5 + .5 * Math.sin(seconds * (2.8 + i * .45) + i * 1.5))) : 2.5f;
            Draw.roundedF(g, right - i * 3.5f, bottom - h, 1.5f, h, .75f, HudStyle.accent());
        }
    }

    private void drawLyrics(GuiGraphicsExtractor g, float x, float cy) {
        if(lyric==null) {
            if(!media || !Lyrics.has()) HudStyle.text(g,media?"Текст песни недоступен":"Здесь появится текст песни",x,cy,HudStyle.MUTED,7.5f,true);
            return;
        }
        float alpha=Math.min(1,Math.max(0,(lyric.endMs()-position)/180f));
        int chars=Math.round(lyricLayout.characters()*lyric.progress());
        for(var row:lyricLayout.page(lyric.progress())) {
            HudStyle.text(g,row.text(),x,cy,InterfaceTheme.alpha(HudStyle.MUTED,alpha),lyricLayout.font(),true);
            HudStyle.text(g,row.sung(chars),x,cy,InterfaceTheme.alpha(HudStyle.TEXT,alpha),lyricLayout.font(),true);
            cy+=10.5f;
        }
    }

    private void drawTimeline(GuiGraphicsExtractor g, int x, float cy) {
        String elapsed = time(position), remaining = duration > 0 ? "-" + time(Math.max(0, duration - position)) : "--:--";
        float font = 5.75f, left = HudStyle.width(elapsed, font), right = HudStyle.width(remaining, font);
        HudStyle.text(g, elapsed, x + 6, cy, HudStyle.MUTED, font, false);
        HudStyle.text(g, remaining, x + WIDTH - 6 - right, cy, HudStyle.MUTED, font, false);
        float bx = x + 13 + left, bw = WIDTH - 26 - left - right;
        float ratio = progress.update(duration > 0 ? Math.clamp(position / (float)duration, 0, 1) : 0, .1f, System.nanoTime());
        Draw.roundedF(g, bx, cy - .75f, bw, 1.5f, .75f, 0x38ffffff);
        Draw.roundedF(g, bx, cy - .75f, bw * ratio, 1.5f, .75f, HudStyle.accent());
        Draw.circle(g, bx + bw * ratio, cy, 2, HudStyle.TEXT);
    }

    private static String time(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
