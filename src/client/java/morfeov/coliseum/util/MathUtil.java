package morfeov.coliseum.util;

import java.util.concurrent.ThreadLocalRandom;

public final class MathUtil {
    private MathUtil() {}

    public static float random(float min, float max) {
        return (float) (ThreadLocalRandom.current().nextDouble() * (max - min) + min);
    }
}
