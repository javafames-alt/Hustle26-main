package morfeov.coliseum.bot.world;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public final class BotPlayerState {
    public record Vitals(float health, int food, float saturation, float experienceProgress, int experienceLevel, int totalExperience) {}
    public record Abilities(boolean invulnerable, boolean flying, boolean canFly, boolean instabuild, float flyingSpeed, float walkingSpeed) {}
    private PositionMoveRotation movement = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0, 0);
    private boolean positioned;
    private Vitals vitals = new Vitals(20, 20, 5, 0, 0, 0);
    private Abilities abilities = new Abilities(false, false, false, false, 0.05f, 0.1f);

    public synchronized PositionMoveRotation snapshot() { return movement; }
    public synchronized boolean positioned() { return positioned; }
    public synchronized Vitals vitals() { return vitals; }
    public synchronized Abilities abilities() { return abilities; }
    public synchronized void movement(PositionMoveRotation value) { movement = value; }
    public synchronized void look(float yaw, float pitch) {
        movement = new PositionMoveRotation(movement.position(), movement.deltaMovement(), yaw, Math.clamp(pitch, -90, 90));
    }
    public synchronized void apply(ClientboundSetHealthPacket packet) {
        vitals = new Vitals(packet.getHealth(), packet.getFood(), packet.getSaturation(), vitals.experienceProgress(), vitals.experienceLevel(), vitals.totalExperience());
    }
    public synchronized void apply(ClientboundSetExperiencePacket packet) {
        vitals = new Vitals(vitals.health(), vitals.food(), vitals.saturation(), packet.getExperienceProgress(), packet.getExperienceLevel(), packet.getTotalExperience());
    }
    public synchronized void apply(ClientboundPlayerAbilitiesPacket packet) {
        abilities = new Abilities(packet.isInvulnerable(), packet.isFlying(), packet.canFly(), packet.canInstabuild(), packet.getFlyingSpeed(), packet.getWalkingSpeed());
    }

    public synchronized void teleport(ClientboundPlayerPositionPacket packet, Connection connection) {
        PositionMoveRotation next = PositionMoveRotation.calculateAbsolute(movement, packet.change(), packet.relatives());
        Vec3 position = next.position(), velocity = next.deltaMovement();
        if (!finite(position) || !finite(velocity) || !Float.isFinite(next.yRot()) || !Float.isFinite(next.xRot())) {
            connection.disconnect(net.minecraft.network.chat.Component.literal("Сервер прислал некорректную позицию бота"));
            return;
        }
        movement = next;
        connection.send(new ServerboundAcceptTeleportationPacket(packet.id(), position.x, position.y, position.z, next.yRot(), next.xRot()));
        if (!positioned) {
            connection.send(new ServerboundPlayerLoadedPacket());
            positioned = true;
        }
    }

    public synchronized void reset() {
        movement = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0, 0);
        positioned = false;
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
}
