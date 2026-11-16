package morfeov.coliseum.combat;

import morfeov.coliseum.module.impl.movement.ElytraTarget;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

public final class ElytraPredictionCheck {
    public static void main(String[] args) {
        var motion = new Vec3(1, 0, 0);
        var look = new Vec3(1, 0, 0);
        var normal = ElytraTarget.simulateGlide(motion, look, 0, .08, false);
        assertEquals(.991782, normal.x, .000001);
        assertEquals(-.01764, normal.y, .000001);
        var slow = ElytraTarget.simulateGlide(motion, look, 0, .08, true);
        assertTrue(slow.y > normal.y);
        var vertical = ElytraTarget.simulateGlide(new Vec3(0, -1, 0), new Vec3(0, -1, 0), 90, .08, false);
        assertTrue(Double.isFinite(vertical.x) && Double.isFinite(vertical.y) && Double.isFinite(vertical.z));
        var ascending = ElytraTarget.simulateGlide(new Vec3(1, -.1, 0), Vec3.directionFromRotation(-30, -90), -30, .08, false);
        assertTrue(ascending.y > -.1);
        System.out.println("Elytra prediction drag, gravity, slow falling, vertical look and climb checks passed.");
    }
}
