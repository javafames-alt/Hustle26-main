package morfeov.coliseum.port.setting;
public final class MultiSelectSetting extends morfeov.coliseum.setting.Setting<java.util.Set<String>> {
    private final java.util.List<String> options;
    public MultiSelectSetting(String name, java.util.List<String> options, java.util.Set<String> defaults) { super(name, java.util.Set.copyOf(defaults)); this.options=options; }
    public boolean isSelected(String name) { return get().contains(name); }
    public java.util.List<String> options() { return options; }
    @Override public void setSilently(java.util.Set<String> next) { value=next.stream().filter(options::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
