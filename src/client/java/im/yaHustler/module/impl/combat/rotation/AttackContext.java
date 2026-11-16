package im.yaHustler.module.impl.combat.rotation;

public final class AttackContext {
    private static final ThreadLocal<State> current = new ThreadLocal<>();

    private AttackContext() {}

    public static boolean run(boolean criticalAttack, boolean maceAttack, Runnable action) {
        State previous = current.get();
        State state = new State(criticalAttack, maceAttack);
        current.set(state);
        try {
            action.run();
            return !state.canceled;
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }

    public static void cancelCurrentAttack() {
        State state = current.get();
        if (state != null) {
            state.canceled = true;
        }
    }

    public static boolean isCriticalAttack() {
        State state = current.get();
        return state != null && state.criticalAttack;
    }

    public static boolean isMaceAttack() {
        State state = current.get();
        return state != null && state.maceAttack;
    }

    public static boolean isAuraAttack() {
        return current.get() != null;
    }

    private static final class State {
        private final boolean criticalAttack;
        private final boolean maceAttack;
        private boolean canceled;

        private State(boolean criticalAttack, boolean maceAttack) {
            this.criticalAttack = criticalAttack;
            this.maceAttack = maceAttack;
        }
    }
}
