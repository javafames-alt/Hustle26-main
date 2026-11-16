package morfeov.coliseum.render;

import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.render.BlockESP;
import morfeov.coliseum.module.impl.combat.AntiBot;
import morfeov.coliseum.module.impl.misc.ChestStealer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public final class BlockEspPortsCheck {
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var registry=new ModuleRegistry();
        var esp=registry.register(new BlockESP());
        assertTrue(esp.addBlock("diamond_ore"));
        assertFalse(esp.addBlock("minecraft:diamond_ore"));
        assertTrue(esp.addBlock("deepslate_diamond_ore"));
        assertEquals(List.of("minecraft:diamond_ore","minecraft:deepslate_diamond_ore"),esp.blockIds());
        assertThrows(IllegalArgumentException.class,()->esp.addBlock("missing:block"));
        assertThrows(IllegalArgumentException.class,()->esp.addBlock("air"));
        var path=Files.createTempFile("coliseum-blockesp-", ".json");
        try {
            ClientConfig.saveProfile(path,registry);
            esp.clearBlocks();assertTrue(esp.blockIds().isEmpty());
            assertTrue(new ClientConfig(path,registry).load());
            assertEquals(2,esp.blockIds().size());
            assertTrue(esp.removeBlock("diamond_ore"));
            assertFalse(esp.removeBlock("minecraft:diamond_ore"));
            assertEquals(List.of("minecraft:deepslate_diamond_ore"),esp.blockIds());
        } finally { Files.deleteIfExists(path); }
        assertTrue(AntiBot.suspiciousDisplay(null,"Player"));
        assertTrue(AntiBot.suspiciousDisplay("Player","Player"));
        assertFalse(AntiBot.suspiciousDisplay("[VIP] Player","Player"));
        assertTrue(ChestStealer.excludedTitle("Аукцион сервера"));
        assertTrue(ChestStealer.excludedTitle("Мои покупки"));
        assertTrue(ChestStealer.excludedTitle("Auction"));
        assertFalse(ChestStealer.excludedTitle("Большой сундук"));
        assertFalse(new ChestStealer().enabled());assertFalse(new AntiBot().enabled());
        System.out.println("BlockESP identifiers, deduplication, invalid blocks, profile persistence, removal, clear, Zenith AntiBot and ChestStealer menu exclusions verified.");
    }
}
