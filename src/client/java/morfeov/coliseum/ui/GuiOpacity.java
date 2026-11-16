package morfeov.coliseum.ui;

/** Extraction-local opacity. Colors are baked into vertices before the scope is restored. */
public final class GuiOpacity {
    private static final ThreadLocal<Float> OPACITY = ThreadLocal.withInitial(() -> 1f);
    private GuiOpacity() {}
    public static float push(float multiplier) {
        float previous = OPACITY.get();
        OPACITY.set(previous * Math.clamp(multiplier, 0, 1));
        return previous;
    }
    public static void restore(float previous) { OPACITY.set(previous); }
    public static int color(int color) {
        return Math.round((color >>> 24) * OPACITY.get()) << 24 | color & 0xffffff;
    }
}
