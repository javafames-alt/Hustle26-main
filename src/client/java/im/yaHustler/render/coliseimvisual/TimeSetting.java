package im.yaHustler.render.coliseimvisual;
import im.yaHustler.setting.ModeSetting;
public final class TimeSetting {
    public final ModeSetting setting;
    public TimeSetting(String name, String... values) { setting = new ModeSetting(name, values[0], values); }
    public boolean usesWorldTime() { return setting.is("Обычное"); }
    public long resolveDayTime(long original) {
        return switch (setting.get()) {
                case "Утро" -> 1000; case "День" -> 6000; case "Вечер" -> 10000;
                case "Закат" -> 12000; case "Ночь" -> 21000; default -> Math.floorMod(original, 24000);
        };
    }
    public float dayFraction(long original) { return resolveDayTime(original) / 24000f; }
}
