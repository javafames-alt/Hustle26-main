package im.yaHustler.command;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.util.ActionMouseButtons;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ClientCommandsTest {
    @ModuleInfo(id = "command-test", name = "CommandTest", category = Category.RENDER, description = "Test")
    static final class TestModule extends Module {}

    public static void main(String[] args) {
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
            .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(ClientCommandsTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 4 || summary.getTotalFailureCount() != 0) throw new AssertionError("Client commands verification failed");
    }

    @Test void parsesKeysUsingMinecraftInputCodes() {
        assertEquals(InputConstants.KEY_A, ClientCommands.parseKey("a"));
        assertEquals(InputConstants.KEY_RSHIFT, ClientCommands.parseKey("RSHIFT"));
        assertEquals(InputConstants.KEY_RETURN, ClientCommands.parseKey("enter"));
        assertEquals(InputConstants.KEY_F5, ClientCommands.parseKey("F5"));
        assertNull(ClientCommands.parseKey("NOT_A_KEY"));
    }

    @Test void mouseBindingsUseActualSdlButtons() {
        assertEquals(-100 - InputConstants.MOUSE_BUTTON_LEFT, ClientCommands.parseKey("mouse1"));
        assertEquals(-100 - InputConstants.MOUSE_BUTTON_RIGHT, ClientCommands.parseKey("mouse2"));
        assertEquals(-100 - InputConstants.MOUSE_BUTTON_MIDDLE, ClientCommands.parseKey("mouse3"));
        assertNull(ClientCommands.parseKey("mouse9"));
        var module = new TestModule();
        module.setKey(ClientCommands.parseKey("mouse2"));
        assertTrue(module.hasKey());
        assertEquals("MOUSE2", module.keyName());
        int button = InputConstants.MOUSE_BUTTON_RIGHT;
        ActionMouseButtons.update(button, true); assertTrue(ActionMouseButtons.down(button));
        ActionMouseButtons.update(button, false); assertFalse(ActionMouseButtons.down(button));
    }

    @Test void mouseBindingsSurviveProfileSaveAndLoad() throws Exception {
        var registry = new ModuleRegistry();
        var module = registry.register(new TestModule());
        var path = Files.createTempFile("hustle263-command-profile", ".json");
        try {
            module.setKey(ClientCommands.parseKey("mouse5"));
            ClientConfig.saveProfile(path, registry);
            module.setKey(-1);
            assertTrue(new ClientConfig(path, registry).load());
            assertEquals(ClientCommands.parseKey("mouse5"), module.key());
            assertTrue(module.hasKey());
        } finally { Files.deleteIfExists(path); }
    }

    @Test void ignoresNormalChatAndUnknownDotCommands() {
        assertFalse(ClientCommands.execute("hello"));
        assertFalse(ClientCommands.execute(".unknown hello"));
        assertFalse(ClientCommands.execute(null));
    }
}
