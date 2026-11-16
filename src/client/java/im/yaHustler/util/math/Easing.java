package im.yaHustler.util.math;

import java.util.concurrent.ThreadLocalRandom;

public final class Easing {

    private static final float C1 = 1.70158F;
    private static final float C2 = C1 * 1.525F;
    private static final float C3 = C1 + 1.0F;
    private static final float C4 = (float) ((2.0 * Math.PI) / 3.0);
    private static final float C5 = (float) ((2.0 * Math.PI) / 4.5);

    private static final EasingType[] DYNAMIC_CURVES = {
            EasingType.EaseInOutSine,
            EasingType.EaseOutSine,
            EasingType.EaseInOutQuad,
            EasingType.EaseOutQuad,
            EasingType.EaseInOutCubic,
            EasingType.EaseOutCubic,
            EasingType.EaseInOutQuart,
            EasingType.EaseOutQuart,
            EasingType.EaseInOutQuint,
            EasingType.EaseOutQuint,
            EasingType.EaseInOutExpo,
            EasingType.EaseOutExpo,
            EasingType.EaseInOutCirc,
            EasingType.EaseOutCirc,
            EasingType.EaseInOutBack,
            EasingType.EaseOutBack
    };

    private static volatile EasingType currentDynamic = EasingType.EaseInOutCubic;

    private Easing() {
    }

    public static EasingType getCurrentDynamic() {
        return currentDynamic;
    }

    public static void rotateDynamic() {
        int idx = ThreadLocalRandom.current().nextInt(DYNAMIC_CURVES.length);
        currentDynamic = DYNAMIC_CURVES[idx];
    }

    public static float apply(EasingType type, float x) {
        if (type == null) return x;
        x = Math.max(0.0F, Math.min(1.0F, x));

        if (type == EasingType.Dynamic) {
            return apply(currentDynamic, x);
        }

        return switch (type) {
            case Linear -> x;
            case EaseInSine -> easeInSine(x);
            case EaseOutSine -> easeOutSine(x);
            case EaseInOutSine -> easeInOutSine(x);
            case EaseInQuad -> easeInQuad(x);
            case EaseOutQuad -> easeOutQuad(x);
            case EaseInOutQuad -> easeInOutQuad(x);
            case EaseInCubic -> easeInCubic(x);
            case EaseOutCubic -> easeOutCubic(x);
            case EaseInOutCubic -> easeInOutCubic(x);
            case EaseInQuart -> easeInQuart(x);
            case EaseOutQuart -> easeOutQuart(x);
            case EaseInOutQuart -> easeInOutQuart(x);
            case EaseInQuint -> easeInQuint(x);
            case EaseOutQuint -> easeOutQuint(x);
            case EaseInOutQuint -> easeInOutQuint(x);
            case EaseInExpo -> easeInExpo(x);
            case EaseOutExpo -> easeOutExpo(x);
            case EaseInOutExpo -> easeInOutExpo(x);
            case EaseInCirc -> easeInCirc(x);
            case EaseOutCirc -> easeOutCirc(x);
            case EaseInOutCirc -> easeInOutCirc(x);
            case EaseInBack -> easeInBack(x);
            case EaseOutBack -> easeOutBack(x);
            case EaseInOutBack -> easeInOutBack(x);
            case EaseInElastic -> easeInElastic(x);
            case EaseOutElastic -> easeOutElastic(x);
            case EaseInOutElastic -> easeInOutElastic(x);
            case EaseInBounce -> easeInBounce(x);
            case EaseOutBounce -> easeOutBounce(x);
            case EaseInOutBounce -> easeInOutBounce(x);
            default -> x;
        };
    }

    // Sine
    public static float easeInSine(float x) {
        return 1.0F - (float) Math.cos((x * Math.PI) / 2.0);
    }

    public static float easeOutSine(float x) {
        return (float) Math.sin((x * Math.PI) / 2.0);
    }

    public static float easeInOutSine(float x) {
        return -(float) (Math.cos(Math.PI * x) - 1.0) / 2.0F;
    }

    // Quad
    public static float easeInQuad(float x) {
        return x * x;
    }

    public static float easeOutQuad(float x) {
        return 1.0F - (1.0F - x) * (1.0F - x);
    }

    public static float easeInOutQuad(float x) {
        return x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0 * x + 2.0, 2.0) / 2.0F;
    }

    // Cubic
    public static float easeInCubic(float x) {
        return x * x * x;
    }

    public static float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0 - x, 3.0);
    }

    public static float easeInOutCubic(float x) {
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0 * x + 2.0, 3.0) / 2.0F;
    }

    // Quart
    public static float easeInQuart(float x) {
        return x * x * x * x;
    }

    public static float easeOutQuart(float x) {
        return 1.0F - (float) Math.pow(1.0 - x, 4.0);
    }

    public static float easeInOutQuart(float x) {
        return x < 0.5F ? 8.0F * x * x * x * x : 1.0F - (float) Math.pow(-2.0 * x + 2.0, 4.0) / 2.0F;
    }

    // Quint
    public static float easeInQuint(float x) {
        return x * x * x * x * x;
    }

    public static float easeOutQuint(float x) {
        return 1.0F - (float) Math.pow(1.0 - x, 5.0);
    }

    public static float easeInOutQuint(float x) {
        return x < 0.5F ? 16.0F * x * x * x * x * x : 1.0F - (float) Math.pow(-2.0 * x + 2.0, 5.0) / 2.0F;
    }

    // Expo
    public static float easeInExpo(float x) {
        return x == 0.0F ? 0.0F : (float) Math.pow(2.0, 10.0 * x - 10.0);
    }

    public static float easeOutExpo(float x) {
        return x == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * x);
    }

    public static float easeInOutExpo(float x) {
        if (x == 0.0F) return 0.0F;
        if (x == 1.0F) return 1.0F;
        return x < 0.5F
                ? (float) Math.pow(2.0, 20.0 * x - 10.0) / 2.0F
                : (2.0F - (float) Math.pow(2.0, -20.0 * x + 10.0)) / 2.0F;
    }

    // Circ
    public static float easeInCirc(float x) {
        return 1.0F - (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(x, 2.0)));
    }

    public static float easeOutCirc(float x) {
        return (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(x - 1.0, 2.0)));
    }

    public static float easeInOutCirc(float x) {
        return x < 0.5F
                ? (1.0F - (float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(2.0 * x, 2.0)))) / 2.0F
                : ((float) Math.sqrt(Math.max(0.0, 1.0 - Math.pow(-2.0 * x + 2.0, 2.0))) + 1.0F) / 2.0F;
    }

    // Back
    public static float easeInBack(float x) {
        return C3 * x * x * x - C1 * x * x;
    }

    public static float easeOutBack(float x) {
        return 1.0F + C3 * (float) Math.pow(x - 1.0, 3.0) + C1 * (float) Math.pow(x - 1.0, 2.0);
    }

    public static float easeInOutBack(float x) {
        return x < 0.5F
                ? ((float) Math.pow(2.0 * x, 2.0) * ((C2 + 1.0F) * 2.0F * x - C2)) / 2.0F
                : ((float) Math.pow(2.0 * x - 2.0, 2.0) * ((C2 + 1.0F) * (x * 2.0F - 2.0F) + C2) + 2.0F) / 2.0F;
    }

    // Elastic
    public static float easeInElastic(float x) {
        if (x == 0.0F) return 0.0F;
        if (x == 1.0F) return 1.0F;
        return -(float) (Math.pow(2.0, 10.0 * x - 10.0) * Math.sin((x * 10.0F - 10.75F) * C4));
    }

    public static float easeOutElastic(float x) {
        if (x == 0.0F) return 0.0F;
        if (x == 1.0F) return 1.0F;
        return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0F - 0.75F) * C4)) + 1.0F;
    }

    public static float easeInOutElastic(float x) {
        if (x == 0.0F) return 0.0F;
        if (x == 1.0F) return 1.0F;
        return x < 0.5F
                ? -(float) (Math.pow(2.0, 20.0 * x - 10.0) * Math.sin((20.0F * x - 11.125F) * C5)) / 2.0F
                : (float) (Math.pow(2.0, -20.0 * x + 10.0) * Math.sin((20.0F * x - 11.125F) * C5)) / 2.0F + 1.0F;
    }

    // Bounce
    public static float easeInBounce(float x) {
        return 1.0F - easeOutBounce(1.0F - x);
    }

    public static float easeOutBounce(float x) {
        float n1 = 7.5625F;
        float d1 = 2.75F;

        if (x < 1.0F / d1) {
            return n1 * x * x;
        } else if (x < 2.0F / d1) {
            x -= 1.5F / d1;
            return n1 * x * x + 0.75F;
        } else if (x < 2.5F / d1) {
            x -= 2.25F / d1;
            return n1 * x * x + 0.9375F;
        } else {
            x -= 2.625F / d1;
            return n1 * x * x + 0.984375F;
        }
    }

    public static float easeInOutBounce(float x) {
        return x < 0.5F
                ? (1.0F - easeOutBounce(1.0F - 2.0F * x)) / 2.0F
                : (1.0F + easeOutBounce(2.0F * x - 1.0F)) / 2.0F;
    }

    public enum EasingType {
        Linear,
        Dynamic,
        EaseInSine, EaseOutSine, EaseInOutSine,
        EaseInQuad, EaseOutQuad, EaseInOutQuad,
        EaseInCubic, EaseOutCubic, EaseInOutCubic,
        EaseInQuart, EaseOutQuart, EaseInOutQuart,
        EaseInQuint, EaseOutQuint, EaseInOutQuint,
        EaseInExpo, EaseOutExpo, EaseInOutExpo,
        EaseInCirc, EaseOutCirc, EaseInOutCirc,
        EaseInBack, EaseOutBack, EaseInOutBack,
        EaseInElastic, EaseOutElastic, EaseInOutElastic,
        EaseInBounce, EaseOutBounce, EaseInOutBounce;

        public static EasingType fromString(String name) {
            if (name == null) return Dynamic;
            for (EasingType type : values()) {
                if (type.name().equalsIgnoreCase(name)) return type;
            }
            return Dynamic;
        }
    }
}
