package morfeov.coliseum.port;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.combat.aura.AimController;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import morfeov.coliseum.module.impl.combat.aura.sprint.SprintResetHelper;
import morfeov.coliseum.port.event.EventAttackEntity;
import morfeov.coliseum.port.event.EventAttackSubmitted;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.port.event.EventInput;
import morfeov.coliseum.port.event.EventOnMovePost;
import morfeov.coliseum.port.event.EventPacketReceive;
import morfeov.coliseum.port.event.EventPacketSend;
import morfeov.coliseum.port.event.EventPostMotion;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatHooks implements IMinecraft {
    private static LocalPlayer session;
    private static final PortSubscriptions SUBSCRIPTIONS = new PortSubscriptions(Coliseum.MODULES);

    private CombatHooks() {}

    public static void beginTick() {
        var rwHelper = Coliseum.MODULES.get(morfeov.coliseum.module.impl.misc.RWHelper.class);
        if (rwHelper != null) rwHelper.restoreWhileDisabled();
        if (session != mc.player) {
            disconnect();
            session = mc.player;
        }
        AimController.tickObserveSensitivity();
        var pearl = Coliseum.MODULES.get(morfeov.coliseum.module.impl.player.ClickPearl.class);
        if (pearl != null) {
            pearl.restoreWhileDisabled();
        }
    }

    public static void disconnect() {
        PacketBufferManager.INSTANCE.reset();
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.DISCONNECT)) {
            port.onDisconnect(new EventDisconnect());
        }
        TimerSpeed.reset();
        PacketBufferManager.INSTANCE.reset();
        InvActionUtil.get().reset();
        SprintResetHelper.resetConnectionState();
        morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.clear();
        morfeov.coliseum.rotation.Rotations.release();
        session = null;
    }

    public static void input(ClientInput input) {
        var event = new EventInput(input);
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.INPUT)) {
            if (port.enabled()) {
                port.onInput(event);
            }
        }
        if (SprintResetHelper.consumeForwardRelease()) {
            event.setForward(0);
            event.setSprint(false);
            if (mc.player != null) {
                mc.player.setSprinting(false);
            }
        }
        if (morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.isBlocking()) {
            event.setSprint(false);
            if (mc.player != null) mc.player.setSprinting(false);
        }
        event.apply();
    }

    public static void postMotion() {
        morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.synchronizedSprint();
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.POST_MOTION)) {
            if (port.enabled()) {
                port.onPostMotion(new EventPostMotion());
            }
        }
    }

    public static void postMove() {
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.MOVE)) {
            if (port.enabled()) {
                port.onMovePost(new EventOnMovePost());
            }
        }
    }

    public static boolean outgoing(Connection connection, Packet<?> packet) {
        if (mc.isSameThread() && mc.level != null && mc.getConnection() != null && mc.getConnection().getConnection() == connection
            && packet instanceof net.minecraft.network.protocol.game.ServerboundAttackPacket attack
            && morfeov.coliseum.friend.Friends.contains(mc.level.getEntity(attack.entityId()))) {
            morfeov.coliseum.module.impl.combat.rotation.AttackContext.cancelCurrentAttack();
            return true;
        }
        if (!mc.isSameThread() || mc.getConnection() == null || mc.getConnection().getConnection() != connection || PacketBufferManager.sendingSilent()) {
            return false;
        }
        if (packet instanceof ServerboundPlayerCommandPacket command) {
            SprintResetHelper.onCommandPacket(command);
        }
        var event = new EventPacketSend(packet);
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.SEND)) {
            if (port.enabled()) {
                port.onPacketSend(event);
            }
        }
        return event.cancelled() || PacketBufferManager.INSTANCE.capture(connection, packet);
    }

    public static void received(Packet<?> packet) {
        incoming(packet);
    }

    public static boolean incoming(Packet<?> packet) {
        var event = new EventPacketReceive(packet);
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.RECEIVE)) {
            if (port.enabled()) {
                port.onPacket(event);
            }
        }
        return event.cancelled();
    }

    public static void attacked(Entity target) {
        morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().onAttackSubmitted(new EventAttackSubmitted(target));
        morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.attack();
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.ATTACK)) {
            if (port.enabled()) {
                port.onAttack(new EventAttackEntity(target));
                port.onAttackSubmitted(new EventAttackSubmitted(target));
            }
        }
    }

    public static LivingEntity target() {
        var imported = Coliseum.MODULES.get(AttackAura.class);
        if (imported != null && imported.enabled() && imported.getCurrentTarget() != null) {
            return imported.getCurrentTarget();
        }
        return null;
    }
}
