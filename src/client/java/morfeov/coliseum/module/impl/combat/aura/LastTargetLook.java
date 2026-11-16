package morfeov.coliseum.module.impl.combat.aura;

import net.minecraft.world.phys.Vec3;

public final class LastTargetLook {
    private Vec3 point;
    private long until;

    public void remember(Vec3 position) {
        point = position;
    }

    public Vec3 point() {
        return point;
    }

    public boolean begin(long now, long durationNanos) {
        if (point == null || durationNanos <= 0) return false;
        until = now + durationNanos;
        return true;
    }

    public boolean isActive(long now) {
        if (until == 0 || now >= until) {
            until = 0;
            return false;
        }
        return true;
    }

    public void clear() {
        point = null;
        until = 0;
    }
}
