package im.yaHustler.port;

import java.util.ArrayList;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.port.event.*;

/** Resolve overrides once at registration, rather than calling every empty hook per packet/tick. */
public final class PortSubscriptions {
    public enum Hook {
        INPUT("onInput", EventInput.class), POST_MOTION("onPostMotion", EventPostMotion.class),
        MOVE("onMovePost", EventOnMovePost.class), DISCONNECT("onDisconnect", EventDisconnect.class),
        SEND("onPacketSend", EventPacketSend.class),
        RECEIVE("onPacket", EventPacketReceive.class, "onPacketReceive"),
        ATTACK("onAttack", EventAttackEntity.class, "onAttackSubmitted", EventAttackSubmitted.class);
        final String method, alternate;
        final Class<?> event, alternateEvent;
        Hook(String method, Class<?> event) { this(method, event, null, event); }
        Hook(String method, Class<?> event, String alternate) { this(method, event, alternate, event); }
        Hook(String method, Class<?> event, String alternate, Class<?> alternateEvent) {
            this.method=method; this.event=event; this.alternate=alternate; this.alternateEvent=alternateEvent;
        }
    }
    private static final Hook[] HOOKS = Hook.values();
    private static final ClassValue<boolean[]> OVERRIDES = new ClassValue<>() {
        @Override protected boolean[] computeValue(Class<?> type) {
            boolean[] result = new boolean[HOOKS.length];
            try {
                for (Hook hook : HOOKS) result[hook.ordinal()] =
                    type.getMethod(hook.method, hook.event).getDeclaringClass() != PortModule.class
                    || hook.alternate != null && type.getMethod(hook.alternate, hook.alternateEvent).getDeclaringClass() != PortModule.class;
            } catch (NoSuchMethodException e) { throw new IllegalStateException(e); }
            return result;
        }
    };
    private final ModuleRegistry registry;
    private int size = -1;
    private PortModule[][] handlers;
    public PortSubscriptions(ModuleRegistry registry) { this.registry = registry; }

    /** Borrowed read-only array. Enabled state is intentionally checked at dispatch time. */
    public PortModule[] handlers(Hook hook) {
        if (size != registry.all().size()) rebuild();
        return handlers[hook.ordinal()];
    }
    private void rebuild() {
        PortModule[][] next = new PortModule[HOOKS.length][];
        for (Hook hook : HOOKS) {
            var list = new ArrayList<PortModule>();
            for (var module : registry.all())
                if (module instanceof PortModule port && OVERRIDES.get(port.getClass())[hook.ordinal()]) list.add(port);
            next[hook.ordinal()] = list.toArray(PortModule[]::new);
        }
        handlers = next;
        size = registry.all().size();
    }
}
