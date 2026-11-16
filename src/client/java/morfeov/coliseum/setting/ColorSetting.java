package morfeov.coliseum.setting;

import morfeov.coliseum.util.ColorUtil;

public final class ColorSetting extends Setting<Integer> {
    private float hue, saturation, brightness;

    public ColorSetting(String name, int defaultValue) {
        super(name, defaultValue);
        sync(defaultValue);
    }
    public int color() { return value; }
    public float hue() { return hue; }
    public float saturation() { return saturation; }
    public float brightness() { return brightness; }
    public void setHsv(float hue, float saturation, float brightness, boolean notify) {
        this.hue = Math.clamp(hue, 0f, .999f);
        this.saturation = Math.clamp(saturation, 0f, 1f);
        this.brightness = Math.clamp(brightness, 0f, 1f);
        int next = ColorUtil.fromHsv(this.hue, this.saturation, this.brightness, value >>> 24);
        if (notify) set(next); else value = next;
    }
    @Override
    public void setSilently(Integer next) {
        value = next;
        sync(next);
    }
    private void sync(int color) {
        float[] hsv = ColorUtil.toHsv(color);
        if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
        if (hsv[2] > 0) saturation = hsv[1];
        brightness = hsv[2];
    }
}
