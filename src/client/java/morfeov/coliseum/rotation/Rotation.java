package morfeov.coliseum.rotation;

import net.minecraft.util.Mth;

public record Rotation(float yaw, float pitch) {
    public double distanceTo(Rotation other) {
        if (other == null) return 0.0;
        double yawDelta = Mth.wrapDegrees(other.yaw - yaw);
        double pitchDelta = Mth.wrapDegrees(other.pitch - pitch);
        return Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
    }
}
