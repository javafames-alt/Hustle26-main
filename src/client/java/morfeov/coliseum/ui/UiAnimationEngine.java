package morfeov.coliseum.ui;

import java.util.HashMap;
import java.util.Map;

public final class UiAnimationEngine {

    private final Map<String, Float> values = new HashMap<>();
    private final Map<String, Float> pixelRawValues = new HashMap<>();
    private final Map<String, Float> textRawValues = new HashMap<>();
    private final Map<String, Float> textSnappedValues = new HashMap<>();
    private float fpsFactor = 1.0f;
    private static final float SNAP_EPSILON = 0.0015f;
    private static final float TEXT_HYSTERESIS = 0.62f;

    public void beginFrame(int fps) {
        int safeFps = Math.max(15, Math.min(360, fps));
        fpsFactor = 60.0f / safeFps;
    }

    public float animate(String key, float target, float speed, float initial) {
        float current = values.getOrDefault(key, initial);

        if (Math.abs(target - current) <= SNAP_EPSILON) {
            values.put(key, target);
            return target;
        }

        float factor = clamp(speed * 0.01f * fpsFactor, 0.0f, 1.0f);
        float next = current + (target - current) * factor;
        if (Math.abs(target - next) <= SNAP_EPSILON) {
            next = target;
        }
        values.put(key, next);
        return next;
    }

    public float animate01(String key, float target, float speed, float initial) {
        return clamp(animate(key, target, speed, initial), 0.0f, 1.0f);
    }

    public float animateLinear(String key, float target, float unitsPerFrame60, float initial) {
        float current = values.getOrDefault(key, initial);
        float diff = target - current;
        if (Math.abs(diff) <= SNAP_EPSILON) {
            values.put(key, target);
            return target;
        }

        float maxStep = Math.max(0.0f, unitsPerFrame60 * fpsFactor);
        if (maxStep <= SNAP_EPSILON) {
            values.put(key, current);
            return current;
        }

        if (Math.abs(diff) <= maxStep) {
            values.put(key, target);
            return target;
        }

        float next = current + Math.copySign(maxStep, diff);
        values.put(key, next);
        return next;
    }

    public float animatePixel(String key, float target, float speed, float initial) {
        float currentRaw = pixelRawValues.getOrDefault(key, initial);
        if (Math.abs(target - currentRaw) <= SNAP_EPSILON) {
            float snapped = Math.round(target);
            pixelRawValues.put(key, target);
            values.put(key, snapped);
            return snapped;
        }

        float factor = clamp(speed * 0.01f * fpsFactor, 0.0f, 1.0f);
        float nextRaw = currentRaw + (target - currentRaw) * factor;
        pixelRawValues.put(key, nextRaw);

        float nextPixel = Math.round(nextRaw);
        values.put(key, nextPixel);
        return nextPixel;
    }

    public float animateText(String key, float target, float speed, float initial) {
        float raw = textRawValues.getOrDefault(key, initial);
        float snapped = textSnappedValues.getOrDefault(key, (float) Math.round(initial));

        if (Math.abs(target - raw) <= SNAP_EPSILON) {
            float targetPx = Math.round(target);
            textRawValues.put(key, target);
            textSnappedValues.put(key, targetPx);
            values.put(key, targetPx);
            return targetPx;
        }

        float factor = clamp(speed * 0.01f * fpsFactor, 0.0f, 1.0f);
        float nextRaw = raw + (target - raw) * factor;
        textRawValues.put(key, nextRaw);

        while (nextRaw >= snapped + TEXT_HYSTERESIS) {
            snapped += 1.0f;
        }
        while (nextRaw <= snapped - TEXT_HYSTERESIS) {
            snapped -= 1.0f;
        }

        textSnappedValues.put(key, snapped);
        values.put(key, snapped);
        return snapped;
    }

    public float stabilizeText(String key, float rawValue) {
        float snapped = textSnappedValues.getOrDefault(key, (float) Math.round(rawValue));
        while (rawValue >= snapped + TEXT_HYSTERESIS) {
            snapped += 1.0f;
        }
        while (rawValue <= snapped - TEXT_HYSTERESIS) {
            snapped -= 1.0f;
        }
        textRawValues.put(key, rawValue);
        textSnappedValues.put(key, snapped);
        values.put(key, snapped);
        return snapped;
    }

    public void snap(String key, float value) {
        values.put(key, value);
        pixelRawValues.put(key, value);
        textRawValues.put(key, value);
        textSnappedValues.put(key, (float) Math.round(value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
