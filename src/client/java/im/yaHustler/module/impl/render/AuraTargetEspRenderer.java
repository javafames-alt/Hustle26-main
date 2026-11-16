package im.yaHustler.module.impl.render;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
public final class AuraTargetEspRenderer {
 private static final Identifier BLOOM=Identifier.fromNamespaceAndPath("hustle263","textures/targetesp/bloom.png"), FIRE=Identifier.fromNamespaceAndPath("hustle263","textures/targetesp/firepart2.png");
 private static final long DAMAGE_FLASH_DURATION=350L,L_SPAWN_INTERVAL_MS=160L;
 private static final float GOLDEN_ANGLE=2.399963229728653f;
 private static final int L_MAX_BOLTS=16;
 private static final class LightningBolt {java.util.List<Vec3> points;long spawnTime;int lifetimeMs;}
 private final java.util.List<LightningBolt> lightBolts=new java.util.ArrayList<>();
 private long lightLastSpawn,lastDamageTime;private int lastTargetEntityId=Integer.MIN_VALUE,lastHurtTime;private float damageFlash;
 private boolean damageRed;
 private final EspBillboards billboards=new EspBillboards();
 public interface Style {String mode();int baseColor();boolean damageRed();boolean glow();float speed();float size();}
 public void render(PoseStack pose,SubmitNodeCollector collector,LivingEntity target,Style style){
 if(target==null||!target.isAlive()) {lightBolts.clear();return;}damageRed=style.damageRed();updateDamageFlash(target);billboards.begin(pose,collector);billboards.glow=style.glow();float tickDelta=net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
 switch(style.mode()){case "Призрачный Всадник"->renderSoulRider(target,tickDelta,style);case "Молнии"->renderLightningBolts(target,tickDelta,style);case "Кристаллы"->renderCrystals(target,tickDelta,style);case "Призраки"->renderGhosts(target,tickDelta,style);}
 }
    private float hurtFactor(LivingEntity entity) {
        return !damageRed ? 0 : Mth.clamp(entity.hurtTime / 10.0f, 0.0f, 1.0f);
    }

    private double width(LivingEntity entity) {
        return entity.getBoundingBox().getXsize();
    }

    private double height(LivingEntity entity) {
        return entity.getBoundingBox().getYsize();
    }

    private void updateDamageFlash(LivingEntity target) {
        if (target.hurtTime > lastHurtTime) {
            lastDamageTime = System.currentTimeMillis();
        }
        lastHurtTime = target.hurtTime;
    }

    private int accentHurt(LivingEntity entity, int alpha, int base) {
        float hf = hurtFactor(entity);
        int r = Mth.clamp((int) (EspColor.red(base) * (1.0f - hf) + 255 * hf), 0, 255);
        int g = Mth.clamp((int) (EspColor.green(base) * (1.0f - hf)), 0, 255);
        int b = Mth.clamp((int) (EspColor.blue(base) * (1.0f - hf)), 0, 255);
        return EspColor.rgba(r, g, b, Mth.clamp(alpha, 0, 255));
    }

    private void renderSoulRider(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double baseY = v.y + height(target) * 0.52;
        long time = System.currentTimeMillis();
        int color = accentHurt(target, 255, style.baseColor());
        float sp = style.speed();
        float sz = style.size();

        int trails = 16;
        for (int i = 0; i < trails; i++) {
            float trail = 1.0f - (float) i / trails * 0.75f;
            double angle = (0.15 * sp) * ((double) time - i * 11.0) / 25.0;
            float size = (float) (9.5f * 0.035 * trail * sz);
            double s = Math.sin(angle) * 0.75 * sz;
            double c = Math.cos(angle) * 0.75 * sz;

            float pAlpha = (float) Math.pow(trail, 1.2);
            int segColor = EspColor.withAlpha(color, (int) (255 * pAlpha));

            orbital(v.x, baseY, v.z, s, c, -c, size, segColor);
            orbital(v.x, baseY, v.z, -s, s, -c, size, segColor);
            orbital(v.x, baseY, v.z, -s, -s, c, size, segColor);
        }
    }

    private void renderSoulOrbit(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double w = width(target) * style.size();
        double h = height(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());

        boolean balancedPlus = true;
        int rings = 3;
        int perRing = balancedPlus ? 6 : 4;

        for (int ringIdx = 0; ringIdx < rings; ringIdx++) {
            float ringPhase = ringIdx * ((float) Math.PI / rings);
            float tilt = Mth.sin(ringPhase) * 0.55f;
            float radius = (float) (w * 0.75 + 0.35f + ringIdx * 0.12f);
            float speed = 1.15f - ringIdx * 0.22f;
            float yOffset = Mth.cos(ringPhase * 1.7f) * (float) h * 0.28f;

            for (int i = 0; i < perRing; i++) {
                float theta = t * speed + i * ((float) (Math.PI * 2.0) / perRing) + ringPhase;
                double px = v.x + Mth.cos(theta) * radius;
                double pz = v.z + Mth.sin(theta) * radius;
                double py = v.y + h * 0.5 + yOffset
                        + Mth.sin(theta * 2.0f + ringPhase) * tilt * 0.5
                        + Mth.sin(t * 1.7f + i) * 0.05;

                float pulse = 0.8f + 0.2f * Mth.sin(t * 3.2f + i * 1.31f + ringIdx);
                float size = 0.16f * pulse * style.size();

                int ghost = EspColor.withAlpha(color, (int) (EspColor.alpha(color) * (0.30f + 0.12f * ringIdx)));
                billboards.drawBillboard(FIRE, px, py, pz, size * 2.4f, size * 2.4f, ghost, 0.0f);
                billboards.drawBillboard(FIRE, px, py, pz, size, size, color, 0.0f);

                float echoTheta = theta - 0.22f;
                double ex = v.x + Mth.cos(echoTheta) * radius;
                double ez = v.z + Mth.sin(echoTheta) * radius;
                billboards.drawBillboard(FIRE, ex, py, ez, size * 0.62f, size * 0.62f,
                        EspColor.withAlpha(color, (int) (EspColor.alpha(color) * 0.45f)), 0.0f);
            }
        }
    }

    private void renderSoulVortex(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double w = width(target) * style.size();
        double h = height(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());

        boolean maxFps = false;
        int count = 14;
        float totalHeight = (float) h + 0.45f;

        for (int i = 0; i < count; i++) {
            float life = (t * 0.55f + i / (float) count) % 1.0f;
            float rise = life;
            float angle = t * 2.1f + life * 5.5f + i * GOLDEN_ANGLE;

            float radius = (float) (w * 0.65 + 0.42f) * (1.0f - 0.72f * rise);
            double px = v.x + Mth.cos(angle) * radius;
            double pz = v.z + Mth.sin(angle) * radius;
            double py = v.y - 0.15f + totalHeight * rise;

            float fade = Mth.clamp(Math.min(life, 1.0f - life) * 6.0f, 0.0f, 1.0f);
            float pulse = 0.78f + 0.22f * Mth.sin(t * 4.0f + i * 2.13f);
            float size = (0.20f - 0.08f * rise) * pulse * style.size();

            int col = EspColor.withAlpha(color, (int) (EspColor.alpha(color) * fade));
            int glow = EspColor.withAlpha(color, (int) (EspColor.alpha(color) * fade * 0.32f));
            billboards.drawBillboard(FIRE, px, py, pz, size * 2.6f, size * 2.6f, glow, 0.0f);
            billboards.drawBillboard(FIRE, px, py, pz, size, size, col, 0.0f);
        }

        int coreCount = maxFps ? 3 : 5;
        for (int i = 0; i < coreCount; i++) {
            float angle = -t * 3.1f + i * ((float) (Math.PI * 2.0) / coreCount);
            float radius = (0.10f + 0.04f * Mth.sin(t * 3.7f + i)) * style.size();
            double px = v.x + Mth.cos(angle) * radius;
            double pz = v.z + Mth.sin(angle) * radius;
            double py = v.y + h * 0.5 + Mth.sin(t * 2.2f + i * 1.57f) * 0.12f;
            float size = 0.12f * (0.85f + 0.15f * Mth.sin(t * 5.1f + i)) * style.size();

            billboards.drawBillboard(FIRE, px, py, pz, size * 2.2f, size * 2.2f,
                    EspColor.rgba(255, 255, 255, 70), 0.0f);
            billboards.drawBillboard(FIRE, px, py, pz, size, size,
                    EspColor.rgba(255, 255, 255, 190), 0.0f);
        }
    }

    private void renderNeonRings(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double h = height(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());
        float r = 0.75f * style.size();

        int points = 16;
        for (int ring = 0; ring < 2; ring++) {
            double ringY = v.y + (ring == 0 ? 0.2 : h * 0.65) + Math.sin(t * 2.5 + ring) * 0.08;
            double rot = t * (ring == 0 ? 2.0 : -2.0);

            for (int i = 0; i < points; i++) {
                double a = rot + (i / (double) points) * Math.PI * 2.0;
                double px = v.x + Math.cos(a) * r;
                double pz = v.z + Math.sin(a) * r;
                float size = 0.14f * style.size();

                billboards.drawBillboard(BLOOM, px, ringY, pz, size * 2.0f, size * 2.0f, EspColor.multiplyAlpha(color, 0.4f), 0.0f);
                billboards.drawBillboard(BLOOM, px, ringY, pz, size, size, color, 0.0f);
            }
        }
    }

    private void renderDnaHelix(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double h = height(target) + 0.2;
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());
        float r = 0.65f * style.size();

        int segments = 18;
        for (int i = 0; i < segments; i++) {
            float progress = (float) i / segments;
            double py = v.y + progress * h;
            double a1 = t * 3.0 + progress * Math.PI * 3.0;
            double a2 = a1 + Math.PI;

            double p1x = v.x + Math.cos(a1) * r;
            double p1z = v.z + Math.sin(a1) * r;
            double p2x = v.x + Math.cos(a2) * r;
            double p2z = v.z + Math.sin(a2) * r;

            float size = 0.11f * style.size();
            billboards.drawBillboard(FIRE, p1x, py, p1z, size * 1.8f, size * 1.8f, color, 0.0f);
            billboards.drawBillboard(FIRE, p2x, py, p2z, size * 1.8f, size * 1.8f, color, 0.0f);
        }
    }

    private void orbital(double bx, double by, double bz, double dx, double dy, double dz, float size, int color) {
        int glow = EspColor.multiplyAlpha(color, 0.35f);
        billboards.drawBillboard(BLOOM, bx + dx, by + dy, bz + dz, size * 2.2f, size * 2.2f, glow, 0.0f);
        billboards.drawBillboard(FIRE, bx + dx, by + dy, bz + dz, size, size, color, 0.0f);
    }

    private void renderMarkerPins(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double h = height(target);
        double w = width(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());

        int pins = 4;
        float pinRadius = (float) (w * 1.15 + 0.25) * style.size();
        for (int i = 0; i < pins; i++) {
            double angle = t * 1.4 + i * (Math.PI / 2.0);
            double px = v.x + Math.cos(angle) * pinRadius;
            double pz = v.z + Math.sin(angle) * pinRadius;
            double py = v.y + h * (0.45 + 0.25 * Math.sin(t * 1.7 + i * 1.9));

            float pinSize = 0.14f * style.size();
            int pinGlow = EspColor.multiplyAlpha(color, 0.35f);
            billboards.drawBillboard(BLOOM, px, py, pz, pinSize * 2.6f, pinSize * 2.6f, pinGlow, 0.0f);
            billboards.drawBillboard(FIRE, px, py, pz, pinSize, pinSize, color, 0.0f);

            for (int s = 1; s <= 3; s++) {
                float trailAlpha = 1.0f - s * 0.25f;
                double sy = py - s * 0.22f * style.size();
                billboards.drawBillboard(FIRE, px, sy, pz, pinSize * 0.5f * trailAlpha, pinSize * 0.5f * trailAlpha,
                        EspColor.withAlpha(color, (int) (EspColor.alpha(color) * trailAlpha * 0.6f)), 0.0f);
            }
        }

        int sparks = 10;
        for (int i = 0; i < sparks; i++) {
            double seed = i * 0.618033988749895;
            double lifetime = (t * 0.55 + seed) % 1.0;
            double angle = seed * Math.PI * 2.0 + t * 0.4;
            double sparkR = (w * 0.9 + 0.15) * style.size();
            double px = v.x + Math.cos(angle) * sparkR;
            double pz = v.z + Math.sin(angle) * sparkR;
            double py = v.y + lifetime * (h + 0.5);

            float fade = (float) (1.0 - lifetime);
            float sparkSize = 0.09f * style.size() * (0.4f + fade * 0.6f);
            int sparkColor = EspColor.withAlpha(color, (int) (EspColor.alpha(color) * fade * 0.8f));
            billboards.drawBillboard(BLOOM, px, py, pz, sparkSize * 2.0f, sparkSize * 2.0f,
                    EspColor.multiplyAlpha(sparkColor, 0.4f), 0.0f);
            billboards.drawBillboard(FIRE, px, py, pz, sparkSize, sparkSize, sparkColor, 0.0f);
        }
    }

    private void renderCrystals(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double h = height(target);
        double w = width(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int baseColor = accentHurt(target, 255, style.baseColor());

        int totalCrystals = 28;
        float baseRadius = (float) (w * 0.52 + 0.12) * style.size();

        for (int i = 0; i < totalCrystals; i++) {
            int stream = i % 2;
            int indexInStream = i / 2;
            int countInStream = totalCrystals / 2;
            float progress = (float) indexInStream / countInStream;

            double py = v.y + 0.08 + progress * (h * 0.95) + Math.sin(t * 3.5 + i * 0.9) * 0.08;

            double streamOffset = stream * Math.PI;
            double a = t * 2.6 + progress * (Math.PI * 3.2) + streamOffset;

            double r = baseRadius + Math.sin(t * 3.0 + i * 1.2) * 0.04;
            double px = v.x + Math.cos(a) * r;
            double pz = v.z + Math.sin(a) * r;

            float sz = 0.18f * style.size();
            float crH = sz * 1.20f;
            float crW = sz * 0.60f;

            float rotY = t * 4.2f + i * 1.3f;
            float rotX = (float) Math.sin(t * 2.8f + i) * 0.35f;

            float cosY = Mth.cos(rotY), sinY = Mth.sin(rotY);
            float cosX = Mth.cos(rotX), sinX = Mth.sin(rotX);

            Vec3 top = rotateAndTranslate(0, crH, 0, cosY, sinY, cosX, sinX, px, py, pz);
            Vec3 bot = rotateAndTranslate(0, -crH, 0, cosY, sinY, cosX, sinX, px, py, pz);

            Vec3 w0 = rotateAndTranslate(crW, 0, 0, cosY, sinY, cosX, sinX, px, py, pz);
            Vec3 w1 = rotateAndTranslate(0, 0, crW, cosY, sinY, cosX, sinX, px, py, pz);
            Vec3 w2 = rotateAndTranslate(-crW, 0, 0, cosY, sinY, cosX, sinX, px, py, pz);
            Vec3 w3 = rotateAndTranslate(0, 0, -crW, cosY, sinY, cosX, sinX, px, py, pz);

            int apexColor = EspColor.rgba(255, 255, 255, 245);
            int facetColor = EspColor.withAlpha(baseColor, 215);
            int baseFacetColor = EspColor.withAlpha(baseColor, 180);

            drawCrystalFacet(top, w0, bot, w1, apexColor, facetColor, baseFacetColor);
            drawCrystalFacet(top, w1, bot, w2, apexColor, facetColor, baseFacetColor);
            drawCrystalFacet(top, w2, bot, w3, apexColor, facetColor, baseFacetColor);
            drawCrystalFacet(top, w3, bot, w0, apexColor, facetColor, baseFacetColor);

            int bloomColor = EspColor.multiplyAlpha(baseColor, 0.45f);
            billboards.drawBillboard(BLOOM, px, py, pz, sz * 2.5f, sz * 2.5f, bloomColor, 0.0f);
            billboards.drawBillboard(FIRE, px, py, pz, sz * 0.70f, sz * 0.70f, apexColor, 0.0f);
        }
    }

    private Vec3 rotateAndTranslate(float x, float y, float z, float cosY, float sinY, float cosX, float sinX, double px, double py, double pz) {
        float y1 = y * cosX - z * sinX;
        float z1 = y * sinX + z * cosX;
        float x2 = x * cosY + z1 * sinY;
        float z2 = -x * sinY + z1 * cosY;
        return new Vec3(px + x2, py + y1, pz + z2);
    }

    private void drawCrystalFacet(Vec3 top, Vec3 w1, Vec3 bot, Vec3 w2, int cTop, int cWaist, int cBot) {
        billboards.drawWorldQuad(FIRE,
                top.x, top.y, top.z,
                w1.x, w1.y, w1.z,
                bot.x, bot.y, bot.z,
                w2.x, w2.y, w2.z,
                cTop, cWaist, cBot, cWaist,
                0.5f, 0.0f,
                1.0f, 0.5f,
                0.5f, 1.0f,
                0.0f, 0.5f);
    }

    private void renderGhosts(LivingEntity target, float tickDelta, Style style) {
        Vec3 v = target.getPosition(tickDelta);
        double h = height(target);
        double w = width(target);
        long time = System.currentTimeMillis();
        float t = ((time % 100000L) / 1000.0f) * style.speed();
        int color = accentHurt(target, 255, style.baseColor());

        int arms = 4;
        int segments = 14;
        double arcStep = Math.toRadians(60.0) / segments;
        double radius = w * 1.25 * style.size();
        double verticalCenter = h * 0.5 + 0.15;
        double verticalAmplitude = 0.65;

        for (int arm = 0; arm < arms; arm++) {
            double verticalTime = t * 1.8 + arm * 15.0;
            double armPhase = arm * (Math.PI / 2.0);

            for (int segment = 0; segment <= segments; segment++) {
                double arc = segment * arcStep;
                double angle = arc + (t * 2.2) + armPhase;
                double px = v.x + radius * Math.cos(angle);
                double py = v.y + Math.sin(verticalTime + arc + arm) * verticalAmplitude + verticalCenter;
                double pz = v.z + radius * Math.sin(angle);

                float progress = (float) segment / segments;
                float size = 0.22f * (0.55f + progress) * style.size();

                float segAlpha = 0.30f + 0.70f * progress;
                int segColor = accentHurt(target, (int) (255 * segAlpha), style.baseColor());

                int glow = EspColor.multiplyAlpha(segColor, 0.40f);
                billboards.drawBillboard(BLOOM, px, py, pz, size * 2.2f, size * 2.2f, glow, 0.0f);
                billboards.drawBillboard(FIRE, px, py, pz, size, size, segColor, 0.0f);
            }
        }
    }

    private void renderCircleMarker(LivingEntity target, float tickDelta, Style style) {
        Vec3 base = target.getPosition(tickDelta);
        double h = height(target);
        double r = Math.max(0.68, width(target) * 1.05) * style.size();
        int baseColor = style.baseColor();
        float speed = style.speed();
        long now = System.currentTimeMillis();
        float time = (now % 100000L) * 0.001f * speed;

        float cycle = (time * 1000.0f % 1500.0f) / 1500.0f;
        float progress = 0.5f + 0.5f * (float) Math.cos(cycle * Math.PI * 2.0);
        double movingY = base.y + h * progress;
        float movingAngle = time * 400.0f / 2.5f;

        int arms = 4;
        int particlesPerArm = 15;

        for (int arm = 0; arm < arms; arm++) {
            double armAngleOffset = arm * (Math.PI / 2.0);
            for (int particle = 0; particle < particlesPerArm; particle++) {
                float particleProgress = particle / 14.0f;
                float angle = (float) (0.2f * (movingAngle - particle * 3.5f) / 15.0f + armAngleOffset);
                double localX = Math.cos(angle) * r;
                double localZ = Math.sin(angle) * r;

                float tail = (float) Math.pow(1.0f - particleProgress, 1.3);
                float pAlpha = Mth.clamp(tail * 0.95f, 0.0f, 1.0f);
                int color = accentHurt(target, (int) (pAlpha * 255.0f), baseColor);
                float pSize = 0.22f * style.size() * (0.6f + (1.0f - particleProgress) * 0.4f);

                billboards.drawBillboard(BLOOM, base.x + localX, movingY + Math.sin(particleProgress * Math.PI) * 0.08, base.z + localZ, pSize, pSize, color, 0.0f);
            }
        }

        int ringSegments = 24;
        double groundY = base.y + 0.04;
        float ringPulse = (float) (1.0 + Math.sin(time * 6.0) * 0.08);
        double ringRadius = r * ringPulse;
        for (int i = 0; i < ringSegments; i++) {
            double ringAngle = (i / (double) ringSegments) * Math.PI * 2.0 + time * 1.5;
            double px = base.x + Math.cos(ringAngle) * ringRadius;
            double pz = base.z + Math.sin(ringAngle) * ringRadius;
            int ringColor = accentHurt(target, 160, baseColor);
            float dotSize = 0.16f * style.size();
            billboards.drawBillboard(BLOOM, px, groundY, pz, dotSize, dotSize, ringColor, 0.0f);
        }
    }

    private void renderLightningBolts(LivingEntity target, float tickDelta, Style style) {
        Vec3 basePos = target.getPosition(tickDelta);
        long now = System.currentTimeMillis();
        int targetId = target.getId();
        if (targetId != lastTargetEntityId) {
            lastTargetEntityId = targetId;
            lightBolts.clear();
        }

        float speed = Math.max(0.2f, style.speed());
        long spawnInterval = (long) (L_SPAWN_INTERVAL_MS / speed);
        if (now - lightLastSpawn > spawnInterval && lightBolts.size() < L_MAX_BOLTS) {
            int toSpawn = 2;
            for (int s = 0; s < toSpawn && lightBolts.size() < L_MAX_BOLTS; s++) {
                lightBolts.add(spawnLightningBolt(target, basePos, style));
            }
            lightLastSpawn = now;
        }

        lightBolts.removeIf(bolt -> now - bolt.spawnTime > bolt.lifetimeMs);

        int baseColor = accentHurt(target, 255, style.baseColor());
        int outerColor = EspColor.multiplyAlpha(baseColor, 0.45f);
        int glowColor = EspColor.multiplyAlpha(baseColor, 0.22f);
        int coreColor = EspColor.rgba(255, 255, 255, 240);

        for (LightningBolt bolt : lightBolts) {
            float life = (now - bolt.spawnTime) / (float) bolt.lifetimeMs;
            float fade = 1.0f - life;
            float flicker = 0.75f + (float) (Math.random() * 0.25f);
            float boltAlpha = fade * flicker;
            if (boltAlpha <= 0.02f) continue;

            int boltOuter = EspColor.withAlpha(outerColor, (int) (EspColor.alpha(outerColor) * boltAlpha));
            int boltGlow = EspColor.withAlpha(glowColor, (int) (EspColor.alpha(glowColor) * boltAlpha));
            int boltCore = EspColor.withAlpha(coreColor, (int) (EspColor.alpha(coreColor) * boltAlpha));

            java.util.List<Vec3> pts = bolt.points;
            int n = pts.size();
            for (int j = 0; j < n; j++) {
                Vec3 pt = pts.get(j);
                float taper = 1.0f - (float) j / (float) Math.max(1, n - 1) * 0.40f;
                float pSize = 0.075f * taper * style.size();

                billboards.drawBillboard(BLOOM, pt.x, pt.y, pt.z, pSize * 2.2f, pSize * 2.2f, boltGlow, 0.0f);
                billboards.drawBillboard(BLOOM, pt.x, pt.y, pt.z, pSize * 1.3f, pSize * 1.3f, boltOuter, 0.0f);
                billboards.drawBillboard(FIRE, pt.x, pt.y, pt.z, pSize * 0.65f, pSize * 0.65f, boltCore, 0.0f);

                if (j < n - 1) {
                    Vec3 next = pts.get(j + 1);
                    Vec3 mid1 = pt.lerp(next, 0.333);
                    Vec3 mid2 = pt.lerp(next, 0.666);
                    billboards.drawBillboard(BLOOM, mid1.x, mid1.y, mid1.z, pSize * 1.5f, pSize * 1.5f, boltGlow, 0.0f);
                    billboards.drawBillboard(FIRE, mid1.x, mid1.y, mid1.z, pSize * 0.45f, pSize * 0.45f, boltCore, 0.0f);
                    billboards.drawBillboard(BLOOM, mid2.x, mid2.y, mid2.z, pSize * 1.5f, pSize * 1.5f, boltGlow, 0.0f);
                    billboards.drawBillboard(FIRE, mid2.x, mid2.y, mid2.z, pSize * 0.45f, pSize * 0.45f, boltCore, 0.0f);
                }
            }

            if (!pts.isEmpty()) {
                Vec3 tip = pts.get(n - 1);
                float tipSize = (0.12f + fade * 0.06f) * style.size();
                billboards.drawBillboard(BLOOM, tip.x, tip.y, tip.z, tipSize * 1.8f, tipSize * 1.8f, boltGlow, 0.0f);
                billboards.drawBillboard(FIRE, tip.x, tip.y, tip.z, tipSize * 0.75f, tipSize * 0.75f, boltCore, 0.0f);
            }
        }
    }

    private LightningBolt spawnLightningBolt(LivingEntity target, Vec3 basePos, Style style) {
        double width = target.getBoundingBox().getXsize();
        double height = target.getBoundingBox().getYsize();
        double surfaceRadius = (width * 0.5 + 0.05) * style.size();

        double angle = Math.random() * Math.PI * 2.0;
        double h = Math.random() * height;

        Vec3 start = basePos.add(Math.cos(angle) * surfaceRadius, h, Math.sin(angle) * surfaceRadius);

        double theta = Math.random() * Math.PI * 2.0;
        double phi = Math.toRadians((Math.random() - 0.5) * 140.0);
        double dx = Math.cos(phi) * Math.cos(theta);
        double dy = Math.sin(phi);
        double dz = Math.cos(phi) * Math.sin(theta);

        double spikeLength = (0.28 + Math.random() * 0.35) * style.size();
        Vec3 end = start.add(dx * spikeLength, dy * spikeLength, dz * spikeLength);

        java.util.List<Vec3> points = generateLightningPath(start, end, 3, spikeLength * 0.4);

        LightningBolt bolt = new LightningBolt();
        bolt.points = points;
        bolt.spawnTime = System.currentTimeMillis();
        bolt.lifetimeMs = (int) ((260 + Math.random() * 240) / Math.max(0.3f, style.speed()));
        return bolt;
    }

    private java.util.List<Vec3> generateLightningPath(Vec3 start, Vec3 end, int depth, double maxOffset) {
        if (depth <= 0) {
            java.util.List<Vec3> result = new java.util.ArrayList<>(2);
            result.add(start);
            result.add(end);
            return result;
        }

        Vec3 dir = end.subtract(start);
        Vec3 mid = start.add(dir.scale(0.5));
        Vec3 perp = randomPerpendicular(dir);
        double offset = (Math.random() - 0.5) * 2.0 * maxOffset;
        Vec3 displacedMid = mid.add(perp.scale(offset));

        java.util.List<Vec3> left = generateLightningPath(start, displacedMid, depth - 1, maxOffset * 0.5);
        java.util.List<Vec3> right = generateLightningPath(displacedMid, end, depth - 1, maxOffset * 0.5);
        left.remove(left.size() - 1);
        left.addAll(right);
        return left;
    }

    private Vec3 randomPerpendicular(Vec3 v) {
        Vec3 randomVec = new Vec3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
        if (randomVec.lengthSqr() < 1e-4) randomVec = new Vec3(0, 1, 0);
        Vec3 perp = v.cross(randomVec);
        if (perp.lengthSqr() < 1e-4) {
            perp = v.cross(new Vec3(0, 1, 0));
        }
        return perp.normalize();
    }
}
