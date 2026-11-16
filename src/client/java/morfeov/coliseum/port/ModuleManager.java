package morfeov.coliseum.port;
public final class ModuleManager {
    public static <T extends morfeov.coliseum.module.Module> T getStaticModule(Class<T> type) { return morfeov.coliseum.Coliseum.MODULES.get(type); }
}
