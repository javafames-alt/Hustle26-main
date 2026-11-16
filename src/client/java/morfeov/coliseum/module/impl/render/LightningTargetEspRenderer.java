package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import morfeov.coliseum.module.impl.themes.Theme;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class LightningTargetEspRenderer {
    static final class Segment {
        float x1, y1, z1;
        float x2, y2, z2;
        float alpha1, alpha2;
        float nx, ny, nz;
        float widthScale;
        int color;

        void set(float x1, float y1, float z1, float x2, float y2, float z2,
                 float alpha1, float alpha2, float nx, float ny, float nz,
                 float widthScale, int color) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.alpha1 = alpha1; this.alpha2 = alpha2;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.widthScale = widthScale;
            this.color = color;
        }
    }

    private Segment[] segmentPool = new Segment[1024];
    private int segmentCount;
    private float lightningTime = 0.0F;
    private long lastNanoTime = 0L;
    private float currentAlpha = 0.0F;
    private float targetAlpha = 0.0F;
    private LivingEntity currentTarget = null;

    private final Vector3f dir = new Vector3f();
    private final Vector3f toCam = new Vector3f();
    private final Vector3f perp = new Vector3f();

    LightningTargetEspRenderer() {
        for (int i = 0; i < segmentPool.length; i++) {
            segmentPool[i] = new Segment();
        }
    }

    void reset() {
        currentTarget = null;
        currentAlpha = 0.0F;
        targetAlpha = 0.0F;
        lastNanoTime = 0L;
        segmentCount = 0;
    }

    void render(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector, LivingEntity target, float dt, float partialTick, boolean hitRed) {
        long nanoNow = System.nanoTime();
        float deltaSec = lastNanoTime == 0L ? dt : Math.min((float) (nanoNow - lastNanoTime) / 1.0E9F, 0.05F);
        lastNanoTime = nanoNow;
        lightningTime += deltaSec;

        if (target != null && target.isAlive()) {
            targetAlpha = 255.0F;
            currentTarget = target;
        } else {
            targetAlpha = 0.0F;
        }

        float fadeSpeed = 1700.0F;
        if (currentAlpha < targetAlpha) {
            currentAlpha = Math.min(targetAlpha, currentAlpha + deltaSec * fadeSpeed);
        } else if (currentAlpha > targetAlpha) {
            currentAlpha = Math.max(targetAlpha, currentAlpha - deltaSec * fadeSpeed);
        }

        if (currentAlpha <= 0.001F) {
            currentTarget = null;
            return;
        }
        if (currentTarget == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();

        int baseColor = Theme.accent();
        int targetColor = baseColor;
        if (hitRed) {
            float hurtTimeRemaining = Math.max(0.0F, currentTarget.hurtTime - partialTick);
            float hurtFraction = (float) Math.sin(hurtTimeRemaining * (Math.PI / 10)) * 0.5F;
            if (hurtFraction > 0.0F) {
                targetColor = SwordTargetEspRenderer.lerpColor(baseColor, 0xffff0000, hurtFraction);
            }
        }

        double targetRelX = Mth.lerp(partialTick, currentTarget.xOld, currentTarget.getX());
        double targetRelY = Mth.lerp(partialTick, currentTarget.yOld, currentTarget.getY());
        double targetRelZ = Mth.lerp(partialTick, currentTarget.zOld, currentTarget.getZ());
        float height = currentTarget.getBbHeight();
        float radius = currentTarget.getBbWidth() * 0.75F + 0.16F;
        float masterAlpha = currentAlpha / 255.0F;

        segmentCount = 0;
        generateArcs((float) targetRelX, (float) targetRelY, height, (float) targetRelZ, radius, targetColor, masterAlpha);
        if (segmentCount == 0) return;

        int totalSegments = segmentCount;
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            float camX = (float) camPos.x;
            float camY = (float) camPos.y;
            float camZ = (float) camPos.z;

            for (int i = 0; i < totalSegments; i++) {
                Segment s = segmentPool[i];
                float p1x = s.x1 - camX, p1y = s.y1 - camY, p1z = s.z1 - camZ;
                float p2x = s.x2 - camX, p2y = s.y2 - camY, p2z = s.z2 - camZ;

                dir.set(p2x - p1x, p2y - p1y, p2z - p1z);
                float len = dir.length();
                if (len < 1e-4F) continue;
                dir.mul(1.0F / len);

                toCam.set((p1x + p2x) * 0.5F, (p1y + p2y) * 0.5F, (p1z + p2z) * 0.5F);
                float toCamLen = toCam.length();
                if (toCamLen > 1e-4F) toCam.mul(1.0F / toCamLen);

                perp.set(dir).cross(toCam);
                float perpLen = perp.length();
                if (perpLen < 1e-4F) {
                    perp.set(0.0F, 1.0F, 0.0F);
                } else {
                    perp.mul(1.0F / perpLen);
                }

                int col = s.color;
                float cr = (col >> 16 & 0xff) / 255.0f;
                float cg = (col >> 8 & 0xff) / 255.0f;
                float cb = (col & 0xff) / 255.0f;

                // Outer halo / glow ribbon
                float glowW = 0.055F * s.widthScale;
                float glowA1 = Math.clamp(s.alpha1 * 0.40F, 0.0F, 1.0F);
                float glowA2 = Math.clamp(s.alpha2 * 0.40F, 0.0F, 1.0F);
                float gx = perp.x * glowW, gy = perp.y * glowW, gz = perp.z * glowW;

                vc.addVertex(pose, p1x - gx, p1y - gy, p1z - gz).setColor(cr, cg, cb, glowA1);
                vc.addVertex(pose, p1x + gx, p1y + gy, p1z + gz).setColor(cr, cg, cb, glowA1);
                vc.addVertex(pose, p2x + gx, p2y + gy, p2z + gz).setColor(cr, cg, cb, glowA2);
                vc.addVertex(pose, p2x - gx, p2y - gy, p2z - gz).setColor(cr, cg, cb, glowA2);

                // Inner electric core ribbon
                float coreW = 0.016F * s.widthScale;
                float coreA1 = Math.clamp(s.alpha1 * 0.95F, 0.0F, 1.0F);
                float coreA2 = Math.clamp(s.alpha2 * 0.95F, 0.0F, 1.0F);
                float hx = perp.x * coreW, hy = perp.y * coreW, hz = perp.z * coreW;
                float wr = Mth.lerp(0.75F, cr, 1.0F);
                float wg = Mth.lerp(0.75F, cg, 1.0F);
                float wb = Mth.lerp(0.75F, cb, 1.0F);

                vc.addVertex(pose, p1x - hx, p1y - hy, p1z - hz).setColor(wr, wg, wb, coreA1);
                vc.addVertex(pose, p1x + hx, p1y + hy, p1z + hz).setColor(wr, wg, wb, coreA1);
                vc.addVertex(pose, p2x + hx, p2y + hy, p2z + hz).setColor(wr, wg, wb, coreA2);
                vc.addVertex(pose, p2x - hx, p2y - hy, p2z - hz).setColor(wr, wg, wb, coreA2);
            }
        });
    }

    private void generateArcs(float targetX, float targetY, float height, float targetZ, float radius, int color, float masterAlpha) {
        int boltCount = 3;
        int segmentsPerBolt = 3;
        float timeStep = lightningTime * 6.0F;
        int timeIndex = (int) timeStep;
        float frac = timeStep - timeIndex;
        float speed = 1.3F;
        float twoPi = (float) (Math.PI * 2);

        for (int bolt = 0; bolt < boltCount; bolt++) {
            float boltTime = lightningTime * speed + (float) bolt / boltCount;
            int boltCycle = (int) Math.floor(boltTime);
            float cycleFrac = boltTime - boltCycle;
            float attack = Mth.clamp(cycleFrac / 0.08F, 0.0F, 1.0F);
            float decay = 1.0F - Mth.clamp((cycleFrac - 0.42F) / 0.3F, 0.0F, 1.0F);
            float jitter = Mth.clamp(0.55F + 0.45F * noise4(bolt, 900, 0, timeIndex, frac), 0.0F, 1.0F);
            float flashEnvelope = attack * decay * jitter;
            if (flashEnvelope <= 0.01F) continue;

            float boltAlpha = masterAlpha * flashEnvelope;
            float rotAngle = lightningTime * 0.9F;
            float yOsc = height * 0.16F * (float) Math.sin(lightningTime * 1.7F + bolt * 2.1F);
            float startAngle = hash3(bolt, 11, boltCycle) * (float) Math.PI;
            float angleRange = 0.6F + 0.5F * Math.abs(hash3(bolt, 12, boltCycle));
            float dirSign = hash3(bolt, 13, boltCycle) >= 0.0F ? 1.0F : -1.0F;
            float startY = targetY + height * (0.85F + 0.05F * hash3(bolt, 14, boltCycle));
            float totalHeight = height * 0.67F;
            float boltRadius = radius * (1.0F + 0.12F * hash3(bolt, 15, boltCycle));
            float prevX = 0.0F, prevY = 0.0F, prevZ = 0.0F;

            for (int seg = 0; seg <= segmentsPerBolt; seg++) {
                float segFrac = (float) seg / segmentsPerBolt;
                float angle = startAngle + rotAngle + dirSign * angleRange * twoPi * segFrac;
                float curX = targetX + (float) Math.cos(angle) * boltRadius;
                float curZ = targetZ + (float) Math.sin(angle) * boltRadius;
                float curY = startY - totalHeight * segFrac + yOsc;

                if (seg > 0) {
                    int seed = (bolt * 131 + seg) * 17 + boltCycle * 101 | 1;
                    subdivide(prevX, prevY, prevZ, curX, curY, curZ, 4, boltAlpha, boltAlpha, color, seed, 0, 1.0F, timeIndex, frac);
                }

                prevX = curX;
                prevY = curY;
                prevZ = curZ;
            }
        }
    }

    private void subdivide(float x1, float y1, float z1, float x2, float y2, float z2,
                           int depth, float alpha1, float alpha2, int color,
                           int seed, int branchCount, float widthScale, int timeIndex, float frac) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (depth > 0 && len >= 1.0E-4F) {
            float dirX = dx / len, dirY = dy / len, dirZ = dz / len;
            float upX = Math.abs(dirY) < 0.9F ? 0.0F : 1.0F;
            float upY = Math.abs(dirY) < 0.9F ? 1.0F : 0.0F;
            float perp1X = -dirZ * upY;
            float perp1Y = dirZ * upX;
            float perp1Z = dirX * upY - dirY * upX;
            float perp1Len = (float) Math.sqrt(perp1X * perp1X + perp1Y * perp1Y + perp1Z * perp1Z);
            if (perp1Len < 1.0E-5F) perp1Len = 1.0F;
            perp1X /= perp1Len; perp1Y /= perp1Len; perp1Z /= perp1Len;

            float perp2X = dirY * perp1Z - dirZ * perp1Y;
            float perp2Y = dirZ * perp1X - dirX * perp1Z;
            float perp2Z = dirX * perp1Y - dirY * perp1X;

            float jitter1 = noise4(seed, 0, 0, timeIndex, frac);
            float jitter2 = noise4(seed, 1, 0, timeIndex, frac);
            float jitterAmount = len * 0.3F;
            float midX = (x1 + x2) * 0.5F + (perp1X * jitter1 + perp2X * jitter2) * jitterAmount;
            float midY = (y1 + y2) * 0.5F + (perp1Y * jitter1 + perp2Y * jitter2) * jitterAmount;
            float midZ = (z1 + z2) * 0.5F + (perp1Z * jitter1 + perp2Z * jitter2) * jitterAmount;
            float midAlpha = (alpha1 + alpha2) * 0.5F;

            subdivide(x1, y1, z1, midX, midY, midZ, depth - 1, alpha1, midAlpha, color, seed * 2 + 7, branchCount, widthScale, timeIndex, frac);
            subdivide(midX, midY, midZ, x2, y2, z2, depth - 1, midAlpha, alpha2, color, seed * 2 + 9, branchCount, widthScale, timeIndex, frac);

            if (branchCount < 2 && depth >= 2 && noise4(seed, 2, 0, timeIndex, frac) > 0.45F) {
                float branchNoise1 = noise4(seed, 3, 0, timeIndex, frac);
                float branchNoise2 = noise4(seed, 4, 0, timeIndex, frac);
                float bDirX = dirX * 0.4F + perp1X * branchNoise1 + perp2X * branchNoise2;
                float bDirY = dirY * 0.4F + perp1Y * branchNoise1 + perp2Y * branchNoise2;
                float bDirZ = dirZ * 0.4F + perp1Z * branchNoise1 + perp2Z * branchNoise2;
                float bLen = (float) Math.sqrt(bDirX * bDirX + bDirY * bDirY + bDirZ * bDirZ);
                if (bLen < 1.0E-5F) bLen = 1.0F;

                float branchDist = len * 0.75F;
                float endX = midX + bDirX / bLen * branchDist;
                float endY = midY + bDirY / bLen * branchDist;
                float endZ = midZ + bDirZ / bLen * branchDist;

                subdivide(midX, midY, midZ, endX, endY, endZ,
                    Math.min(depth - 1, 3), midAlpha * 0.7F, 0.0F, color,
                    seed * 3 + 13, branchCount + 1, widthScale * 0.55F, timeIndex, frac);
            }
        } else {
            float dx1 = x2 - x1, dy1 = y2 - y1, dz1 = z2 - z1;
            float slen = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1 + dz1 * dz1);
            if (slen >= 1.0E-5F) {
                if (alpha1 <= 0.004F && alpha2 <= 0.004F) return;
                float inv = 1.0F / slen;
                addSegment(x1, y1, z1, x2, y2, z2, alpha1, alpha2, dx1 * inv, dy1 * inv, dz1 * inv, widthScale, color);
            }
        }
    }

    private void addSegment(float x1, float y1, float z1, float x2, float y2, float z2,
                            float alpha1, float alpha2, float nx, float ny, float nz,
                            float widthScale, int color) {
        if (segmentCount == segmentPool.length) {
            Segment[] next = new Segment[segmentPool.length * 2];
            System.arraycopy(segmentPool, 0, next, 0, segmentPool.length);
            for (int i = segmentPool.length; i < next.length; i++) {
                next[i] = new Segment();
            }
            segmentPool = next;
        }
        segmentPool[segmentCount++].set(x1, y1, z1, x2, y2, z2, alpha1, alpha2, nx, ny, nz, widthScale, color);
    }

    private static float noise4(int v0, int v1, int v2, int v3, float frac) {
        float a = hash3(v0, v1, v3 * 4 + v2);
        float b = hash3(v0, v1, (v3 + 1) * 4 + v2);
        float s = frac * frac * (3.0F - 2.0F * frac);
        return a + (b - a) * s;
    }

    private static float hash3(int v0, int v1, int v2) {
        int h = v0 * 374761393 + v1 * 668265263 + v2 * 1274126177;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (h & 65535) / 32768.0F - 1.0F;
    }
}
