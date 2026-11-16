package im.yaHustler.render.coliseimvisual;
import org.joml.Matrix4f;
/** View/projection snapshots supplied by the 26.3 world render hook. */
public final class WorldRenderMatrices {
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    public static void captureMatrices(Matrix4f view, Matrix4f projection) { VIEW.set(view); PROJECTION.set(projection); }
    public static Matrix4f viewMatrix() { return new Matrix4f(VIEW); }
    public static Matrix4f projectionMatrix() { return new Matrix4f(PROJECTION); }
}
