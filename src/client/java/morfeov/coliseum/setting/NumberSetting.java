package morfeov.coliseum.setting;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }
    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public float floatValue() { return value.floatValue(); }
    public int intValue() { return (int) Math.round(value); }
    public double progress() { return (value - min) / (max - min); }
    public void setProgress(double t, boolean notify) {
        double v = min + Math.clamp(t, 0, 1) * (max - min);
        if (notify) set(v); else setSilently(v);
    }
    @Override
    public void setSilently(Double next) {
        double v = Math.clamp(next, min, max);
        if (step > 0) v = min + Math.round((v - min) / step) * step;
        value = Math.clamp(Math.round(v * 1e6) / 1e6, min, max);
    }
    public String display() {
        if (step >= 1) return String.valueOf(Math.round(value));
        int digits = step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
        return String.format(java.util.Locale.ROOT, "%." + digits + "f", value);
    }
}
