package morfeov.coliseum.config;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ClientConfigProfileTest {
    @TempDir Path directory;

    @ModuleInfo(id = "profile-test", name = "Profile test", category = Category.RENDER, description = "Test")
    static final class TestModule extends Module {
        final BooleanSetting flag = add(new BooleanSetting("Флаг", true));
        final NumberSetting number = add(new NumberSetting("Число", 2, 0, 10, .5));
        final ModeSetting mode = add(new ModeSetting("Режим", "First", "First", "Second"));
    }

    @Test void namedProfileRestoresModuleStateAndSettings() throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        TestModule module = registry.register(new TestModule());
        module.setEnabled(true); module.setFavorite(true);
        module.setKey('R');
        module.number.set(4.5); module.mode.set("Second");
        Path profile = directory.resolve("профили/Мой конфиг.json");
        ClientConfig.saveProfile(profile, registry);
        module.setEnabled(false); module.setFavorite(false);
        module.setKey('T');
        module.flag.set(false); module.number.set(9.0); module.mode.set("First");
        assertTrue(new ClientConfig(profile, registry).load());
        assertTrue(module.enabled()); assertTrue(module.favorite()); assertTrue(module.flag.enabled());
        assertEquals('R', module.key());
        assertEquals(4.5, module.number.get()); assertEquals("Second", module.mode.get());
        assertFalse(Files.exists(profile.resolveSibling("Мой конфиг.json.tmp")));
    }

    @Test void savingOneProfileDoesNotOverwriteAnother() throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        TestModule module = registry.register(new TestModule());
        Path first = directory.resolve("first.json"), second = directory.resolve("second.json");
        ClientConfig.saveProfile(first, registry);
        module.number.set(8.0);
        ClientConfig.saveProfile(second, registry);
        module.setKey('T');
        assertTrue(new ClientConfig(first, registry).load()); assertEquals(2.0, module.number.get());
        assertEquals(-1, module.key());
        assertTrue(new ClientConfig(second, registry).load()); assertEquals(8.0, module.number.get());
    }
}
