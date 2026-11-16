package morfeov.coliseum.module.impl.combat.aura;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AimPart {
    public final float deltaYaw;
    public final float deltaPitch;
    public final float distance;
    public final boolean aimingAtTarget;
    public final boolean airborne;
    public final LivingEntity target;
    public final Vec3 aimPoint;
    public final float attackRange;
    public final boolean moving;
    public final String aimPart;

    public AimPart(float deltaYaw, float deltaPitch, float distance) {
        this(deltaYaw, deltaPitch, distance, false, false);
    }

    public AimPart(float deltaYaw, float deltaPitch, float distance, boolean aimingAtTarget, boolean airborne) {
        this(deltaYaw, deltaPitch, distance, aimingAtTarget, airborne, null, null, distance, false, "Грудь");
    }

    public AimPart(
            float deltaYaw,
            float deltaPitch,
            float distance,
            boolean aimingAtTarget,
            boolean airborne,
            LivingEntity target,
            Vec3 aimPoint,
            float attackRange,
            boolean moving
    ) {
        this(deltaYaw, deltaPitch, distance, aimingAtTarget, airborne, target, aimPoint, attackRange, moving, "Грудь");
    }

    public AimPart(
            float deltaYaw,
            float deltaPitch,
            float distance,
            boolean aimingAtTarget,
            boolean airborne,
            LivingEntity target,
            Vec3 aimPoint,
            float attackRange,
            boolean moving,
            String aimPart
    ) {
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
        this.distance = distance;
        this.aimingAtTarget = aimingAtTarget;
        this.airborne = airborne;
        this.target = target;
        this.aimPoint = aimPoint;
        this.attackRange = attackRange;
        this.moving = moving;
        this.aimPart = aimPart != null ? aimPart : "Грудь";
    }
}
