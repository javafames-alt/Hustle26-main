package morfeov.coliseum.performance;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Temporary graphics override. Manual changes made afterwards always take precedence on restore. */
public final class OptionOverride<T> {
    private final Supplier<T> read;
    private final Consumer<T> write;
    private T original, applied;
    private boolean active;

    public OptionOverride(Supplier<T> read, Consumer<T> write) { this.read = read; this.write = write; }

    public void update(boolean requested, T value) {
        if (!requested) { restore(); return; }
        if (active) return;
        original = read.get();
        write.accept(value);
        applied = read.get();
        active = true;
    }

    public void restore() {
        if (!active) return;
        if (Objects.equals(read.get(), applied)) write.accept(original);
        active = false;
        original = applied = null;
    }
}
