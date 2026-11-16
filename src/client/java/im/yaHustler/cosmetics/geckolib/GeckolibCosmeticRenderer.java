package im.yaHustler.cosmetics.geckolib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import im.yaHustler.cosmetics.geo.GeoBone;
import im.yaHustler.cosmetics.geo.GeoCube;
import im.yaHustler.cosmetics.geo.GeoModel;
import im.yaHustler.cosmetics.geo.GeoQuad;
import im.yaHustler.cosmetics.geo.GeoVertex;
import im.yaHustler.cosmetics.model.CosmeticModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GeckolibCosmeticRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hustle-Cosmetics");
    private static GeckolibCosmeticRenderer instance;

    private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<>();
    private final Map<Integer, CosmeticAnimationData> animationCache = new ConcurrentHashMap<>();
    private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<>();
    private final GeckolibModelParser modelParser = new GeckolibModelParser();

    public static GeckolibCosmeticRenderer getInstance() {
        if (instance == null) {
            instance = new GeckolibCosmeticRenderer();
        }
        return instance;
    }

    public void renderCosmetic(CosmeticModel cosmetic, PoseStack matrices, VertexConsumer vertexConsumer, int light) {
        if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
            GeoModel geoModel = this.getOrParseModel(cosmetic);
            if (geoModel != null) {
                CosmeticAnimationData animationData = this.getOrParseAnimation(cosmetic);
                if (animationData != null) {
                    this.applyAnimations(geoModel, animationData, cosmetic.getId());
                }

                for (GeoBone bone : geoModel.topLevelBones) {
                    this.renderBone(bone, matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        }
    }

    private GeoModel getOrParseModel(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.modelCache.containsKey(id)) {
            return this.modelCache.get(id);
        }

        GeoModel geoModel = this.modelParser.parseModel(cosmetic);
        if (geoModel != null) {
            this.modelCache.put(id, geoModel);
            this.saveInitialBoneTransforms(id, geoModel);
            LOGGER.info("Cached model for cosmetic: {}", cosmetic.getName());
        }

        return geoModel;
    }

    private void saveInitialBoneTransforms(int id, GeoModel model) {
        HashMap<String, float[]> map = new HashMap<>();
        for (GeoBone bone : model.topLevelBones) {
            this.saveBonesRecursive(bone, map);
        }
        this.initialBoneTransforms.put(id, map);
    }

    private void saveBonesRecursive(GeoBone bone, Map<String, float[]> map) {
        map.put(
                bone.name,
                new float[]{
                        bone.getRotationX(),
                        bone.getRotationY(),
                        bone.getRotationZ(),
                        bone.getPositionX(),
                        bone.getPositionY(),
                        bone.getPositionZ(),
                        bone.getScaleX(),
                        bone.getScaleY(),
                        bone.getScaleZ()
                }
        );

        for (GeoBone child : bone.childBones) {
            this.saveBonesRecursive(child, map);
        }
    }

    private void renderBone(GeoBone bone, PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float r, float g, float b, float a) {
        if (!bone.isHidden) {
            matrices.pushPose();
            GeckoRenderHelper.translate(bone, matrices);
            GeckoRenderHelper.moveToPivot(bone, matrices);
            GeckoRenderHelper.rotate(bone, matrices);
            GeckoRenderHelper.scale(bone, matrices);
            GeckoRenderHelper.moveBackFromPivot(bone, matrices);

            for (GeoCube cube : bone.childCubes) {
                this.renderCube(cube, matrices, vertexConsumer, light, overlay, r, g, b, a);
            }

            for (GeoBone child : bone.childBones) {
                this.renderBone(child, matrices, vertexConsumer, light, overlay, r, g, b, a);
            }

            matrices.popPose();
        }
    }

    private void renderCube(GeoCube cube, PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float r, float g, float b, float a) {
        matrices.pushPose();
        GeckoRenderHelper.moveToPivot(cube, matrices);
        GeckoRenderHelper.rotate(cube, matrices);
        GeckoRenderHelper.moveBackFromPivot(cube, matrices);
        Matrix4f posMatrix = matrices.last().pose();
        Matrix3f normalMatrix = matrices.last().normal();

        for (GeoQuad quad : cube.quads) {
            if (quad != null) {
                Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
                normalMatrix.transform(normal);
                float nx = normal.x();
                float ny = normal.y();
                float nz = normal.z();

                if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && nx < 0.0F) {
                    nx = -nx;
                }
                if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && ny < 0.0F) {
                    ny = -ny;
                }
                if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && nz < 0.0F) {
                    nz = -nz;
                }

                for (GeoVertex vertex : quad.vertices) {
                    vertexConsumer.addVertex(posMatrix, vertex.position.getX(), vertex.position.getY(), vertex.position.getZ())
                            .setColor(r, g, b, a)
                            .setUv(vertex.textureU, vertex.textureV)
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(nx, ny, nz);
                }
            }
        }

        matrices.popPose();
    }

    private CosmeticAnimationData getOrParseAnimation(CosmeticModel cosmetic) {
        int id = cosmetic.getId();
        if (this.animationCache.containsKey(id)) {
            return this.animationCache.get(id);
        }

        if (this.noAnimationSet.contains(id)) {
            return null;
        }

        JsonObject animJson = cosmetic.getAnimationJson();
        if (animJson == null) {
            this.noAnimationSet.add(id);
            return null;
        }

        try {
            CosmeticAnimationData animData = this.parseAnimationData(animJson);
            if (animData != null) {
                this.animationCache.put(id, animData);
                LOGGER.info("Cached animation for cosmetic: {}", cosmetic.getName());
            } else {
                this.noAnimationSet.add(id);
            }
            return animData;
        } catch (Exception e) {
            LOGGER.error("Failed to parse animation for: " + cosmetic.getName(), e);
            this.noAnimationSet.add(id);
            return null;
        }
    }

    private CosmeticAnimationData parseAnimationData(JsonObject root) {
        CosmeticAnimationData data = new CosmeticAnimationData();
        if (!root.has("animations")) {
            return null;
        }

        JsonObject animations = root.getAsJsonObject("animations");
        Iterator<Map.Entry<String, JsonElement>> it = animations.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, JsonElement> entry = it.next();
            String animName = entry.getKey();
            JsonObject animObj = entry.getValue().getAsJsonObject();
            data.animationName = animName;
            data.loop = animObj.has("loop") && animObj.get("loop").getAsBoolean();
            data.length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0F;

            if (animObj.has("bones")) {
                JsonObject bones = animObj.getAsJsonObject("bones");
                for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                    String boneName = boneEntry.getKey();
                    JsonObject boneAnimObj = boneEntry.getValue().getAsJsonObject();
                    BoneAnimationData boneAnim = new BoneAnimationData();
                    if (boneAnimObj.has("rotation")) {
                        boneAnim.rotationKeyframes = this.parseKeyframes(boneAnimObj.get("rotation"));
                    }
                    if (boneAnimObj.has("position")) {
                        boneAnim.positionKeyframes = this.parseKeyframes(boneAnimObj.get("position"));
                    }
                    if (boneAnimObj.has("scale")) {
                        boneAnim.scaleKeyframes = this.parseKeyframes(boneAnimObj.get("scale"));
                    }
                    data.boneAnimations.put(boneName, boneAnim);
                }
            }
        }

        return data;
    }

    private Map<Float, float[]> parseKeyframes(JsonElement elem) {
        HashMap<Float, float[]> keyframes = new HashMap<>();
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    JsonElement val = entry.getValue();
                    float[] vec = new float[3];
                    if (val.isJsonObject()) {
                        JsonObject valObj = val.getAsJsonObject();
                        if (valObj.has("vector")) {
                            JsonArray arr = valObj.getAsJsonArray("vector");
                            vec[0] = arr.get(0).getAsFloat();
                            vec[1] = arr.get(1).getAsFloat();
                            vec[2] = arr.get(2).getAsFloat();
                        }
                    } else if (val.isJsonArray()) {
                        JsonArray arr = val.getAsJsonArray();
                        vec[0] = arr.get(0).getAsFloat();
                        vec[1] = arr.get(1).getAsFloat();
                        vec[2] = arr.get(2).getAsFloat();
                    }
                    keyframes.put(time, vec);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return keyframes;
    }

    private void applyAnimations(GeoModel model, CosmeticAnimationData animData, int id) {
        Map<String, float[]> initialTransforms = this.initialBoneTransforms.get(id);
        if (initialTransforms != null) {
            long startTime = this.animationStartTime.computeIfAbsent(id, k -> System.currentTimeMillis());
            float elapsed = (float) (System.currentTimeMillis() - startTime) / 1000.0F;
            float currentTime;
            if (animData.loop && animData.length > 0.0F) {
                currentTime = elapsed % animData.length;
            } else {
                currentTime = Math.min(elapsed, animData.length);
            }

            for (GeoBone bone : model.topLevelBones) {
                this.resetBoneRecursive(bone, initialTransforms);
            }

            for (Map.Entry<String, BoneAnimationData> entry : animData.boneAnimations.entrySet()) {
                String boneName = entry.getKey();
                BoneAnimationData boneAnim = entry.getValue();
                GeoBone bone = this.findBone(model, boneName);
                if (bone != null) {
                    float[] init = initialTransforms.get(boneName);
                    if (init == null) {
                        init = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
                    }

                    if (!boneAnim.rotationKeyframes.isEmpty()) {
                        float[] rot = this.interpolateKeyframes(boneAnim.rotationKeyframes, currentTime);
                        bone.setRotationX(init[0] + (float) Math.toRadians(-rot[0]));
                        bone.setRotationY(init[1] + (float) Math.toRadians(-rot[1]));
                        bone.setRotationZ(init[2] + (float) Math.toRadians(rot[2]));
                    }

                    if (!boneAnim.positionKeyframes.isEmpty()) {
                        float[] pos = this.interpolateKeyframes(boneAnim.positionKeyframes, currentTime);
                        bone.setPositionX(init[3] + pos[0]);
                        bone.setPositionY(init[4] + pos[1]);
                        bone.setPositionZ(init[5] + pos[2]);
                    }

                    if (!boneAnim.scaleKeyframes.isEmpty()) {
                        float[] scl = this.interpolateKeyframes(boneAnim.scaleKeyframes, currentTime);
                        bone.setScaleX(init[6] * scl[0]);
                        bone.setScaleY(init[7] * scl[1]);
                        bone.setScaleZ(init[8] * scl[2]);
                    }
                }
            }
        }
    }

    private void resetBoneRecursive(GeoBone bone, Map<String, float[]> transforms) {
        float[] vals = transforms.get(bone.name);
        if (vals != null) {
            bone.setRotationX(vals[0]);
            bone.setRotationY(vals[1]);
            bone.setRotationZ(vals[2]);
            bone.setPositionX(vals[3]);
            bone.setPositionY(vals[4]);
            bone.setPositionZ(vals[5]);
            bone.setScaleX(vals[6]);
            bone.setScaleY(vals[7]);
            bone.setScaleZ(vals[8]);
        }

        for (GeoBone child : bone.childBones) {
            this.resetBoneRecursive(child, transforms);
        }
    }

    private GeoBone findBone(GeoModel model, String name) {
        for (GeoBone bone : model.topLevelBones) {
            GeoBone found = this.findBoneRecursive(bone, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private GeoBone findBoneRecursive(GeoBone current, String name) {
        if (current.name.equals(name)) {
            return current;
        }
        for (GeoBone child : current.childBones) {
            GeoBone found = this.findBoneRecursive(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private float[] interpolateKeyframes(Map<Float, float[]> keyframes, float time) {
        if (keyframes.isEmpty()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }

        Float t1 = null;
        Float t2 = null;
        float[] v1 = null;
        float[] v2 = null;

        for (Map.Entry<Float, float[]> entry : keyframes.entrySet()) {
            float kfTime = entry.getKey();
            if (kfTime <= time && (t1 == null || kfTime > t1)) {
                t1 = kfTime;
                v1 = entry.getValue();
            }
            if (kfTime >= time && (t2 == null || kfTime < t2)) {
                t2 = kfTime;
                v2 = entry.getValue();
            }
        }

        if (v1 == null && v2 == null) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        if (v1 == null) {
            return v2;
        }
        if (v2 == null) {
            return v1;
        }
        if (t1.equals(t2)) {
            return v1;
        }

        float progress = (time - t1) / (t2 - t1);
        return new float[]{
                v1[0] + progress * (v2[0] - v1[0]),
                v1[1] + progress * (v2[1] - v1[1]),
                v1[2] + progress * (v2[2] - v1[2])
        };
    }

    private static class BoneAnimationData {
        Map<Float, float[]> rotationKeyframes = new HashMap<>();
        Map<Float, float[]> positionKeyframes = new HashMap<>();
        Map<Float, float[]> scaleKeyframes = new HashMap<>();
    }

    private static class CosmeticAnimationData {
        String animationName;
        boolean loop;
        float length;
        Map<String, BoneAnimationData> boneAnimations = new HashMap<>();
    }
}
