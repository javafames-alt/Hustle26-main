package morfeov.coliseum.ui;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.ClickGui;

public final class GuiMotion {
    public record Frame(float opacity, float scale, float offsetY, boolean finished) {}
    private static final long maxFrameStep = 40_000_000L;
    private long opened = -1;
    private long closed = -1;
    private long lastFrame = -1;
    private long playback;
    private Frame closeFrom;

    public static Frame initialFrame() {
        return new Frame(0.0f, 0.94f, 28.0f, false);
    }

    public Frame frame(long now) {
        if (lastFrame >= 0) {
            playback += Math.clamp(now - lastFrame, 0, maxFrameStep);
        }
        lastFrame = now;
        return sample(playback);
    }

    public void closeFrame(long now) {
        if (lastFrame < 0) {
            frame(now);
        }
        close(now);
    }

    public Frame sample(long now) {
        if (opened < 0) {
            opened = now;
        }
        ClickGui options = Coliseum.MODULES != null ? Coliseum.MODULES.get(ClickGui.class) : null;
        String mode = options != null ? options.animation.get() : "Cinematic";
        if ("Off".equals(mode)) {
            if (closed >= 0) {
                return new Frame(0.0f, 1.0f, 0.0f, true);
            }
            return new Frame(1.0f, 1.0f, 0.0f, false);
        }

        if (closed >= 0) {
            float t = progress(now, closed, 300_000_000L);
            float e = smooth(t);
            float closeOpacity = closeFrom.opacity * (1.0f - e);
            Frame initial = initialFrame();
            float closeScale = lerp(closeFrom.scale, "Fade".equals(mode) || "Slide".equals(mode) ? 1.0f : initial.scale, e);
            float closeOffset = lerp(closeFrom.offsetY, "Fade".equals(mode) ? 0.0f : initial.offsetY, e);
            return new Frame(closeOpacity, closeScale, closeOffset, t >= 1.0f);
        }

        float t = progress(now, opened, 420_000_000L);
        float e = smooth(t);
        float opacity = smooth(progress(now, opened, 320_000_000L));
        Frame initial = initialFrame();

        if ("Fade".equals(mode)) {
            return new Frame(opacity, 1.0f, 0.0f, false);
        }
        if ("Slide".equals(mode)) {
            return new Frame(opacity, 1.0f, initial.offsetY * (1.0f - e), false);
        }
        return new Frame(opacity, lerp(initial.scale, 1.0f, e), initial.offsetY * (1.0f - e), false);
    }

    public void close(long now) {
        if (closed >= 0) return;
        long time = lastFrame >= 0 ? playback : now;
        closeFrom = sample(time);
        closed = time;
        if (lastFrame >= 0) {
            lastFrame = now;
        }
    }

    public boolean closing() {
        return closed >= 0;
    }

    public static float cinematic(float value) {
        float x = Math.clamp(value, 0.0f, 1.0f);
        float t = 1.0f - x;
        return 1.0f - t * t * t * t;
    }

    /** Cubic easing retained for category transitions. */
    public static float ease(float value) {
        float t = 1.0f - Math.clamp(value, 0.0f, 1.0f);
        return 1.0f - t * t * t;
    }

    public static float smooth(float t) {
        t = Math.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float progress(long now, long start, long duration) {
        return Math.clamp((now - start) / (float) duration, 0.0f, 1.0f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
