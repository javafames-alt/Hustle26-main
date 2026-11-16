package morfeov.coliseum.module.impl.combat.aura.sprint;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.mixin.LocalPlayerAccessor;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;

public final class RwSprintReset {
    private static final RwSprintReset runtime = new RwSprintReset();
    private int attackTicks;
    private boolean blocking;

    public void afterAttack(boolean sprinting) { if (sprinting) attackTicks = 40; }
    public void synchronizedSprint(boolean sprinting) {
        blocking = false;
        attackTicks = sprinting ? Math.max(0, attackTicks - 1) : 0;
    }
    public boolean serverFlags(boolean inWater, boolean sprinting, byte flags) {
        if (!inWater || !sprinting || attackTicks == 0 || (flags & 8) != 0) return false;
        attackTicks = 0;
        blocking = true;
        return true;
    }
    public boolean blocking() { return blocking; }
    public void reset() { attackTicks = 0; blocking = false; }
    private static boolean enabled() {
        var aura = Coliseum.MODULES.get(AttackAura.class);
        return aura != null && aura.enabled() && aura.isSprintTypeRw();
    }
    private static boolean wasSprinting() {
        var player = Minecraft.getInstance().player;
        return player != null && ((LocalPlayerAccessor) player).coliseum$wasSprinting();
    }
    public static void attack() { if (enabled()) runtime.afterAttack(wasSprinting()); }
    public static void synchronizedSprint() {
        if (enabled()) runtime.synchronizedSprint(wasSprinting());
        else runtime.reset();
    }
    public static void clear() { runtime.reset(); }
    public static boolean isBlocking() { return enabled() && runtime.blocking; }
    public static void received(ClientboundSetEntityDataPacket packet) {
        var player = Minecraft.getInstance().player;
        if (!enabled() || player == null || packet.id() != player.getId()) return;
        int flagsId = morfeov.coliseum.mixin.EntitySharedFlagsAccessor.coliseum$sharedFlags().id();
        for (var value : packet.packedItems()) {
            if (value.id() == flagsId && value.value() instanceof Byte flags && runtime.serverFlags(player.isInWater(), wasSprinting(), flags)) {
                player.setSprinting(false);
                return;
            }
        }
    }
}
