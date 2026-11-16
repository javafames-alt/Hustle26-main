package im.yaHustler.cosmetics.extra;

import org.joml.Vector3f;

public class CosmeticQuad {
    public final CosmeticVertex[] vertices = new CosmeticVertex[4];
    public Vector3f normal = new Vector3f(0.0F, 1.0F, 0.0F);

    public CosmeticQuad(CosmeticVertex v1, CosmeticVertex v2, CosmeticVertex v3, CosmeticVertex v4, Vector3f normal) {
        this.vertices[0] = v1;
        this.vertices[1] = v2;
        this.vertices[2] = v3;
        this.vertices[3] = v4;
        this.normal = normal;
    }
}
