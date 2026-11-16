package im.yaHustler.port;
public final class ModuleManager {
    public static <T extends im.yaHustler.module.Module> T getStaticModule(Class<T> type) { return im.yaHustler.Hustle26.MODULES.get(type); }
}
