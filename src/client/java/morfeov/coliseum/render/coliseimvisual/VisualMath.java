package morfeov.coliseum.render.coliseimvisual;

public final class VisualMath {
    private VisualMath() {}

    public static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
