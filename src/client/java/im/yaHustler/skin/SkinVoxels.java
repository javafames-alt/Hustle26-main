package im.yaHustler.skin;

import im.yaHustler.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

public final class SkinVoxels {
    public static final int HAT = 0, JACKET = 1, LEFT_SLEEVE = 2, RIGHT_SLEEVE = 3, LEFT_PANTS = 4, RIGHT_PANTS = 5, COUNT = 6;
    private static final String[] NAMES = {"hat", "jacket", "left_sleeve", "right_sleeve", "left_pants", "right_pants"};
    private static final int MIN_ALPHA = 16;
    private static Map<Direction, Vector3fc> faceNormals;

    private final ModelPart[] parts = new ModelPart[COUNT];
    private final ModelPart[][] paths = new ModelPart[COUNT][];

    private SkinVoxels() {}

    public ModelPart part(int index) { return parts[index]; }
    public ModelPart[] path(int index) { return paths[index]; }

    static SkinVoxels build(SkinEntry entry, PlayerModel model) {
        SkinVoxels result = new SkinVoxels();
        ModelPart root = model.root();
        ModelPart[] parents = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        for (int i = 0; i < COUNT; i++) {
            ModelPart[] path = find(root, parents, NAMES[i]);
            if (path == null) continue;
            ModelPart overlay = path[path.length - 1];
            List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) overlay).hustle263$cubes();
            if (cubes.isEmpty()) continue;
            List<ModelPart.Cube> voxels = new ArrayList<>();
            for (ModelPart.Cube cube : cubes) voxelize(entry, cube, voxels);
            if (voxels.isEmpty()) continue;
            result.parts[i] = new ModelPart(voxels, Map.of());
            result.paths[i] = path;
        }
        return result;
    }

    private static ModelPart[] find(ModelPart root, ModelPart[] parents, String name) {
        if (root.hasChild(name)) return new ModelPart[]{root, root.getChild(name)};
        for (ModelPart parent : parents)
            if (parent.hasChild(name)) return new ModelPart[]{root, parent, parent.getChild(name)};
        return null;
    }

    private static void voxelize(SkinEntry entry, ModelPart.Cube cube, List<ModelPart.Cube> out) {
        int size = entry.size();
        int[] pixels = entry.pixels();
        float voxel = 64f / size;
        float[] baseMin = {cube.minX, cube.minY, cube.minZ}, baseMax = {cube.maxX, cube.maxY, cube.maxZ};
        for (ModelPart.Polygon polygon : cube.polygons) {
            ModelPart.Vertex[] v = polygon.vertices();
            Vector3fc normal = polygon.normal();
            int axis = Math.abs(normal.x()) > .5f ? 0 : Math.abs(normal.y()) > .5f ? 1 : 2;
            float sign = Math.signum(axis == 0 ? normal.x() : axis == 1 ? normal.y() : normal.z());
            ModelPart.Vertex a = v[0], b = null, c = null;
            for (int k = 1; k < v.length; k++) {
                if (b == null && Math.abs(v[k].v() - a.v()) < 1e-6f && Math.abs(v[k].u() - a.u()) > 1e-6f) b = v[k];
                if (c == null && Math.abs(v[k].u() - a.u()) < 1e-6f && Math.abs(v[k].v() - a.v()) > 1e-6f) c = v[k];
            }
            if (b == null || c == null) continue;
            float[] gMin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] gMax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            float uMin = Float.MAX_VALUE, uMax = -Float.MAX_VALUE, vMin = Float.MAX_VALUE, vMax = -Float.MAX_VALUE;
            for (ModelPart.Vertex vertex : v) {
                float[] p = {vertex.x(), vertex.y(), vertex.z()};
                for (int k = 0; k < 3; k++) { gMin[k] = Math.min(gMin[k], p[k]); gMax[k] = Math.max(gMax[k], p[k]); }
                uMin = Math.min(uMin, vertex.u()); uMax = Math.max(uMax, vertex.u());
                vMin = Math.min(vMin, vertex.v()); vMax = Math.max(vMax, vertex.v());
            }
            int px0 = Math.round(uMin * size), px1 = Math.round(uMax * size);
            int py0 = Math.round(vMin * size), py1 = Math.round(vMax * size);
            Set<Direction> faces = EnumSet.allOf(Direction.class);
            Direction inward = inward(normal);
            if (inward != null) faces.remove(inward);
            for (int py = Math.max(0, py0); py < Math.min(size, py1); py++) {
                for (int px = Math.max(0, px0); px < Math.min(size, px1); px++) {
                    if ((pixels[py * size + px] >>> 24) < MIN_ALPHA) continue;
                    float uc = (px + .5f) / size, vc = (py + .5f) / size;
                    float tu = (uc - a.u()) / (b.u() - a.u()), tv = (vc - a.v()) / (c.v() - a.v());
                    float[] p = {
                        a.x() + tu * (b.x() - a.x()) + tv * (c.x() - a.x()),
                        a.y() + tu * (b.y() - a.y()) + tv * (c.y() - a.y()),
                        a.z() + tu * (b.z() - a.z()) + tv * (c.z() - a.z())
                    };
                    for (int k = 0; k < 3; k++) {
                        if (k == axis) p[k] = sign > 0 ? baseMax[k] : baseMin[k];
                        else if (gMax[k] - gMin[k] > 1e-6f)
                            p[k] = baseMin[k] + (p[k] - gMin[k]) / (gMax[k] - gMin[k]) * (baseMax[k] - baseMin[k]);
                    }
                    p[axis] += sign * voxel / 2f;
                    ModelPart.Cube box = new ModelPart.Cube(0, 0, p[0] - voxel / 2f, p[1] - voxel / 2f, p[2] - voxel / 2f,
                        voxel, voxel, voxel, 0, 0, 0, false, size, size, faces);
                    for (int k = 0; k < box.polygons.length; k++) {
                        ModelPart.Polygon face = box.polygons[k];
                        ModelPart.Vertex[] fv = face.vertices();
                        ModelPart.Vertex[] remapped = new ModelPart.Vertex[fv.length];
                        for (int n = 0; n < fv.length; n++) remapped[n] = fv[n].remap(uc, vc);
                        box.polygons[k] = new ModelPart.Polygon(remapped, face.normal());
                    }
                    out.add(box);
                }
            }
        }
    }

    private static Direction inward(Vector3fc normal) {
        if (faceNormals == null) {
            Map<Direction, Vector3fc> map = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                ModelPart.Cube probe = new ModelPart.Cube(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, false, 64, 64, EnumSet.of(direction));
                if (probe.polygons.length > 0) map.put(direction, probe.polygons[0].normal());
            }
            faceNormals = map;
        }
        for (Map.Entry<Direction, Vector3fc> e : faceNormals.entrySet())
            if (e.getValue().dot(normal) < -.5f) return e.getKey();
        return null;
    }
}
