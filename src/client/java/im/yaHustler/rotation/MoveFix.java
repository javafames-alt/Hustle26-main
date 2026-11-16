package im.yaHustler.rotation;

import im.yaHustler.Hustle26;
import im.yaHustler.mixin.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class MoveFix {
    private static float yaw;
    private static int priority = Integer.MAX_VALUE;

    private MoveFix() {}

    public static void request(float moveYaw, int prio) {
        if (prio >= priority) return;
        priority = prio;
        yaw = moveYaw;
    }

    public static boolean isListening() {
        if (priority != Integer.MAX_VALUE || Rotations.active()) return true;
        var imported=Hustle26.MODULES.get(im.yaHustler.module.impl.combat.aura.AttackAura.class);
        return imported!=null && imported.enabled();
    }

    public static void apply(KeyboardInput input) {
        Input keys = input.keyPresses;
        float forward = (keys.forward() ? 1.0f : 0.0f) - (keys.backward() ? 1.0f : 0.0f);
        float strafe = (keys.left() ? 1.0f : 0.0f) - (keys.right() ? 1.0f : 0.0f);
        Rotations.correctInput();
        LocalPlayer player = Minecraft.getInstance().player;
        if (priority != Integer.MAX_VALUE && player != null && (forward != 0.0f || strafe != 0.0f)) {
            double angle = Mth.wrapDegrees(Math.toDegrees(moveAngle(yaw, forward, strafe)));
            float bestForward = 0.0f;
            float bestStrafe = 0.0f;
            float bestDifference = Float.MAX_VALUE;
            for (float f = -1.0f; f <= 1.0f; f += 1.0f) {
                for (float s = -1.0f; s <= 1.0f; s += 1.0f) {
                    if (f == 0.0f && s == 0.0f) continue;
                    double predicted = Mth.wrapDegrees(Math.toDegrees(moveAngle(player.getYRot(), f, s)));
                    float difference = (float) Math.abs(angle - predicted);
                    if (difference < bestDifference) {
                        bestDifference = difference;
                        bestForward = f;
                        bestStrafe = s;
                    }
                }
            }
            forward = bestForward;
            strafe = bestStrafe;
        }
        priority = Integer.MAX_VALUE;
        input.keyPresses = new Input(forward > 0.0f, forward < 0.0f, strafe > 0.0f, strafe < 0.0f,
            keys.jump(), keys.shift(), keys.sprint());
        ((ClientInputAccessor) input).hustle263$setMoveVector(new Vec2(strafe, forward).normalized());
    }

    private static double moveAngle(float rotationYaw, double moveForward, double moveStrafing) {
        float offset;
        float base = moveForward < 0.0 ? rotationYaw + 180.0f : rotationYaw;
        if (moveStrafing > 0.0) {
            float factor = moveForward < 0.0 ? -0.5f : moveForward > 0.0 ? 0.5f : 1.0f;
            offset = -90.0f * factor;
        } else if (moveStrafing < 0.0) {
            float factor = moveForward < 0.0 ? -0.5f : moveForward > 0.0 ? 0.5f : 1.0f;
            offset = 90.0f * factor;
        } else {
            offset = 0.0f;
        }
        return Math.toRadians(base + offset);
    }
}
