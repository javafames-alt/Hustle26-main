package im.yaHustler.cosmetics.extra;

import org.joml.Vector3f;

public class CosmeticVertex {
    public final Vector3f pos;
    public final float u;
    public final float v;

    public CosmeticVertex(float x, float y, float z, float u, float v) {
        this.pos = new Vector3f(x, y, z);
        this.u = u;
        this.v = v;
    }
}
