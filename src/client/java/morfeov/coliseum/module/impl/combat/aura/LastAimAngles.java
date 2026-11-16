package morfeov.coliseum.module.impl.combat.aura;

import morfeov.coliseum.rotation.Rotation;
import net.minecraft.util.Mth;

/** One-shot handoff of the last applied aim, scoped to the current player session. */
public final class LastAimAngles {
    private Object owner;
    private Rotation rotation;
    public void remember(Object player, float yaw, float pitch) {
        if (player == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) { clear(); return; }
        owner = player;
        rotation = new Rotation(yaw, Mth.clamp(pitch, -90, 90));
    }
    public Rotation consume(Object player) {
        Rotation result = owner == player ? rotation : null;
        clear();
        return result;
    }
    public void clear() { owner = null; rotation = null; }
}
