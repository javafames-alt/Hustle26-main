package im.yaHustler.hud;

public final class AnimatedValue {

    private float value;
    private boolean initialized;
    private long lastUpdateNanos = -1L;

    public float update(float target, float smoothTime, long nowNanos) {
        if (!initialized) {
            this.value = target;
            this.initialized = true;
            this.lastUpdateNanos = nowNanos;
            return this.value;
        }

        if (smoothTime <= 0.0f) {
            this.value = target;
            this.lastUpdateNanos = nowNanos;
            return this.value;
        }

        if (lastUpdateNanos < 0L) {
            lastUpdateNanos = nowNanos;
        }

        float deltaTime = (nowNanos - lastUpdateNanos) / 1_000_000_000.0f;
        lastUpdateNanos = nowNanos;

        float safeDeltaTime = clamp(deltaTime, 0.0f, 0.10f);
        float safeSmoothTime = Math.max(0.0001f, smoothTime);
        float factor = 1.0f - (float) Math.exp(-safeDeltaTime / safeSmoothTime);
        this.value += (target - this.value) * factor;

        if (Math.abs(target - this.value) <= 0.0005f) {
            this.value = target;
        }

        return this.value;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
        this.initialized = true;
        this.lastUpdateNanos = -1L;
    }

    public void reset() {
        this.value = 0.0f;
        this.initialized = false;
        this.lastUpdateNanos = -1L;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
