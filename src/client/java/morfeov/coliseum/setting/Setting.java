package morfeov.coliseum.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visible = () -> true;
    private Runnable changed = () -> {};

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }
    public final String name() { return name; }
    public final T get() { return value; }
    public final T defaultValue() { return defaultValue; }
    public final boolean visible() { return visible.getAsBoolean(); }
    private String menuGroup;
    /** Shows this flag inside a multi-select list in the ClickGUI without changing its saved name. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S inMenuGroup(String group) {
        menuGroup = group;
        return (S) this;
    }
    public final String menuGroup() { return menuGroup; }
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        visible = condition;
        return (S) this;
    }
    /** Adds an extra visibility condition on top of the existing one (both must be true). */
    public final Setting<T> alsoVisibleWhen(BooleanSupplier extra) {
        BooleanSupplier previous = visible;
        visible = () -> extra.getAsBoolean() && previous.getAsBoolean();
        return this;
    }
    public final void set(T next) {
        setSilently(next);
        changed.run();
    }
    public void setSilently(T next) { value = next; }
    public final void onChanged(Runnable listener) { changed = listener; }
}
