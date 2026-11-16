package im.yaHustler.port.setting;
public final class SelectSetting extends im.yaHustler.setting.ModeSetting {
    private java.util.function.Consumer<String> listener = value -> {};
    private java.util.function.UnaryOperator<String> normalizer = java.util.function.UnaryOperator.identity();
    public SelectSetting(String name, String value, java.util.List<String> modes) { super(name, value, modes.toArray(String[]::new)); }
    public String getValue() { return get(); }
    public SelectSetting onChange(java.util.function.Consumer<String> next) { listener = next; return this; }
    public SelectSetting normalizeWith(java.util.function.UnaryOperator<String> next) { normalizer = next; return this; }
    @Override public void setSilently(String next) { String old = get(); super.setSilently(normalizer.apply(next)); if (!old.equals(get())) listener.accept(get()); }
}
