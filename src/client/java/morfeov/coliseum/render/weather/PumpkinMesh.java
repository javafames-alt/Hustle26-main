package morfeov.coliseum.render.weather;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Detailed closed jack-o'-lantern: smooth-shaded lobed body with dimpled poles, curved tapered stem,
 * double-sided leaf and a curly vine. Generated once and shared by every pumpkin instance.
 * Vertex layout (POSITION_TEX_COLOR): uv = body (angle, 1-height); color = (glow, shade, material, alpha).
 * Materials: 0 body, .45 stem, .62 vine, .85 leaf (decoded in weather_pumpkin.fsh).
 */
public final class PumpkinMesh {
    public static final int LOBES = 8;
    private static final int SIDES = LOBES * 6, RINGS = 12, COMPONENTS = 9;
    private static final float BODY = 0f, STEM = .45f, VINE = .62f, LEAF = .85f;
    private static final float TOP = .72f - .17f; // body top after the dimple; stem/leaf/vine sit here
    private static final float[] MODEL = build();
    public static final int VERTEX_COUNT = MODEL.length / COMPONENTS;
    // Moonlight from above-front with a soft fill, in model space after rotation.
    private static final float LX = -.32f, LY = .86f, LZ = -.40f;
    private PumpkinMesh() {}

    public static void append(ByteBuffer target, float x, float y, float z, float size, float yaw, float tilt, float alpha, float glow) {
        float cy = (float)Math.cos(yaw), sy = (float)Math.sin(yaw), ct = (float)Math.cos(tilt), st = (float)Math.sin(tilt);
        int a = Math.round(Math.clamp(alpha, 0, 1) * 255), r = Math.round(Math.clamp(glow, 0, 1) * 255);
        float il = 1f / (float)Math.sqrt(LX*LX + LY*LY + LZ*LZ);
        for (int i = 0; i < MODEL.length; i += COMPONENTS) {
            float px = MODEL[i]*cy + MODEL[i+2]*sy, pz = MODEL[i+2]*cy - MODEL[i]*sy, py = MODEL[i+1];
            float nx = MODEL[i+5]*cy + MODEL[i+7]*sy, nz = MODEL[i+7]*cy - MODEL[i+5]*sy, ny = MODEL[i+6];
            float rnx = nx*ct - ny*st, rny = nx*st + ny*ct;
            float diffuse = (rnx*LX + rny*LY + nz*LZ) * il;
            // Wrapped diffuse keeps the night side readable; the fill lifts the underside slightly.
            float light = Math.max(0, (diffuse + .35f) / 1.35f) * .82f + Math.max(0, -rny) * .10f;
            int shade = Math.round(Math.clamp(.22f + .78f * light, 0, 1) * 255);
            target.putFloat(x + (px*ct - py*st)*size).putFloat(y + (px*st + py*ct)*size).putFloat(z + pz*size);
            target.putFloat(MODEL[i+3]).putFloat(MODEL[i+4]);
            target.put((byte)r).put((byte)shade).put((byte)Math.round(MODEL[i+8]*255)).put((byte)a);
        }
    }

    // ---------------------------------------------------------------- geometry
    private static float[] build() {
        var out = new ArrayList<Float>();
        body(out); stem(out); leaf(out); vine(out);
        float[] result = new float[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /** Body surface: theta around (0 = front, -Z), t from bottom (0) to top (1). */
    private static float[] bodyPoint(double theta, double t) {
        double phi = Math.clamp(t, 0, 1) * Math.PI;
        double lobe = Math.pow(Math.abs(Math.cos(theta * LOBES / 2.0)), .55);
        double radius = Math.sin(phi) * 1.04 * (.85 + .15 * lobe) * (1 + .022 * Math.sin(3 * theta + 1.3) * Math.sin(2 * phi));
        double y = -Math.cos(phi) * .72;
        y += .17 * Math.exp(-Math.pow(phi / .42, 2));            // bottom dimple
        y -= .17 * Math.exp(-Math.pow((Math.PI - phi) / .42, 2)); // top dimple around the stem
        return new float[]{(float)(Math.sin(theta) * radius), (float)y, (float)(-Math.cos(theta) * radius)};
    }
    private static float[] bodyNormal(double theta, double t) {
        double e = 1e-3;
        float[] p = bodyPoint(theta, t);
        float[] du = sub(bodyPoint(theta + e, t), bodyPoint(theta - e, t));
        float[] dv = sub(bodyPoint(theta, Math.min(1, t + e)), bodyPoint(theta, Math.max(0, t - e)));
        float[] n = cross(dv, du);
        float len = length(n);
        if (len < 1e-6f) return new float[]{0, t < .5 ? -1 : 1, 0};
        n = scale(n, 1 / len);
        // Orient outward relative to the pumpkin centre.
        if (dot(n, p) < 0) n = scale(n, -1);
        return n;
    }
    private static void body(ArrayList<Float> out) {
        for (int side = 0; side < SIDES; side++) {
            double t0 = 2 * Math.PI * side / SIDES, t1 = 2 * Math.PI * (side + 1) / SIDES;
            float u0 = (float)side / SIDES, u1 = (float)(side + 1) / SIDES;
            for (int ring = 0; ring < RINGS; ring++) {
                double h0 = ease((double)ring / RINGS), h1 = ease((double)(ring + 1) / RINGS);
                float[] a = bodyVertex(t0, h0, u0), b = bodyVertex(t1, h0, u1), c = bodyVertex(t1, h1, u1), d = bodyVertex(t0, h1, u0);
                triangle(out, a, b, c, BODY, false);
                triangle(out, a, c, d, BODY, false);
            }
        }
    }
    /** Concentrates rings near the equator where the face and lobes need resolution. */
    private static double ease(double t) { return .5 - .5 * Math.cos(t * Math.PI) * .25 + (t - .5) * .75; }
    private static float[] bodyVertex(double theta, double t, float u) {
        float[] p = bodyPoint(theta, t), n = bodyNormal(theta, t);
        return new float[]{p[0], p[1], p[2], u, (float)(1 - t), n[0], n[1], n[2]};
    }

    private static float[] stemCenter(float s) {
        return new float[]{.16f * s * s, TOP - .05f + .46f * s, -.03f * s};
    }
    private static void stem(ArrayList<Float> out) {
        int sides = 8, segments = 7;
        for (int seg = 0; seg < segments; seg++) {
            float s0 = (float)seg / segments, s1 = (float)(seg + 1) / segments;
            for (int side = 0; side < sides; side++) {
                float a0 = (float)side / sides, a1 = (float)(side + 1) / sides;
                float[] a = stemVertex(s0, a0), b = stemVertex(s0, a1), c = stemVertex(s1, a1), d = stemVertex(s1, a0);
                triangle(out, a, b, c, STEM, false);
                triangle(out, a, c, d, STEM, false);
            }
        }
        float[] tip = stemCenter(1);
        float[] top = {tip[0], tip[1] + .015f, tip[2], .5f, 1, 0, 1, 0};
        for (int side = 0; side < sides; side++)
            triangle(out, stemVertex(1, (float)side / sides), stemVertex(1, (float)(side + 1) / sides), top, STEM, false);
    }
    private static float stemRadius(float s) { return .065f + .055f * (1 - s) * (1 - s) + .035f * (float)Math.exp(-s * 14); }
    private static float[] stemVertex(float s, float around) {
        float[] c = stemCenter(s);
        double angle = around * Math.PI * 2;
        // Five shallow ridges like a real dried stem.
        float radius = stemRadius(s) * (1 + .12f * (float)Math.cos(angle * 5));
        float nx = (float)Math.sin(angle), nz = -(float)Math.cos(angle);
        return new float[]{c[0] + nx * radius, c[1], c[2] + nz * radius, around, s, nx, .15f, nz};
    }

    private static void leaf(ArrayList<Float> out) {
        int along = 8, across = 4;
        double dir = 2.25;
        float dx = (float)Math.sin(dir), dz = -(float)Math.cos(dir), sx = -dz, sz = dx;
        float[][][] grid = new float[along + 1][across + 1][];
        for (int i = 0; i <= along; i++) {
            float l = (float)i / along;
            float width = .30f * (float)Math.pow(Math.sin(Math.PI * Math.min(.98, l * .92 + .04)), .75) * (1 + .18f * (float)Math.sin(l * 18));
            float reach = .08f + .62f * l;
            float lift = .05f * (float)Math.sin(Math.PI * l) - .16f * l * l;
            for (int j = 0; j <= across; j++) {
                float w = (float)j / across * 2 - 1;
                float cup = .05f * w * w; // slight cupping across the leaf
                float x = dx * reach + sx * w * width, z = dz * reach + sz * w * width;
                grid[i][j] = new float[]{x, TOP + .02f + lift + cup, z, (w + 1) * .5f, l, 0, 1, 0};
            }
        }
        for (int i = 0; i < along; i++) for (int j = 0; j < across; j++) {
            float[] a = grid[i][j], b = grid[i][j + 1], c = grid[i + 1][j + 1], d = grid[i + 1][j];
            triangle(out, a, b, c, LEAF, true); triangle(out, a, c, d, LEAF, true);
        }
    }

    private static float[] vinePoint(float s) {
        double dir = -2.5, a = 1.2 + s * Math.PI * 3.6;
        float baseX = (float)Math.sin(dir) * (.10f + .30f * s), baseZ = -(float)Math.cos(dir) * (.10f + .30f * s);
        float coil = .05f + .10f * s;
        return new float[]{baseX + (float)Math.cos(a) * coil, TOP + .04f + .14f * (float)Math.sin(s * Math.PI) - .05f * s, baseZ + (float)Math.sin(a) * coil};
    }
    private static void vine(ArrayList<Float> out) {
        int segments = 22, sides = 5;
        float[][][] ring = new float[segments + 1][sides][];
        for (int i = 0; i <= segments; i++) {
            float s = (float)i / segments;
            float[] p = vinePoint(s), tangent = norm(sub(vinePoint(Math.min(1, s + .01f)), vinePoint(Math.max(0, s - .01f))));
            float[] side = norm(cross(tangent, new float[]{0, 1, 0}));
            if (length(side) < 1e-4f) side = new float[]{1, 0, 0};
            float[] up = norm(cross(side, tangent));
            float radius = .022f * (1 - .7f * s) + .004f;
            for (int k = 0; k < sides; k++) {
                double angle = Math.PI * 2 * k / sides;
                float c = (float)Math.cos(angle), sn = (float)Math.sin(angle);
                float[] n = {side[0]*c + up[0]*sn, side[1]*c + up[1]*sn, side[2]*c + up[2]*sn};
                ring[i][k] = new float[]{p[0] + n[0]*radius, p[1] + n[1]*radius, p[2] + n[2]*radius, (float)k / sides, s, n[0], n[1], n[2]};
            }
        }
        for (int i = 0; i < segments; i++) for (int k = 0; k < sides; k++) {
            int k1 = (k + 1) % sides;
            triangle(out, ring[i][k], ring[i][k1], ring[i + 1][k1], VINE, false);
            triangle(out, ring[i][k], ring[i + 1][k1], ring[i + 1][k], VINE, false);
        }
    }

    // ---------------------------------------------------------------- helpers
    /**
     * Emits a triangle whose winding faces along the averaged vertex normals (the pipeline culls back faces).
     * Double-sided surfaces are emitted a second time with reversed winding and flipped normals.
     */
    private static void triangle(ArrayList<Float> out, float[] a, float[] b, float[] c, float material, boolean doubleSided) {
        float[] hint = {a[5] + b[5] + c[5], a[6] + b[6] + c[6], a[7] + b[7] + c[7]};
        float[] g = cross(sub3(b, a), sub3(c, a));
        if (dot(g, hint) < 0) { float[] t = b; b = c; c = t; }
        emit(out, a, material, 1); emit(out, b, material, 1); emit(out, c, material, 1);
        if (doubleSided) { emit(out, a, material, -1); emit(out, c, material, -1); emit(out, b, material, -1); }
    }
    private static void emit(ArrayList<Float> out, float[] v, float material, float flip) {
        out.add(v[0]); out.add(v[1]); out.add(v[2]); out.add(v[3]); out.add(v[4]);
        out.add(v[5] * flip); out.add(v[6] * flip); out.add(v[7] * flip); out.add(material);
    }
    private static float[] sub(float[] a, float[] b) { return new float[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; }
    private static float[] sub3(float[] a, float[] b) { return new float[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; }
    private static float[] cross(float[] a, float[] b) { return new float[]{a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]}; }
    private static float dot(float[] a, float[] b) { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]; }
    private static float length(float[] a) { return (float)Math.sqrt(dot(a, a)); }
    private static float[] scale(float[] a, float s) { return new float[]{a[0]*s, a[1]*s, a[2]*s}; }
    private static float[] norm(float[] a) { float l = length(a); return l < 1e-6f ? new float[]{0, 0, 0} : scale(a, 1 / l); }
}
