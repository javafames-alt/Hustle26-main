package morfeov.coliseum.cosmetics.geo;

public class GeoQuad {
    public GeoVertex[] vertices;
    public Vec3F normal;

    public GeoQuad(GeoVertex[] vertices, Vec3F normal) {
        this.vertices = vertices;
        this.normal = normal;
    }

    public GeoQuad(GeoVertex[] vertices, float x, float y, float z) {
        this.vertices = vertices;
        this.normal = new Vec3F(x, y, z);
    }
}
