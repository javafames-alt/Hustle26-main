package im.yaHustler.ui;

import im.yaHustler.render.coliseimvisual.CameraMotionHistory;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class CameraMotionHistoryTest {
    private final CameraMotionHistory history = new CameraMotionHistory();
    private final Matrix4f identity = new Matrix4f();
    private boolean record(long time, double x, float yaw) {
        return history.record(time,identity,identity,x,0,0,yaw,0);
    }
    @Test void interpolatesAtSixtyHzRatherThanOneFrameAgo() {
        assertFalse(record(1_000_000_000L,0,0));
        assertTrue(record(1_010_000_000L,1,10));
        assertTrue(record(1_020_000_000L,2,20));
        assertEquals(.3333334,history.position().x(),1e-6);
        assertEquals(3.333334,history.yaw(),1e-5);
    }
    @Test void interpolatesYawAcrossTheWrapBoundary() {
        record(1_000_000_000L,0,179);
        assertTrue(record(1_033_333_332L,0,-179));
        assertEquals(180,history.yaw(),1e-5);
    }
    @Test void resetsOnTeleportsPausesAndExplicitReset() {
        record(1_000_000_000L,0,0);
        assertFalse(record(1_016_666_666L,9,0));
        assertTrue(record(1_033_333_332L,9,0));
        assertFalse(record(2_000_000_000L,9,0));
        history.reset();
        assertFalse(record(2_016_666_666L,9,0));
    }
    @Test void invalidCameraDataCannotContaminateFollowingFrames() {
        record(1_000_000_000L,0,0);
        assertFalse(record(1_016_666_666L,Double.NaN,0));
        assertFalse(record(1_033_333_332L,0,0));
        assertFalse(record(1_050_000_000L,0,Float.POSITIVE_INFINITY));
        assertFalse(record(1_066_666_666L,0,0));
    }
}
