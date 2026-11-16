package im.yaHustler.combat;

import im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset;
import static org.junit.jupiter.api.Assertions.*;

public final class RwSprintResetCheck {
    public static void main(String[] args) {
        var reset = new RwSprintReset();
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        reset.afterAttack(true);
        assertFalse(reset.serverFlags(false, true, (byte) 0));
        assertFalse(reset.serverFlags(true, false, (byte) 0));
        assertFalse(reset.serverFlags(true, true, (byte) 8));
        assertTrue(reset.serverFlags(true, true, (byte) 0));
        assertTrue(reset.blocking());
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        reset.synchronizedSprint(false);
        assertFalse(reset.blocking());
        reset.afterAttack(true);
        for (int tick = 0; tick < 40; tick++) reset.synchronizedSprint(true);
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        reset.afterAttack(true);
        reset.synchronizedSprint(false);
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        reset.afterAttack(false);
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        reset.afterAttack(true);
        assertTrue(reset.serverFlags(true, true, (byte) 0));
        reset.reset();
        assertFalse(reset.blocking());
        assertFalse(reset.serverFlags(true, true, (byte) 0));
        System.out.println("RW sprint water, server flag, attack window, one-sync blocking and reset checks passed.");
    }
}
