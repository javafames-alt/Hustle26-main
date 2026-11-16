package morfeov.coliseum.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class ModuleRegistry {
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> tickers = new ArrayList<>();
    private static final ClassValue<Boolean> HAS_TICK = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try { return type.getMethod("tick", Minecraft.class).getDeclaringClass() != Module.class; }
            catch (NoSuchMethodException e) { throw new IllegalStateException(e); }
        }
    };
    private final List<Module> view = java.util.Collections.unmodifiableList(modules);
    private final java.util.concurrent.ConcurrentMap<Class<?>, java.util.Optional<Module>> byType = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private Runnable changed = () -> {};
    private final java.util.Set<Module> hidden = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public <T extends Module> T register(T module) {
        if (modules.stream().anyMatch(m -> m.id().equals(module.id())))
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        modules.add(module);
        if (HAS_TICK.get(module.getClass())) tickers.add(module);
        byType.clear();
        revision.incrementAndGet();
        module.onChanged(() -> { revision.incrementAndGet(); changed.run(); });
        return module;
    }
    public List<Module> all() { return view; }
    /** Hides a module from ClickGUI lists and search; it stays registered and keeps working. */
    public void hide(Module module) { hidden.add(module); revision.incrementAndGet(); }
    public boolean isHidden(Module module) { return hidden.contains(module); }
    public void unregister(Module module) {
        if (!modules.contains(module)) return;
        module.setEnabled(false);
        modules.remove(module);
        tickers.remove(module);
        module.onChanged(() -> {});
        byType.clear();
        revision.incrementAndGet();
        changed.run();
    }
    public <T extends Module> T get(Class<T> type) {
        var cached = byType.get(type);
        if (cached != null) return type.cast(cached.orElse(null));
        return type.cast(byType.computeIfAbsent(type, requested -> {
            for (Module module : modules) if (requested.isInstance(module)) return java.util.Optional.of(module);
            return java.util.Optional.empty();
        }).orElse(null));
    }
    public long revision() { return revision.get(); }
    public void onChanged(Runnable listener) { changed = listener; }
    public void handleKeys(Minecraft client) {
        boolean active = client.gui.screen() == null && client.player != null;
        for (Module module : modules) {
            boolean down = module.key() <= -100 ? morfeov.coliseum.util.ActionMouseButtons.down(-100 - module.key())
                : module.hasKey() && com.mojang.blaze3d.platform.InputConstants.isKeyDown(module.key());
            boolean was = module.keyHeld;
            module.keyHeld = down;
            if (active && down && !was) module.toggle();
        }
    }
    public void tick(Minecraft client) {
        for (Module module : tickers) if (module.enabled()) module.tick(client);
    }
    public List<Module> search(String query, boolean favoritesOnly) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Category category : Category.values())
            for (Module module : modules)
                if (module.category() == category && category != Category.BOTS && !hidden.contains(module)
                    && (!favoritesOnly || module.favorite()) && module.matches(needle))
                    result.add(module);
        return result;
    }
    public List<Module> visible(Category category, String query, boolean favoritesOnly) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.category() != category || hidden.contains(module) || favoritesOnly && !module.favorite()) continue;
            if (module.matches(needle)) result.add(module);
        }
        return result;
    }
}
