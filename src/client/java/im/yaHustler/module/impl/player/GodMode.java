package im.yaHustler.module.impl.player;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import im.yaHustler.font.Fonts;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.MoveUtil;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventPacketReceive;
import im.yaHustler.port.event.EventPacketSend;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id = "god_mode", name = "GodMode", category = Category.PLAYER, description = "Режим бессмертия с десинхронизацией позиции")
public class GodMode extends PortModule {
    private static final long WAIT_MS = 7000L;
    private static final float SPEED = 1.3F;

    private RemotePlayer fakePlayer;
    private long enableTime;
    private Vec3 origin;
    private boolean keepGhost;
    private ClientLevel lastWorld;

    private float savedHealth = 20.0f;
    private float savedAbsorption = 0.0f;

    private boolean wasUsingChorus;
    private boolean waitingForChorusTeleport;
    private long chorusFinishTime;

    public void stopGodMode(boolean keepGhost) {
        this.keepGhost = keepGhost;
        setEnabled(false);
        this.keepGhost = false;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        enableTime = System.currentTimeMillis();
        origin = mc.player.position();
        lastWorld = mc.level;
        savedHealth = mc.player.getHealth();
        savedAbsorption = mc.player.getAbsorptionAmount();
        wasUsingChorus = false;
        waitingForChorusTeleport = false;
        chorusFinishTime = 0L;
        spawnFakePlayer();
    }

    @Override
    protected void onDisable() {
        if (fakePlayer != null) {
            ClientLevel world = mc.level != null ? mc.level : lastWorld;
            fakePlayer.discard();
            if (world != null) {
                world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            }
            fakePlayer = null;
        }

        if (mc.player != null && !keepGhost && origin != null && (mc.level == lastWorld || mc.level != null)) {
            mc.player.snapTo(origin.x, origin.y, origin.z, mc.player.getYRot(), mc.player.getXRot());
            mc.player.setDeltaMovement(Vec3.ZERO);
        }

        wasUsingChorus = false;
        waitingForChorusTeleport = false;
        chorusFinishTime = 0L;
        origin = null;
        lastWorld = null;
    }

    private void spawnFakePlayer() {
        if (fakePlayer != null || mc.level == null || mc.player == null || origin == null) return;

        String name = mc.player.getName().getString();
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
        fakePlayer = new RemotePlayer(mc.level, profile);
        fakePlayer.setId(-1338);
        fakePlayer.setPos(origin.x, origin.y, origin.z);
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setYHeadRot(mc.player.getYRot());
        fakePlayer.setHealth(fakePlayer.getMaxHealth());
        fakePlayer.setNoGravity(true);
        fakePlayer.setDeltaMovement(Vec3.ZERO);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fakePlayer.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        }

        mc.level.addEntity(fakePlayer);
    }

    @Override
    public void tick(Minecraft client) {
        if (!enabled()) return;

        if (client.player == null || client.level == null || client.level != lastWorld) {
            stopGodMode(false);
            return;
        }

        long elapsed = System.currentTimeMillis() - enableTime;
        if (elapsed >= WAIT_MS) {
            stopGodMode(false);
            return;
        }

        if (client.player.getHealth() < savedHealth) {
            client.player.setHealth(savedHealth);
        }
        if (client.player.getAbsorptionAmount() < savedAbsorption) {
            client.player.setAbsorptionAmount(savedAbsorption);
        }
        client.player.hurtTime = 0;
        client.player.hurtDuration = 0;

        client.player.removeEffect(MobEffects.POISON);
        client.player.removeEffect(MobEffects.WITHER);
        client.player.removeEffect(MobEffects.SLOWNESS);
        client.player.removeEffect(MobEffects.BLINDNESS);
        client.player.removeEffect(MobEffects.DARKNESS);
        client.player.removeEffect(MobEffects.WEAKNESS);
        client.player.removeEffect(MobEffects.NAUSEA);
        client.player.removeEffect(MobEffects.MINING_FATIGUE);
        client.player.removeEffect(MobEffects.LEVITATION);
        client.player.removeEffect(MobEffects.INSTANT_DAMAGE);
        client.player.clearFire();
        client.player.setRemainingFireTicks(0);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Projectile projectile && projectile.getOwner() != client.player) {
                double distPlayer = projectile.distanceToSqr(client.player);
                double distFake = fakePlayer != null ? projectile.distanceToSqr(fakePlayer) : Double.MAX_VALUE;
                if (distPlayer < 36.0 || distFake < 36.0) {
                    projectile.setDeltaMovement(0, -10, 0);
                    projectile.discard();
                }
            }
        }

        if (fakePlayer != null && origin != null) {
            fakePlayer.setPos(origin.x, origin.y, origin.z);
            fakePlayer.setDeltaMovement(Vec3.ZERO);
            fakePlayer.setNoGravity(true);
        }

        if (SPEED > 1.0f && MoveUtil.isMoving()) {
            Vec3 vel = client.player.getDeltaMovement();
            client.player.setDeltaMovement(vel.x * SPEED, vel.y, vel.z * SPEED);
        }

        if (client.options.keyAttack.isDown()) {
            Vec3 eye = client.player.getEyePosition();
            Vec3 look = client.player.calculateViewVector(client.player.getXRot(), client.player.getYRot());
            Vec3 end = eye.add(look.scale(5.2));
            AABB box = client.player.getBoundingBox().expandTowards(look.scale(5.2)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                client.player,
                eye,
                end,
                box,
                e -> e != client.player && e != fakePlayer && e instanceof LivingEntity && !e.isSpectator() && e.isAlive(),
                5.2 * 5.2
            );

            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                if (client.player.getAttackStrengthScale(0.5f) >= 0.85f && client.gameMode != null) {
                    client.gameMode.attack(client.player, target);
                    client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                }
                client.options.keyAttack.setDown(false);
            }
        }

        boolean isUsingChorusNow = client.player.isUsingItem() && client.player.getUseItem().is(Items.CHORUS_FRUIT);
        boolean isChorusCoolingDown = client.player.getCooldowns().isOnCooldown(Items.CHORUS_FRUIT.getDefaultInstance());

        if (wasUsingChorus && (isChorusCoolingDown || !client.player.isUsingItem())) {
            if (isChorusCoolingDown) {
                waitingForChorusTeleport = true;
                chorusFinishTime = System.currentTimeMillis();
            }
        }
        wasUsingChorus = isUsingChorusNow;

        if (waitingForChorusTeleport && (System.currentTimeMillis() - chorusFinishTime > 600L)) {
            stopGodMode(true);
        }
    }

    @Override
    public void onPacketSend(EventPacketSend event) {
        if (!enabled() || mc.player == null) return;

        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            event.cancel();
        }
    }

    @Override
    public void onPacket(EventPacketReceive event) {
        if (!enabled() || mc.player == null) return;

        Packet<?> p = event.getPacket();

        if (p instanceof ClientboundSetEntityMotionPacket velocityPacket) {
            if (velocityPacket.id() == mc.player.getId()) {
                event.cancel();
                return;
            }
        }

        if (p instanceof ClientboundExplodePacket || p instanceof ClientboundHurtAnimationPacket) {
            event.cancel();
            return;
        }

        if (p instanceof ClientboundEntityEventPacket status) {
            Entity targetEntity = status.getEntity(mc.level);
            if (targetEntity == mc.player || (fakePlayer != null && targetEntity == fakePlayer)) {
                byte s = status.getEventId();
                if (s == 2 || s == 3 || s == 33) {
                    event.cancel();
                    return;
                }
            }
        }

        if (p instanceof ClientboundDamageEventPacket damagePacket) {
            if (damagePacket.entityId() == mc.player.getId() || (fakePlayer != null && damagePacket.entityId() == fakePlayer.getId())) {
                event.cancel();
                return;
            }
        }

        if (p instanceof ClientboundSetHealthPacket healthPacket) {
            if (healthPacket.getHealth() < savedHealth) {
                event.cancel();
                return;
            }
        }

        if (p instanceof ClientboundUpdateMobEffectPacket effectPacket) {
            if (effectPacket.getEntityId() == mc.player.getId()) {
                event.cancel();
                return;
            }
        }

        if (p instanceof ClientboundPlayerPositionPacket) {
            if (waitingForChorusTeleport) {
                waitingForChorusTeleport = false;
                mc.execute(() -> stopGodMode(true));
                return;
            }
            event.cancel();
        }
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!enabled() || mc.player == null || origin == null) return;

        long elapsed = System.currentTimeMillis() - enableTime;
        long remaining = WAIT_MS - elapsed;
        if (remaining <= 0) return;

        int seconds = (int) Math.ceil(remaining / 1000.0);
        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2.0F - 30.0F;
        String text = "Ждите " + seconds + " секунд";
        float scale = 1.0F;
        float textWidth = Fonts.width(text, scale);
        Fonts.draw(graphics, text, centerX - (textWidth / 2.0F), centerY, -1, scale);
    }
}
