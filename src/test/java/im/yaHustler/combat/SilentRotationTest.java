package im.yaHustler.combat;

import java.lang.reflect.Field;
import im.yaHustler.module.impl.combat.CrystalAura;
import im.yaHustler.port.RotationHandler;
import im.yaHustler.port.SilentComponent;
import im.yaHustler.rotation.Look;
import im.yaHustler.rotation.Rotation;
import im.yaHustler.rotation.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual camera/rotation lifecycle without opening a game window. */
public final class SilentRotationTest {
    private static Minecraft client, previous;
    private static LocalPlayer player;
    private static Field instance;

    @BeforeAll static void installClientFixture() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        instance = Minecraft.class.getDeclaredField("instance"); instance.setAccessible(true);
        previous = (Minecraft) instance.get(null);
        client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        player = (LocalPlayer) unsafe.allocateInstance(LocalPlayer.class);
        var options = (Options) unsafe.allocateInstance(Options.class);
        var sensitivity = (OptionInstance<?>) unsafe.allocateInstance(OptionInstance.class);
        set(OptionInstance.class, sensitivity, "value", .5d);
        set(Options.class, options, "sensitivity", sensitivity);
        set(Minecraft.class, client, "options", options);
        client.player = player;
        instance.set(null, client);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    @AfterAll static void restoreClient() throws Exception {
        if (client != null) SilentComponent.forceClear();
        if (instance != null) instance.set(null, previous);
    }

    @BeforeEach void resetView() {
        SilentComponent.forceClear();
        player.setYRot(40); player.setXRot(12);
        player.yRotO = player.yHeadRot = player.yHeadRotO = player.yBodyRot = player.yBodyRotO = 40;
        player.xRotO = 12;
        Look.track(40, 12);
    }

    @Test void targetingDoesNotMoveTheCameraAndMouseStillChangesTheFreeView() {
        var owner = new Object();
        assertTrue(Rotations.aim(owner, new Rotation(-70, 35), 180, 0, 80));
        assertTrue(Look.active()); assertEquals(40, Look.yaw()); assertEquals(12, Look.pitch());
        assertNotEquals(40, player.getYRot());
        Look.onMouse(20, -10);
        float cameraYaw = Look.yaw(), cameraPitch = Look.pitch();
        assertTrue(Rotations.aim(owner, new Rotation(110, -45), 180, 0, 80));
        assertEquals(cameraYaw, Look.yaw()); assertEquals(cameraPitch, Look.pitch());
    }

    @Test void reclaimsSilentCameraWhenFreeLookWasDisabledDuringAnExistingRequest() {
        var owner = new Object();
        Rotations.aim(owner, new Rotation(-70, 35), 180, 0, 80);
        Look.setActive(false);
        Rotations.release();
        assertTrue(Rotations.active()); assertFalse(Look.active());
        Rotations.aim(owner, new Rotation(110, -45), 180, 0, 80);
        assertTrue(Look.active()); assertEquals(40, Look.yaw()); assertEquals(12, Look.pitch());
    }

    @Test void dataSetStepInterpolatesOnceAndKeepsTheFreeCamera() {
        RotationHandler.apply(20, -4);
        SilentComponent.interpolateDataSetStep(player);
        player.setOldRot();
        player.yHeadRotO = player.yHeadRot;

        SilentComponent.finishDataSetTick(player);
        assertEquals(50, player.getViewYRot(.5f), .001f);
        assertEquals(10, player.getViewXRot(.5f), .001f);
        assertEquals(40, player.yHeadRotO, .001f);
        assertTrue(Look.active());
        assertEquals(40, Look.yaw(), .001f);

        player.setOldRot();
        SilentComponent.finishDataSetTick(player);
        SilentComponent.applyRenderInterpolation();
        assertEquals(60, player.getViewYRot(.5f), .001f);
        assertEquals(8, player.getViewXRot(.5f), .001f);
    }

    @Test void crystalDisableRestoresAllCurrentAndPreviousAnglesOnce() {
        var aura = new CrystalAura();
        aura.setEnabled(true);
        Rotations.aim(aura, new Rotation(-70, 35), 180, 0, 80);
        Look.onMouse(20, -10);
        float yaw = Look.yaw(), pitch = Look.pitch();
        aura.setEnabled(false);
        assertFalse(Rotations.active()); assertFalse(Look.active());
        assertStableAngles(yaw, pitch);
        for (int i = 0; i < 20; i++) {
            Rotations.tick(client); SilentComponent.applyRenderInterpolation();
            assertStableAngles(yaw, pitch);
        }
        // A second disable/re-enable cannot revive the old aim or its interpolation snapshot.
        aura.setEnabled(true); aura.setEnabled(false);
        assertStableAngles(yaw, pitch);
    }

    @Test void releasingAnOldOwnerCannotCancelAnotherModulesRotation() {
        var crystal = new Object(); var pearl = new Object();
        Rotations.aim(crystal, new Rotation(-70, 35), 180, 0, 80);
        Rotations.aim(pearl, new Rotation(90, -20), 180, 0, 100);
        float yaw = player.getYRot(), pitch = player.getXRot();
        SilentComponent.releaseOwnedRotation(crystal);
        assertTrue(Rotations.owns(pearl)); assertTrue(Look.active());
        assertEquals(yaw, player.getYRot()); assertEquals(pitch, player.getXRot());
        assertFalse(Rotations.aim(crystal, new Rotation(20, 0), 180, 0, 80));
        assertTrue(Rotations.owns(pearl));
        SilentComponent.releaseOwnedRotation(pearl);
        assertFalse(Rotations.active()); assertFalse(Look.active());
    }

    private static void assertStableAngles(float yaw, float pitch) {
        assertEquals(yaw, player.getYRot(), .001); assertEquals(pitch, player.getXRot(), .001);
        assertEquals(yaw, player.yRotO, .001); assertEquals(pitch, player.xRotO, .001);
        assertEquals(yaw, player.yHeadRot, .001); assertEquals(yaw, player.yHeadRotO, .001);
        assertEquals(yaw, player.yBodyRot, .001); assertEquals(yaw, player.yBodyRotO, .001);
    }
}
