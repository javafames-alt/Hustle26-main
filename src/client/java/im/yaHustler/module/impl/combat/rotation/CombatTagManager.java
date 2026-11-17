package im.yaHustler.module.impl.combat.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CombatTagManager {
    private static long lastCombatNanos = 0;
    private static final float combatDurationSec = 15.0f;
    private static String lastTargetName = "";

    private CombatTagManager() {}

    public static void onAttack(Entity target) {
        lastCombatNanos = System.nanoTime();
        if (target != null) {
            lastTargetName = target.getName().getString();
        }
    }

    public static void onDamageReceived() {
        lastCombatNanos = System.nanoTime();
    }

    public static void reset() {
        lastCombatNanos = 0;
        lastTargetName = "";
    }

    public static boolean isInCombat() {
        if (lastCombatNanos <= 0) return false;
        long elapsed = System.nanoTime() - lastCombatNanos;
        return elapsed < (long) (combatDurationSec * 1_000_000_000L);
    }

    public static float getRemainingSeconds() {
        if (!isInCombat()) return 0.0f;
        long elapsed = System.nanoTime() - lastCombatNanos;
        float remain = combatDurationSec - (elapsed / 1_000_000_000.0f);
        return Math.max(0.0f, remain);
    }

    public static float getProgress() {
        if (!isInCombat()) return 0.0f;
        return Math.clamp(getRemainingSeconds() / combatDurationSec, 0.0f, 1.0f);
    }

    public static String getLastTargetName() {
        return lastTargetName != null ? lastTargetName : "";
    }

    public static float getItemCooldownRemaining(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || item == null) return 0.0f;
        ItemStack stack = item.getDefaultInstance();
        if (!mc.player.getCooldowns().isOnCooldown(stack)) return 0.0f;
        return mc.player.getCooldowns().getCooldownPercent(stack, 0.0f);
    }
}
