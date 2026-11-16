package im.yaHustler.render.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import im.yaHustler.hud.Lyrics;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Playback-driven captions. Their positions and rotations are captured once per lyric. */
public final class Music3DScene {
    public record Placement(Vec3 position, Quaternionf rotation, double distance) {
        public Placement { rotation = new Quaternionf(rotation); }
    }

    public static final class Caption {
        private final Lyrics.Line line;
        private final Placement placement;
        private final double born;
        private double retired = Double.NaN;
        private float progress;

        private Caption(Lyrics.Line line, Placement placement, double born) {
            this.line = line;
            this.placement = placement;
            this.born = born;
        }

        public Lyrics.Line line() { return line; }
        public Placement placement() { return placement; }
        public float progress() { return progress; }
        public double age(double clock) { return Math.max(0, clock - born); }
        public boolean retiring() { return !Double.isNaN(retired); }
        public float retirement(double clock, double trailSeconds) {
            return retiring() ? (float)Math.clamp((clock - retired) / Math.max(.001, trailSeconds), 0, 1) : 0;
        }
    }

    private static final int MAX_CAPTIONS = 4;
    private final List<Caption> captions = new ArrayList<>();
    private final List<Caption> view = Collections.unmodifiableList(captions);
    private Caption current;
    private String track = "";
    private long lastNanos = Long.MIN_VALUE, lastPosition;
    private boolean wasPlaying;
    private double clock;

    public List<Caption> captions() { return view; }
    public double clock() { return clock; }

    public void clear() {
        captions.clear();
        current = null;
        track = "";
        lastNanos = Long.MIN_VALUE;
        wasPlaying = false;
        clock = 0;
    }

    public void update(String track, long position, boolean playing, Lyrics.Cue cue, long nowNanos,
                       double trailSeconds, Supplier<Placement> placement) {
        double elapsed = lastNanos == Long.MIN_VALUE ? 0 : Math.max(0, (nowNanos - lastNanos) / 1e9);
        long movement = position - lastPosition;
        double expected = wasPlaying ? elapsed * 1000 : 0;
        boolean seek = lastNanos != Long.MIN_VALUE
                && (movement < -200 || Math.abs(movement - expected) > 1500);
        if (!this.track.equals(track) || seek) clear();
        if (lastNanos != Long.MIN_VALUE && playing && wasPlaying) clock += elapsed;
        this.track = track;
        lastNanos = nowNanos;
        lastPosition = position;
        wasPlaying = playing;

        Lyrics.Line line = cue == null ? null : cue.line();
        if (current != null && !current.line.equals(line)) {
            current.retired = clock;
            current = null;
        }
        // Pausing retains the existing caption, but never spawns a new one until playback resumes.
        if (current == null && line != null && playing) {
            current = new Caption(line, placement.get(), clock);
            captions.add(current);
        }
        if (current != null) current.progress = cue.progress();
        captions.removeIf(caption -> caption.retiring() && (trailSeconds <= 0 || caption.retirement(clock, trailSeconds) >= 1));
        while (captions.size() > MAX_CAPTIONS) captions.removeFirst();
    }
}
