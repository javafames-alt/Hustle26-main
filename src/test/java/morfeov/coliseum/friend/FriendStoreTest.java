package morfeov.coliseum.friend;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FriendStoreTest {
    public static void main(String[] args) throws Exception {
        var test=new FriendStoreTest();
        test.persistsAndMatchesExactNamesIgnoringCase();
        test.failedSaveDoesNotChangeProtection();
        test.parsesTeleportRequestsAndRejectsOutgoing();
        System.out.println("Friend persistence, exact matching, failed-save safety and teleport parsing checks passed.");
    }
    @Test void persistsAndMatchesExactNamesIgnoringCase() throws Exception {
        var file=Files.createTempDirectory("coliseum-friends-test").resolve("friends.json");
        var store=new FriendStore(file);
        assertTrue(store.add("TestNick"));
        assertTrue(store.contains("testnick"));
        assertFalse(store.contains("TestNickOther"));
        assertFalse(store.add("TESTNICK"));
        assertThrows(IllegalArgumentException.class,()->store.add("../../bad"));
        var restored=new FriendStore(file);restored.load();
        assertEquals(java.util.List.of("TestNick"),restored.all());
        assertTrue(restored.remove("tEsTnIcK"));
        restored.add("Second");restored.clear();store.load();assertTrue(store.all().isEmpty());
    }
    @Test void failedSaveDoesNotChangeProtection() throws Exception {
        var parent=Files.createTempFile("friend-blocked",".tmp");
        var store=new FriendStore(parent.resolve("friends.json"));
        assertThrows(java.io.IOException.class,()->store.add("TestNick"));
        assertFalse(store.contains("TestNick"));
    }
    @Test void parsesTeleportRequestsAndRejectsOutgoing() {
        var module=new morfeov.coliseum.module.impl.player.AutoTpaccept();
        assertEquals("TestNick",module.requester("TestNick хочет телепортироваться к вам"));
        assertEquals("TestNick",module.requester("TestNick has requested teleport to you"));
        assertEquals("TestNick",module.requester("§aTestNick§r отправил запрос на телепортацию"));
        assertNull(module.requester("Вы отправили запрос на телепортацию TestNick"));
        assertNull(module.requester("Обычное сообщение TestNick"));
    }
}
