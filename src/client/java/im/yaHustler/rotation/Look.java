package im.yaHustler.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class Look {
    private static boolean active;
    private static float yaw, pitch;

    private Look() {}

    public static boolean active() { return active; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static void setActive(boolean state) {
        if (active == state) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (state) {
            if (player != null) {
                yaw = player.getYRot();
                pitch = player.getXRot();
            }
            active = true;
        } else {
            active = false;
            if (player != null) {
                player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()));
                player.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
            }
        }
    }

    public static void track(float viewYaw, float viewPitch) {
        yaw = viewYaw;
        pitch = viewPitch;
    }

    public static void onMouse(double deltaYaw, double deltaPitch) {
        yaw += (float) (deltaYaw * 0.15);
        pitch = Mth.clamp(pitch + (float) (deltaPitch * 0.15), -90.0f, 90.0f);
    }
}
