package morfeov.coliseum.render.weather;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Camera-relative weather bounds; accepts both regular and reversed/infinite depth. */
public final class WeatherFrustum {
    private final Matrix4f matrix = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();

    public void update(Matrix4fc projection, Matrix4fc view) {
        matrix.set(projection).mul(view);
        // AABB tests don't require normalization. Keeping raw planes also avoids
        // dividing by a zero-length far plane in infinite projection matrices.
        frustum.set(matrix, false);
    }
    public Matrix4f matrix() { return matrix; }

    public boolean particle(float x, float y, float z, float halfSize, float stretch) {
        float radius = halfSize * (1 + stretch) + .01f;
        return box(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    public boolean box(float x0, float y0, float z0, float x1, float y1, float z1) {
        return frustum.testAab(x0, y0, z0, x1, y1, z1);
    }
}
