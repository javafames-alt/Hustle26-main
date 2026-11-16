package im.yaHustler.module.impl.combat.aura.sprint;

public final class SpookySprintReset {
    private SpookySprintReset() {}

    public static boolean shouldRelease(boolean hasTarget, boolean onGround, boolean underwater,
                                        double distance, double range, double verticalSpeed,
                                        boolean itemCooling, boolean maceSmash, boolean mace,
                                        double fallDistance, float predictedCharge, int ticksSinceAttack) {
        if (!hasTarget || onGround || underwater || distance >= range || verticalSpeed > .16477328182606651) return false;
        if (itemCooling && (maceSmash || fallDistance > 1.5 || mace)) return false;
        if (maceSmash) return true;
        if (fallDistance > 1.5) return ticksSinceAttack > 0;
        return predictedCharge >= .9f && (mace || ticksSinceAttack >= 7);
    }
}
