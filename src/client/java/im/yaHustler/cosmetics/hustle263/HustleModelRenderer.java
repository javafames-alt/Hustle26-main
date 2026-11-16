package im.yaHustler.cosmetics.hustle263;

import im.yaHustler.cosmetics.HustleCosmetics;
import im.yaHustler.cosmetics.geckolib.GeckoRenderHelper;
import im.yaHustler.cosmetics.geckolib.GeckolibModelParser;

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
import net.minecraft.util.Mth;
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

public class HustleModelRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hustle-Cosmetics");
    private static HustleModelRenderer instance;

    private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, CosmeticAnimationData> animationCache = new ConcurrentHashMap<>();
    private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<>();
    private final Map<Integer, String> activeAnimationName = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, float[]>> prevTransitionBoneTransforms = new ConcurrentHashMap<>();
    private final Map<Integer, Long> transitionStartTime = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, float[]>> lastAppliedBoneTransforms = new ConcurrentHashMap<>();
    private static final float TRANSITION_DURATION_MS = 200.0F;
    private boolean gliding, swimming, sneaking, moving;
    private float textureFrameOffset;
    private float textureHeightScale = 1.0F;

    public void setPlayerState(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
        this.gliding = gliding;
        this.swimming = swimming;
        this.sneaking = sneaking;
        this.moving = moving;
    }

    private void updateTextureFrame(CosmeticModel cosmetic) {
        textureFrameOffset = 0;
        textureHeightScale = 1;
        JsonObject animation = HustleCosmetics.textureAnimation(cosmetic.getId());
        if (animation == null) return;
        int count = Math.max(1, animation.get("frameCount").getAsInt());
        int ticks = Math.max(1, animation.get("frameTime").getAsInt());
        int frame = (int) ((System.nanoTime() / 50_000_000L / ticks) % count);
        textureHeightScale = (float) animation.get("frameHeight").getAsInt() / animation.get("totalHeight").getAsInt();
        textureFrameOffset = frame * textureHeightScale;
    }

    private final GeckolibModelParser modelParser = new GeckolibModelParser();

    public static HustleModelRenderer getInstance() {
        if (instance == null) {
            instance = new HustleModelRenderer();
        }
        return instance;
    }

    public void applyPetOrientation(CosmeticModel cosmetic, PoseStack matrices) {
        GeoModel model = getOrParseModel(cosmetic);
        GeoBone root = model == null || model.topLevelBones.isEmpty() ? null : model.topLevelBones.getFirst();
        if (root != null) GeckoRenderHelper.moveToPivot(root, matrices);
        matrices.rotate(com.mojang.math.Axis.YP.rotationDegrees(180));
        matrices.scale(1, -1, 1);
        if (root != null) GeckoRenderHelper.moveBackFromPivot(root, matrices);
    }

    public void renderCosmetic(CosmeticModel cosmetic, PoseStack matrices, VertexConsumer vertexConsumer, int light) {
        if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
            updateTextureFrame(cosmetic);
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
                            .setUv(vertex.textureU, vertex.textureV * textureHeightScale + textureFrameOffset)
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
        if (this.noAnimationSet.contains(id)) {
            return null;
        }

        JsonObject animJson = cosmetic.getAnimationJson();
        if (animJson == null) {
            this.noAnimationSet.add(id);
            return null;
        }

        try {
            String animationName = HustleAnimationSelector.select(animJson.getAsJsonObject("animations"), gliding, swimming, sneaking, moving);
            String cacheKey = id + ":" + animationName;
            if (animationCache.containsKey(cacheKey)) return animationCache.get(cacheKey);
            JsonObject selected = new JsonObject();
            JsonObject animations = new JsonObject();
            if (animationName != null) animations.add(animationName, animJson.getAsJsonObject("animations").get(animationName));
            selected.add("animations", animations);
            CosmeticAnimationData animData = this.parseAnimationData(selected);
            if (animData != null) {
                this.animationCache.put(cacheKey, animData);
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
            data.loop = !animObj.has("loop") || animObj.get("loop").getAsBoolean();
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
        if (elem.isJsonArray() || elem.isJsonPrimitive()) {
            keyframes.put(0.0F, readVector(elem));
        } else if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    JsonElement val = entry.getValue();
                    if (val.isJsonObject()) {
                        JsonObject value = val.getAsJsonObject();
                        val = value.has("post") ? value.get("post") : value.has("pre") ? value.get("pre") : value.get("vector");
                    }
                    float[] vec = readVector(val);
                    keyframes.put(time, vec);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return keyframes;
    }

    private static float[] readVector(JsonElement value) {
        float[] result = new float[3];
        if (value == null || value.isJsonNull()) return result;
        if (value.isJsonPrimitive()) {
            java.util.Arrays.fill(result, value.getAsFloat());
        } else if (value.isJsonArray()) {
            for (int axis = 0; axis < Math.min(3, value.getAsJsonArray().size()); axis++) {
                result[axis] = value.getAsJsonArray().get(axis).getAsFloat();
            }
        }
        return result;
    }

    private void applyAnimations(GeoModel model, CosmeticAnimationData animData, int id) {
        Map<String, float[]> initialTransforms = this.initialBoneTransforms.get(id);
        if (initialTransforms == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String currentTrack = animData.animationName != null ? animData.animationName : "";
        String lastTrack = this.activeAnimationName.get(id);

        if (lastTrack != null && !lastTrack.equals(currentTrack)) {
            // Плавное переключение трека анимации: сохраняем текущие позы костей для интерполяции
            Map<String, float[]> lastApplied = this.lastAppliedBoneTransforms.get(id);
            if (lastApplied != null) {
                this.prevTransitionBoneTransforms.put(id, new HashMap<>(lastApplied));
                this.transitionStartTime.put(id, now);
            }
            this.animationStartTime.put(id, now);
        }
        this.activeAnimationName.put(id, currentTrack);

        long startTime = this.animationStartTime.computeIfAbsent(id, k -> now);
        float elapsed = (float) (now - startTime) / 1000.0F;
        float currentTime;
        if (animData.loop && animData.length > 0.0F) {
            currentTime = elapsed % animData.length;
        } else {
            currentTime = Math.min(elapsed, animData.length);
        }

        Long transStart = this.transitionStartTime.get(id);
        float blend = 1.0F;
        Map<String, float[]> prevTransforms = null;
        if (transStart != null) {
            float transElapsed = (float) (now - transStart);
            if (transElapsed < TRANSITION_DURATION_MS) {
                blend = Mth.clamp(transElapsed / TRANSITION_DURATION_MS, 0.0F, 1.0F);
                blend = blend * blend * (3.0F - 2.0F * blend); // smoothstep
                prevTransforms = this.prevTransitionBoneTransforms.get(id);
            } else {
                this.prevTransitionBoneTransforms.remove(id);
                this.transitionStartTime.remove(id);
            }
        }

        for (GeoBone bone : model.topLevelBones) {
            this.resetBoneRecursive(bone, initialTransforms);
        }

        Map<String, float[]> currentApplied = new HashMap<>();

        for (Map.Entry<String, BoneAnimationData> entry : animData.boneAnimations.entrySet()) {
            String boneName = entry.getKey();
            BoneAnimationData boneAnim = entry.getValue();
            GeoBone bone = this.findBone(model, boneName);
            if (bone != null) {
                float[] init = initialTransforms.get(boneName);
                if (init == null) {
                    init = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
                }

                float targetRotX = init[0];
                float targetRotY = init[1];
                float targetRotZ = init[2];
                if (!boneAnim.rotationKeyframes.isEmpty()) {
                    float[] rot = this.interpolateKeyframes(boneAnim.rotationKeyframes, currentTime);
                    targetRotX = init[0] + (float) Math.toRadians(-rot[0]);
                    targetRotY = init[1] + (float) Math.toRadians(-rot[1]);
                    targetRotZ = init[2] + (float) Math.toRadians(rot[2]);
                }

                float targetPosX = init[3];
                float targetPosY = init[4];
                float targetPosZ = init[5];
                if (!boneAnim.positionKeyframes.isEmpty()) {
                    float[] pos = this.interpolateKeyframes(boneAnim.positionKeyframes, currentTime);
                    targetPosX = init[3] + pos[0];
                    targetPosY = init[4] + pos[1];
                    targetPosZ = init[5] + pos[2];
                }

                float targetScaleX = init[6];
                float targetScaleY = init[7];
                float targetScaleZ = init[8];
                if (!boneAnim.scaleKeyframes.isEmpty()) {
                    float[] scl = this.interpolateKeyframes(boneAnim.scaleKeyframes, currentTime);
                    targetScaleX = init[6] * scl[0];
                    targetScaleY = init[7] * scl[1];
                    targetScaleZ = init[8] * scl[2];
                }

                if (blend < 1.0F && prevTransforms != null && prevTransforms.containsKey(boneName)) {
                    float[] prev = prevTransforms.get(boneName);
                    targetRotX = Mth.lerp(blend, prev[0], targetRotX);
                    targetRotY = Mth.lerp(blend, prev[1], targetRotY);
                    targetRotZ = Mth.lerp(blend, prev[2], targetRotZ);
                    targetPosX = Mth.lerp(blend, prev[3], targetPosX);
                    targetPosY = Mth.lerp(blend, prev[4], targetPosY);
                    targetPosZ = Mth.lerp(blend, prev[5], targetPosZ);
                    targetScaleX = Mth.lerp(blend, prev[6], targetScaleX);
                    targetScaleY = Mth.lerp(blend, prev[7], targetScaleY);
                    targetScaleZ = Mth.lerp(blend, prev[8], targetScaleZ);
                }

                bone.setRotationX(targetRotX);
                bone.setRotationY(targetRotY);
                bone.setRotationZ(targetRotZ);
                bone.setPositionX(targetPosX);
                bone.setPositionY(targetPosY);
                bone.setPositionZ(targetPosZ);
                bone.setScaleX(targetScaleX);
                bone.setScaleY(targetScaleY);
                bone.setScaleZ(targetScaleZ);

                currentApplied.put(boneName, new float[]{
                        targetRotX, targetRotY, targetRotZ,
                        targetPosX, targetPosY, targetPosZ,
                        targetScaleX, targetScaleY, targetScaleZ
                });
            }
        }

        this.lastAppliedBoneTransforms.put(id, currentApplied);
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
