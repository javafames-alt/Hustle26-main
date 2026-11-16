package im.yaHustler.cosmetics.extra;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
final class CosmeticPlacement {
    private record WingAdjustment(float height,float distance) {}
    private record BackAdjustment(float distance,float rotation) {}
    private record PetAdjustment(float x,float y,float z) {}
    private record ModelAdjustment(float x,float y,float z,float rotationX,float rotationY,float rotationZ) {}
    private record Placement(float x,float y,float z,float scale,float autoYaw) {}
    static final Map<String, WingAdjustment> fixedWingAdjustments = Map.ofEntries(
        Map.entry("wings_1_cddf708b", new WingAdjustment(0.05249998F, 0.0F)),
        Map.entry("wings_3_81b06a4b", new WingAdjustment(0.101499975F, -0.0485F)),
        Map.entry("wings_7_ee401e1a", new WingAdjustment(-0.055999994F, 0.11074999F)),
        Map.entry("wings_8_ee401e1a", new WingAdjustment(0.17849997F, 0.094499975F)),
        Map.entry("wings_9_ee401e1a", new WingAdjustment(-0.24849999F, 0.0F)),
        Map.entry("wings_12_f684c6fe", new WingAdjustment(0.0F, 0.10100001F)),
        Map.entry("wings_14_19ec3eb9", new WingAdjustment(-0.1435F, 0.0F)),
        Map.entry("wings_22_3dfa663d", new WingAdjustment(0.0F, -0.064750016F)),
        Map.entry("wings_23_28b34740", new WingAdjustment(0.048999995F, -0.0485F))
    );
    static final Map<String, BackAdjustment> fixedBackAdjustments = Map.ofEntries(
        Map.entry("back_2_42b31d7f", new BackAdjustment(-0.22725F, 0.0F)),
        Map.entry("back_3_0c472e0e", new BackAdjustment(-0.22725F, 180.0F)),
        Map.entry("back_5_a3051e7e", new BackAdjustment(0.0F, 88.20001F)),
        Map.entry("back_10_94b04f2e", new BackAdjustment(-0.24675F, 180.0F)),
        Map.entry("back_12_6d765ef2", new BackAdjustment(-0.2305F, 0.0F)),
        Map.entry("back_15_3d08f827", new BackAdjustment(-0.2305F, 0.0F)),
        Map.entry("back_16_997ccf8a", new BackAdjustment(-0.09075001F, 0.0F)),
        Map.entry("back_17_d31ee85e", new BackAdjustment(-0.2305F, 0.0F)),
        Map.entry("back_20_e91ba7a0", new BackAdjustment(0.0F, 180.0F)),
        Map.entry("back_21_61fb616b", new BackAdjustment(-0.120000005F, 0.0F)),
        Map.entry("back_23_7fef0612", new BackAdjustment(-0.25F, 0.0F)),
        Map.entry("back_24_0212a027", new BackAdjustment(-0.23375F, 180.0F))
    );
    static final Map<String, PetAdjustment> fixedPetAdjustments = Map.ofEntries(
        Map.entry("pets_3_4d300ebb", new PetAdjustment(-90.0F, 0.0F, 0.0F)),
        Map.entry("pets_6_d97f2e47", new PetAdjustment(-90.0F, 180.0F, -23.399994F)),
        Map.entry("pets_18_95f0ae96", new PetAdjustment(0.0F, -180.0F, 0.0F)),
        Map.entry("pets_19_95f0ae96", new PetAdjustment(0.0F, -180.0F, 0.0F)),
        Map.entry("pets_20_95f0ae96", new PetAdjustment(0.0F, -180.0F, 0.0F)),
        Map.entry("pets_21_95f0ae96", new PetAdjustment(0.0F, -180.0F, 0.0F))
    );
    static final Map<String, ModelAdjustment> fixedModelAdjustments = Map.ofEntries(
        Map.entry("hats_2_cf6887c0", new ModelAdjustment(0.0F, 0.0F, -0.16F, 0.0F, 90.0F, -90.0F)),
        Map.entry("hats_13_e2b017aa", new ModelAdjustment(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 180.0F)),
        Map.entry("back_4_73991370", new ModelAdjustment(-0.14F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)),
        Map.entry("back_5_a3051e7e", new ModelAdjustment(0.14F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)),
        Map.entry("back_11_88774abf", new ModelAdjustment(0.0F, 0.0F, 0.0F, 0.0F, 180.0F, 0.0F)),
        Map.entry("back_14_82cf5272", new ModelAdjustment(-0.1F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F)),
        Map.entry("back_18_b9cf72a5", new ModelAdjustment(0.0F, 0.0F, 0.15F, 0.0F, 0.0F, 0.0F)),
        Map.entry("back_19_5b7aef16", new ModelAdjustment(0.0F, 0.0F, 0.0F, 0.0F, 180.0F, 0.0F)),
        Map.entry("back_24_0212a027", new ModelAdjustment(0.05F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F)),
        Map.entry("pets_6_d97f2e47", new ModelAdjustment(0.0F, 0.1F, 0.0F, 0.0F, 0.0F, 0.0F))
    );
    static final ModelAdjustment noModelAdjustment = new ModelAdjustment(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    static final Map<CosmeticItem, Placement> placementCache = new ConcurrentHashMap<>();
    boolean loaded = false;
    static final Set<String> topHats = Set.of(
        "hats_1_f83bf9c2",
        "hats_5_469c9b9e",
        "hats_8_6a481e43",
        "hats_9_5f7f08cb",
        "hats_15_5a319037",
        "hats_16_f37d302a",
        "hats_17_8f15edc6",
        "hats_20_f2a80fc7",
        "hats_25_2b9734ca"
    );
    static final Set<String> lowerPlayerWings = Set.of("wings_8_ee401e1a");
    static final Set<String> higherPlayerWings = Set.of("wings_3_81b06a4b");
    static final String evenHigherInfernalWings = "wings_4_ac93f2ee";
    static final Set<String> slightlyHigherPlayerWings = Set.of("wings_13_04875af2", "wings_23_28b34740");
    static final String raisedPlayerWings = "wings_9_ee401e1a";
    static final Set<String> oppositeVerticalWings = Set.of(
        "wings_3_81b06a4b", "wings_4_ac93f2ee", "wings_8_ee401e1a", "wings_9_ee401e1a", "wings_13_04875af2", "wings_22_3dfa663d", "wings_23_28b34740"
    );

    static float getWingHeightAdjustment(String slug) {
        WingAdjustment adjustment = fixedWingAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.height;
    }

    static float getWingDistanceAdjustment(String slug) {
        WingAdjustment adjustment = fixedWingAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.distance;
    }

    static float getBackDistanceAdjustment(String slug) {
        BackAdjustment adjustment = fixedBackAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.distance;
    }

    static float getBackRotationAdjustment(String slug) {
        BackAdjustment adjustment = fixedBackAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.rotation;
    }

    static float getPetRotationX(String slug) {
        PetAdjustment adjustment = fixedPetAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.x;
    }

    static float getPetRotationY(String slug) {
        PetAdjustment adjustment = fixedPetAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.y;
    }

    static float getPetRotationZ(String slug) {
        PetAdjustment adjustment = fixedPetAdjustments.get(slug);
        return adjustment == null ? 0.0F : adjustment.z;
    }

    static void applyPlacement(PoseStack matrices, CosmeticItem item, String category) {
        Placement placement = placementCache.computeIfAbsent(item, key -> placement(key, category));
        ModelAdjustment modelAdjustment = fixedModelAdjustments.getOrDefault(item.getSlug(), noModelAdjustment);
        float customHeight = "wings".equals(category) ? getWingHeightAdjustment(item.getSlug()) : 0.0F;
        float customDistance = "wings".equals(category) ? getWingDistanceAdjustment(item.getSlug()) : 0.0F;
        if ("back".equals(category)) {
            customDistance = getBackDistanceAdjustment(item.getSlug());
        }

        matrices.translate(placement.x + modelAdjustment.x, placement.y + customHeight + modelAdjustment.y, placement.z + customDistance + modelAdjustment.z);
        matrices.scale(placement.scale, placement.scale, placement.scale);
        float adjustmentRotationX = modelAdjustment.rotationX;
        float adjustmentRotationY = modelAdjustment.rotationY;
        float adjustmentRotationZ = modelAdjustment.rotationZ;
        if ("back".equals(category)) {
            adjustmentRotationZ += getBackRotationAdjustment(item.getSlug());
        } else if ("pets".equals(category)) {
            adjustmentRotationX += getPetRotationX(item.getSlug());
            adjustmentRotationY += getPetRotationY(item.getSlug());
            adjustmentRotationZ += getPetRotationZ(item.getSlug());
        }

        applyCenteredRotation(matrices, item, placement.autoYaw, adjustmentRotationX, adjustmentRotationY, adjustmentRotationZ);
        if ("wings".equals(category)) {
            float[] bounds = orientedBounds(item, placement.autoYaw);
            float pivotX = center(bounds[0], bounds[3]);
            float pivotY = center(bounds[1], bounds[4]);
            float pivotZ = center(bounds[2], bounds[5]);
            matrices.translate(pivotX, pivotY, pivotZ);
            if (oppositeVerticalWings.contains(item.getSlug())) {
                matrices.scale(-1.0F, 1.0F, 1.0F);
            } else {
                matrices.scale(1.0F, -1.0F, 1.0F);
            }

            matrices.translate(-pivotX, -pivotY, -pivotZ);
        }

        applyRotation(matrices, item.getRotationX(), item.getRotationY(), item.getRotationZ());
        if (placement.autoYaw != 0.0F) {
            matrices.rotate(Axis.YP.rotationDegrees(placement.autoYaw));
        }

        matrices.scale(item.getOrientationSignX(), item.getOrientationSignY(), item.getOrientationSignZ());
    }

    static void applyCenteredRotation(PoseStack matrices, CosmeticItem item, float autoYaw, float rotationX, float rotationY, float rotationZ) {
        if (rotationX != 0.0F || rotationY != 0.0F || rotationZ != 0.0F) {
            float[] bounds = orientedBounds(item, autoYaw);
            float pivotX = center(bounds[0], bounds[3]);
            float pivotY = center(bounds[1], bounds[4]);
            float pivotZ = center(bounds[2], bounds[5]);
            matrices.translate(pivotX, pivotY, pivotZ);
            applyRotation(matrices, rotationX, rotationY, rotationZ);
            matrices.translate(-pivotX, -pivotY, -pivotZ);
        }
    }

    static Placement placement(CosmeticItem item, String category) {
        float autoYaw = 0.0F;
        float[] bounds = orientedBounds(item, 0.0F);
        float width = bounds[3] - bounds[0];
        float height = bounds[4] - bounds[1];
        float depth = bounds[5] - bounds[2];
        if ("wings".equals(category) && depth > width * 1.35F) {
            autoYaw = 90.0F;
            bounds = orientedBounds(item, autoYaw);
            width = bounds[3] - bounds[0];
            height = bounds[4] - bounds[1];
            depth = bounds[5] - bounds[2];
        }

        if ("hats".equals(category)) {
            float scale = Math.min(1.0F, Math.min(safeRatio(1.25F, width), Math.min(safeRatio(1.15F, height), safeRatio(1.1F, depth))));
            float largest = Math.max(width, Math.max(height, depth));
            if (largest < 0.35F) {
                scale = Math.min(1.65F, safeRatio(0.52F, largest));
            } else if (largest < 0.55F) {
                scale = Math.min(1.3F, safeRatio(0.62F, largest));
            }

            float targetX = 0.0F;
            float targetY = topHats.contains(item.getSlug()) ? 0.27F : -0.03F;
            float targetZ = 0.0F;
            return new Placement(
                targetX - center(bounds[0], bounds[3]) * scale, targetY - bounds[1] * scale, targetZ - center(bounds[2], bounds[5]) * scale, scale, autoYaw
            );
        } else if ("wings".equals(category)) {
            float scale = Math.min(1.0F, Math.min(safeRatio(2.8F, width), safeRatio(1.75F, height)));
            float targetY = -0.32F;
            float targetZ = 0.14F;
            if (lowerPlayerWings.contains(item.getSlug())) {
                targetY -= 0.06F;
            }

            if (higherPlayerWings.contains(item.getSlug())) {
                targetY += 0.16F;
            }

            if ("wings_4_ac93f2ee".equals(item.getSlug())) {
                targetY += 0.21F;
            }

            if (slightlyHigherPlayerWings.contains(item.getSlug())) {
                targetY += 0.08F;
            }

            if ("wings_9_ee401e1a".equals(item.getSlug())) {
                targetY += 0.11F;
                targetZ += 0.14F;
            }

            return new Placement(
                -center(bounds[0], bounds[3]) * scale, targetY - center(bounds[1], bounds[4]) * scale, targetZ - bounds[2] * scale, scale, autoYaw
            );
        } else if ("back".equals(category)) {
            float scale = Math.min(1.0F, Math.min(safeRatio(1.18F, width), Math.min(safeRatio(1.48F, height), safeRatio(1.05F, depth))));
            return new Placement(
                -center(bounds[0], bounds[3]) * scale, -0.3F - center(bounds[1], bounds[4]) * scale, 0.1F - bounds[2] * scale, scale, autoYaw
            );
        } else {
            float scale = Math.min(0.9F, Math.min(safeRatio(0.72F, width), Math.min(safeRatio(0.76F, height), safeRatio(0.72F, depth))));
            return new Placement(
                -center(bounds[0], bounds[3]) * scale, -bounds[1] * scale, -center(bounds[2], bounds[5]) * scale, scale, autoYaw
            );
        }
    }

    static float[] orientedBounds(CosmeticItem item, float autoYaw) {
        BedrockModel model = item.getModel();
        Matrix4f transform = new Matrix4f();
        if (item.getRotationZ() != 0.0F) {
            transform.rotateZ((float)Math.toRadians(item.getRotationZ()));
        }

        if (item.getRotationY() != 0.0F) {
            transform.rotateY((float)Math.toRadians(item.getRotationY()));
        }

        if (item.getRotationX() != 0.0F) {
            transform.rotateX((float)Math.toRadians(item.getRotationX()));
        }

        if (autoYaw != 0.0F) {
            transform.rotateY((float)Math.toRadians(autoYaw));
        }

        transform.scale(item.getOrientationSignX(), item.getOrientationSignY(), item.getOrientationSignZ());
        float[] result = new float[]{
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        };
        Vector3f point = new Vector3f();
        float[] xs = new float[]{model.minX, model.maxX};
        float[] ys = new float[]{model.minY, model.maxY};
        float[] zs = new float[]{model.minZ, model.maxZ};

        for (float x : xs) {
            for (float y : ys) {
                for (float z : zs) {
                    transform.transformPosition(x, y, z, point);
                    result[0] = Math.min(result[0], point.x);
                    result[3] = Math.max(result[3], point.x);
                    result[1] = Math.min(result[1], point.y);
                    result[4] = Math.max(result[4], point.y);
                    result[2] = Math.min(result[2], point.z);
                    result[5] = Math.max(result[5], point.z);
                }
            }
        }

        return result;
    }

    static float safeRatio(float target, float value) {
        return value > 1.0E-4F ? target / value : 1.0F;
    }

    static float center(float min, float max) {
        return (min + max) * 0.5F;
    }

    static void applyRotation(PoseStack matrices, float x, float y, float z) {
        if (z != 0.0F) {
            matrices.rotate(Axis.ZP.rotationDegrees(z));
        }

        if (y != 0.0F) {
            matrices.rotate(Axis.YP.rotationDegrees(y));
        }

        if (x != 0.0F) {
            matrices.rotate(Axis.XP.rotationDegrees(x));
        }
    }
}
