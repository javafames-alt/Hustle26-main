package morfeov.coliseum.hud;

/** Measures the same metric positions used for painting, including both outer gutters. */
public final class InlineHudLayout {
    public record Metric(float divider, float icon, float label, float value, float end) {}
    public record Row(Metric[] metrics, int width) {}
    private InlineHudLayout() {}
    public static Row measure(float[] labelWidths, float[] valueWidths, boolean[] icons) {
        if (labelWidths.length != valueWidths.length || icons.length != valueWidths.length)
            throw new IllegalArgumentException("Metric lengths differ");
        Metric[] metrics = new Metric[valueWidths.length];
        float cursor = 8;
        for (int i = 0; i < metrics.length; i++) {
            float divider = -1, icon = -1;
            if (i > 0) { cursor += 7; divider = cursor; cursor += 6.5f; }
            if (icons[i]) { icon = cursor; cursor += 11; }
            float label = cursor;
            cursor += labelWidths[i] + 4;
            float value = cursor;
            cursor += valueWidths[i];
            metrics[i] = new Metric(divider, icon, label, value, cursor);
        }
        return new Row(metrics, (int)Math.ceil(cursor + 8));
    }
}
