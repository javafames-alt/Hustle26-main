package im.yaHustler.module.impl.combat.rotation;

public class CooldownTimer {
    private long startTime = System.currentTimeMillis();

    public boolean finished(long delay) {
        return System.currentTimeMillis() - delay >= startTime;
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }
}
