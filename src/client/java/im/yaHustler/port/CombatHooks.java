package im.yaHustler.port;

import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.combat.aura.AimController;
import im.yaHustler.module.impl.combat.aura.AttackAura;
import im.yaHustler.module.impl.combat.aura.sprint.SprintResetHelper;
import im.yaHustler.port.event.EventAttackEntity;
import im.yaHustler.port.event.EventAttackSubmitted;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.port.event.EventInput;
import im.yaHustler.port.event.EventOnMovePost;
import im.yaHustler.port.event.EventPacketReceive;
import im.yaHustler.port.event.EventPacketSend;
import im.yaHustler.port.event.EventPostMotion;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatHooks implements IMinecraft {
    private static LocalPlayer session;
    private static final PortSubscriptions SUBSCRIPTIONS = new PortSubscriptions(Hustle26.MODULES);

    private CombatHooks() {}

    public static void beginTick() {
        var mace = Hustle26.MODULES.get(im.yaHustler.module.impl.combat.AutoMace.class);
        if (mace != null && !mace.enabled()) mace.restoreAttackSwap(mc, true);
        var rwHelper = Hustle26.MODULES.get(im.yaHustler.module.impl.misc.RWHelper.class);
        if (rwHelper != null) rwHelper.restoreWhileDisabled();
        if (session != mc.player) {
            disconnect();
            session = mc.player;
        }
        AimController.tickObserveSensitivity();
        var pearl = Hustle26.MODULES.get(im.yaHustler.module.impl.player.ClickPearl.class);
        if (pearl != null) {
            pearl.restoreWhileDisabled();
        }
    }

    public static void disconnect() {
        im.yaHustler.module.impl.combat.rotation.CombatTagManager.reset();
        PacketBufferManager.INSTANCE.reset();
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.DISCONNECT)) {
            port.onDisconnect(new EventDisconnect());
        }
        TimerSpeed.reset();
        PacketBufferManager.INSTANCE.reset();
        InvActionUtil.get().reset();
        SprintResetHelper.resetConnectionState();
        im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.clear();
        im.yaHustler.rotation.Rotations.release();
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
        if (im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.isBlocking()) {
            event.setSprint(false);
            if (mc.player != null) mc.player.setSprinting(false);
        }
        event.apply();
    }

    public static void postMotion() {
        im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.synchronizedSprint();
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
            && im.yaHustler.friend.Friends.contains(mc.level.getEntity(attack.entityId()))) {
            im.yaHustler.module.impl.combat.rotation.AttackContext.cancelCurrentAttack();
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
        if (target instanceof net.minecraft.world.entity.player.Player)
            im.yaHustler.module.impl.combat.rotation.CombatTagManager.onAttack(target);
        im.yaHustler.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().onAttackSubmitted(new EventAttackSubmitted(target));
        im.yaHustler.module.impl.combat.aura.sprint.RwSprintReset.attack();
        for (var port : SUBSCRIPTIONS.handlers(PortSubscriptions.Hook.ATTACK)) {
            if (port.enabled()) {
                port.onAttack(new EventAttackEntity(target));
                port.onAttackSubmitted(new EventAttackSubmitted(target));
            }
        }
    }

    public static LivingEntity target() {
        var imported = Hustle26.MODULES.get(AttackAura.class);
        if (imported != null && imported.enabled() && imported.getCurrentTarget() != null) {
            return imported.getCurrentTarget();
        }
        return null;
    }
}
