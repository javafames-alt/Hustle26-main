package im.yaHustler.module.impl.combat.aura.rotation;

import java.util.Random;
import java.util.regex.Pattern;
import im.yaHustler.mixin.BossHealthOverlayAccessor;
import im.yaHustler.module.impl.combat.aura.AimPart;
import im.yaHustler.module.impl.combat.aura.AttackAura;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;

public final class SpookyTimeRotation {
    private static final SpookyTimeRotation INSTANCE = new SpookyTimeRotation();
    private static final Pattern BOSS_TIMER = Pattern.compile("(\\d+)\\s*сек");
    private final Random random = new Random();
    private final SpookyMotion motion = new SpookyMotion(random);
    private LivingEntity tracked;
    private int lastTick = Integer.MIN_VALUE;
    public static SpookyTimeRotation getInstance() { return INSTANCE; }
    public void reset() { tracked = null; lastTick = Integer.MIN_VALUE; motion.reset(); }

    public Vec3 aimPoint(LivingEntity target) {
        var mc = Minecraft.getInstance();
        if (target == null || mc.player == null) return Vec3.ZERO;
        boolean mace = mace(mc);
        return target.position().add(mace ? 0 : motion.offsetX(),
            Mth.clamp(target.getBbHeight() * (mace ? 1 : motion.height()), .1, Math.max(.1, target.getBbHeight() - .1)),
            mace ? 0 : motion.offsetZ());
    }

    public float[] step(AimPart observation, float gcd) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || observation == null || observation.target == null) { reset(); return new float[2]; }
        if (tracked != observation.target || mc.player.tickCount < lastTick) { reset(); tracked = observation.target; }
        if (lastTick == mc.player.tickCount) return new float[2];
        lastTick = mc.player.tickCount;
        var player = mc.player;
        var aura = AttackAura.getInstance();
        boolean ready = aura != null && aura.rotationAttackReady();
        if (arenaOrBoss(mc)) return arenaStep(observation, gcd, ready);
        Vec3 eye = player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        Vec3 direction = aimPoint(observation.target).subtract(eye).normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = Mth.clamp((float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))), -90, 90);
        var box = observation.target.getBoundingBox();
        Vec3 currentEye = player.getEyePosition();
        boolean ray = box.contains(currentEye) || box.clip(currentEye, currentEye.add(player.getViewVector(1).scale(observation.attackRange + 1))).isPresent();
        return motion.step(System.currentTimeMillis(), player.getYRot(), player.getXRot(), yaw, pitch,
            ready && observation.distance < observation.attackRange, mace(mc), ray,
            box.intersects(player.getBoundingBox()), player.isSwinging() ? 1 : 0, gcd);
    }

    private boolean mace(Minecraft mc) {
        return mc.player.getMainHandItem().is(Items.MACE) && !mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava();
    }

    private boolean arenaOrBoss(Minecraft mc) {
        var objective = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective != null) {
            String[] parts = objective.getDisplayName().getString().split("-");
            if (parts.length > 1 && parts[1].replaceAll("\\D", "").matches("70[24]")) return true;
        }
        if (!mc.level.dimension().identifier().getPath().equals("arenas")) return false;
        for (var boss : ((BossHealthOverlayAccessor)mc.gui.hud.getBossOverlay()).hustle263$events().values()) {
            var match = BOSS_TIMER.matcher(boss.getName().getString().replaceAll("(?i)§[0-9a-fk-or]", ""));
            if (boss.getProgress() > .05f && match.find()) {
                try { if (Integer.parseInt(match.group(1)) / boss.getProgress() < 60) return true; }
                catch (NumberFormatException ignored) { /* Ignore malformed server timer text. */ }
            }
        }
        return false;
    }

    private float[] arenaStep(AimPart o, float gcd, boolean ready) {
        var player = Minecraft.getInstance().player;
        var target = o.target; var box = target.getBoundingBox(); Vec3 eye = player.getEyePosition();
        Vec3 velocity = new Vec3(target.getX() - target.xOld, 0, target.getZ() - target.zOld)
            .subtract(player.getDeltaMovement().x, 0, player.getDeltaMovement().z).scale(1.5);
        Vec3 predicted = new Vec3(box.getCenter().x, box.minY + box.getYsize() * .82, box.getCenter().z).add(velocity).subtract(eye);
        Vec3 upper = new Vec3(box.getCenter().x, box.minY + box.getYsize() * .87, box.getCenter().z).subtract(eye);
        boolean near = predicted.x * predicted.x + predicted.z * predicted.z < .03;
        double distance = Math.max(.05, Math.hypot(predicted.x, predicted.z));
        float scale = (float)Mth.clamp(distance / 2, .7, 1);
        float yawError = near ? 0 : Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-predicted.x, predicted.z)) - player.getYRot());
        float pitchError = (float)-Math.toDegrees(Math.atan2(upper.y, Math.max(.7, Math.hypot(upper.x, upper.z)))) - player.getXRot();
        float angularWidth = (float)Math.toDegrees(Math.atan2(box.getXsize() * .5, distance));
        float cooldown = player.getAttackStrengthScale(.5f);
        boolean imminent = ready || cooldown >= .9f;
        float wobble = imminent ? 0 : .75f + .45f * cooldown;
        double time = System.currentTimeMillis() / 60.0;
        float noiseYaw = ((float)(Math.cos(time * .7) + Math.sin(time * 1.7) * .5) * (angularWidth * 1.8f + 7) + range(-4, 4)) * wobble;
        float noisePitch = ((float)(Math.sin(time * .55) + Math.cos(time * 1.3) * .4) * 6 + range(-2, 2)) * wobble;
        float gain = Mth.clamp((imminent ? range(.85f, 1) : range(.55f, .85f) * (1 - cooldown * .35f))
            * (1 + Math.min(Math.abs(yawError), 90) / 300) * scale, .1f, 1);
        float cap = range(55, 80) * scale;
        float dy = Mth.clamp((yawError + noiseYaw) * gain, -cap, cap);
        if (ready && !box.contains(eye) && box.clip(eye, eye.add(Vec3.directionFromRotation(player.getXRot(), player.getYRot() + dy).scale(o.attackRange + 1))).isEmpty()) dy = yawError;
        float dp = imminent ? pitchError * range(.8f, .95f) : noisePitch * .35f + pitchError * (Math.abs(pitchError) > 18 ? .3f : .15f);
        if (!ready && imminent) { dy *= .35f; dp *= .35f; }
        float desiredPitch = Mth.clamp(player.getXRot() + dp, -89, 89);
        return new float[]{SpookyMotion.quantize(Mth.clamp(Mth.wrapDegrees(dy), near ? -8 : -35, near ? 8 : 35), gcd),
            Mth.clamp(player.getXRot() + SpookyMotion.quantize(Mth.clamp(desiredPitch - player.getXRot(), -22, 22), gcd), -90, 90) - player.getXRot()};
    }
    private float range(float min, float max) { return min + random.nextFloat() * (max - min); }
}
