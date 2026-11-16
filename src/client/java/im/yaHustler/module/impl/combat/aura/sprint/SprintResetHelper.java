package im.yaHustler.module.impl.combat.aura.sprint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public final class SprintResetHelper {
    private static int testForwardReleaseTicks;
    private static int legitForwardReleaseTicks;
    private static int legitReleaseInputTick = Integer.MIN_VALUE;
    private static boolean resetPending;
    private static boolean serverSprinting;

    private SprintResetHelper() {}

    public static boolean prepareTestAttack(double targetDistance, double attackRange) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return true;
        if (!canResetInAir(mc.player)) {
            cancelReset();
            return true;
        }
        if (resetPending) {
            return !isServerSprinting();
        }
        if (mc.player.isSprinting() && targetDistance <= attackRange) {
            resetPending = true;
            testForwardReleaseTicks = 1;
            return false;
        }
        if (isServerSprinting()) {
            resetPending = true;
            testForwardReleaseTicks = 1;
            return false;
        }
        return true;
    }

    public static boolean canResetInAir(LocalPlayer player) {
        if (player == null || player.level() == null) return false;
        if (player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger() || player.getAbilities().flying) {
            return false;
        }
        return player.fallDistance > 0.0f && player.getDeltaMovement().y < -1.0E-4D
                && !im.yaHustler.module.impl.movement.Sprint.isNearPartialStep(player);
    }

    public static boolean consumeForwardRelease() {
        Minecraft mc = Minecraft.getInstance();
        if (legitForwardReleaseTicks > 0) {
            legitForwardReleaseTicks--;
            legitReleaseInputTick = mc.player != null ? mc.player.tickCount : Integer.MIN_VALUE;
            return true;
        }
        if (testForwardReleaseTicks > 0) {
            testForwardReleaseTicks--;
            return true;
        }
        return false;
    }

    public static boolean isLegitForwardReleasePending() {
        return legitForwardReleaseTicks > 0;
    }

    public static boolean isLegitForwardReleaseActive() {
        Minecraft mc = Minecraft.getInstance();
        return legitForwardReleaseTicks > 0 || (mc.player != null && legitReleaseInputTick == mc.player.tickCount);
    }

    public static void startLegitForwardRelease(int ticks) {
        legitForwardReleaseTicks = Math.max(legitForwardReleaseTicks, Math.max(0, ticks));
    }

    public static boolean isServerSprinting() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return serverSprinting && !mc.player.isFallFlying() && !mc.player.isInWater();
    }

    public static void onCommandPacket(ServerboundPlayerCommandPacket packet) {
        if (packet.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            serverSprinting = true;
        } else if (packet.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
            serverSprinting = false;
        }
    }

    public static boolean isResetPending() {
        return resetPending;
    }

    public static void onPostHitTest(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || mc.options == null) return;
        im.yaHustler.module.impl.movement.Sprint.holdSprint(2);
        mc.options.keySprint.setDown(false);
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    public static void cancelReset() {
        testForwardReleaseTicks = 0;
        legitForwardReleaseTicks = 0;
        legitReleaseInputTick = Integer.MIN_VALUE;
        resetPending = false;
    }

    public static void cancelTestReset() {
        testForwardReleaseTicks = 0;
        resetPending = false;
    }

    public static void cancelLegitForwardRelease() {
        legitForwardReleaseTicks = 0;
        legitReleaseInputTick = Integer.MIN_VALUE;
    }

    public static void resetConnectionState() {
        cancelReset();
        serverSprinting = false;
    }
}
