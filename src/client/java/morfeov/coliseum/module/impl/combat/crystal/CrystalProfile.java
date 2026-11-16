package morfeov.coliseum.module.impl.combat.crystal;

/** Named adaptation of Zenith utility.misc.Data3; values match the donor presets. */
public record CrystalProfile(boolean postMotion, float placeRange, float breakRange, int actions,
        int placeDelay, int breakDelay, float rotationSpeed, Swap swap, boolean instantBreak,
        boolean instantPlace, boolean varyRotation, boolean throughWalls, boolean strictReach) {
    public enum Swap { KEEP, SILENT, OFF }

    public static CrystalProfile preset(String name) {
        return switch (name) {
            case "DonutSMP" -> new CrystalProfile(false, 4.5f, 3, 1, 50, 50, 35, Swap.KEEP, false, false, true, false, true);
            case "Без ограничений" -> new CrystalProfile(true, 5.5f, 5, 3, 0, 0, 180, Swap.SILENT, true, true, false, true, false);
            default -> new CrystalProfile(false, 4.5f, 3, 1, 0, 0, 180, Swap.KEEP, false, false, true, false, true);
        };
    }

    public static boolean delayReady(int now, int last, int milliseconds) {
        return last == Integer.MIN_VALUE || milliseconds <= 0 || now - last >= Math.max(1, Math.round(milliseconds / 50f));
    }

    public static float minimumDamage(float configured, float health, float faceplace, boolean armorBroken) {
        return health <= faceplace || armorBroken ? Math.min(configured, 2) : configured;
    }

    public static boolean safe(float self, float health, float maximum, boolean antiSuicide, boolean lethalWithoutTotem) {
        return Float.isFinite(self) && self >= 0 && (!antiSuicide || self < Math.max(0, health - 2))
                && (self <= maximum || lethalWithoutTotem);
    }

    public static float score(float damage, float self, float friendDamage, float health, float selfHealth,
                              boolean lethal, boolean hasTotem, int ticksAfterPop, boolean antiTotem) {
        float value = damage + (lethal ? hasTotem ? 6 : 15 : 0);
        if (antiTotem && hasTotem) {
            if (lethal) value += -Math.min(4, Math.max(0, damage - health) * .5f) + (ticksAfterPop <= 10 ? 4 : 0);
            else if (health - damage > 0 && health - damage < 2) value += 2.5f;
        }
        return value - self * 1.2f * Math.clamp(16f / Math.max(1, selfHealth), 1, 3) - friendDamage;
    }
}
