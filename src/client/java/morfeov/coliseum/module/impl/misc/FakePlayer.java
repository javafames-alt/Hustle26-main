package morfeov.coliseum.module.impl.misc;

import com.mojang.authlib.GameProfile;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(id = "fakeplayer", name = "FakePlayer", category = Category.MISC,
    description = "Создаёт локального манекена игрока")
public final class FakePlayer extends Module {
    private static final UUID FAKE_UUID = UUID.fromString("66123666-6666-6666-6666-666666666600");

    private final BooleanSetting copyInventory = add(new BooleanSetting("Copy Inventory", false));
    private final BooleanSetting record = add(new BooleanSetting("Record Movement", false));
    private final BooleanSetting play = add(new BooleanSetting("Playback", false));
    private final BooleanSetting autoTotem = add(new BooleanSetting("Auto Totem", false));
    private final ModeSetting behavior = add(new ModeSetting("Behavior", "Normal", "Normal", "Eating"));

    private RemotePlayer dummy;
    private final List<PlayerState> positions = new ArrayList<>();
    private int movementTick;
    private int deathTime;
    private boolean eating;
    private ItemStack savedMainHand = ItemStack.EMPTY;

    public Entity dummy() { return dummy; }

    @Override
    protected void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            setEnabled(false);
            return;
        }
        removeDummy(client);
        movementTick = 0;
        deathTime = 0;
        dummy = new EatingRemotePlayer(client.level, new GameProfile(FAKE_UUID, "FakePlayer"));
        dummy.setId(661236);
        dummy.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        dummy.setYRot(client.player.getYRot());
        dummy.setXRot(client.player.getXRot());
        dummy.setYHeadRot(client.player.getYHeadRot());
        dummy.setHealth(client.player.getHealth());
        dummy.setAbsorptionAmount(client.player.getAbsorptionAmount());
        dummy.setInvisible(false);
        if (copyInventory.enabled()) {
            for (int slot = 0; slot < 41; slot++) {
                dummy.getInventory().setItem(slot, client.player.getInventory().getItem(slot).copy());
            }
            dummy.setItemInHand(InteractionHand.MAIN_HAND, client.player.getMainHandItem().copy());
            dummy.setItemInHand(InteractionHand.OFF_HAND, client.player.getOffhandItem().copy());
        }
        dummy.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 9999, 2));
        dummy.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 9999, 4));
        dummy.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 9999, 1));
        client.level.addEntity(dummy);
        updateEating();
    }

    @Override
    protected void onDisable() {
        removeDummy(Minecraft.getInstance());
        positions.clear();
        movementTick = 0;
        deathTime = 0;
    }

    @Override
    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (client.level == null || player == null || dummy == null) return;
        if (record.enabled()) {
            positions.add(new PlayerState(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
        } else if (play.enabled() && !positions.isEmpty()) {
            movementTick = (movementTick + 1) % positions.size();
            PlayerState state = positions.get(movementTick);
            dummy.setPos(state.x(), state.y(), state.z());
            dummy.setYRot(state.yaw());
            dummy.setXRot(state.pitch());
            dummy.setYHeadRot(state.yaw());
        } else {
            movementTick = 0;
        }
        if (autoTotem.enabled() && !dummy.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            dummy.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        updateEating();
        if (!dummy.isAlive()) {
            if (++deathTime > 10) setEnabled(false);
        } else {
            deathTime = 0;
        }
    }

    public void onAttacked(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (dummy == null || player == null || entity != dummy || dummy.hurtTime != 0) return;
        player.playSound(SoundEvents.PLAYER_HURT, 1.0f, 1.0f);
        ItemStack weapon = player.getMainHandItem();
        boolean sword = weapon.getItem().toString().toLowerCase(java.util.Locale.ROOT).contains("sword");
        float damage = weapon.is(ItemTags.AXES) ? 9.0f : sword ? 7.0f : 1.0f;
        if (player.fallDistance > 0.0f) {
            player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
            damage *= 1.5f;
        }
        MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
        if (strength != null) damage += 3.0f * (strength.getAmplifier() + 1);
        damage(damage);
    }

    private void damage(float amount) {
        if (dummy == null) return;
        float absorption = dummy.getAbsorptionAmount();
        float absorbed = Math.min(absorption, amount);
        dummy.setAbsorptionAmount(absorption - absorbed);
        dummy.setHealth(dummy.getHealth() - (amount - absorbed));
        dummy.hurtTime = 10;
        if (dummy.getHealth() <= 0.0f && useTotem()) {
            dummy.setHealth(10.0f);
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) client.level.broadcastEntityEvent(dummy, (byte) 35);
        }
    }

    private boolean useTotem() {
        if (dummy == null) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = dummy.getItemInHand(hand);
            if (!stack.is(Items.TOTEM_OF_UNDYING)) continue;
            stack.shrink(1);
            dummy.removeAllEffects();
            dummy.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            dummy.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            dummy.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            return true;
        }
        return false;
    }

    private void updateEating() {
        if (!(dummy instanceof EatingRemotePlayer eatingDummy)) return;
        if (!behavior.is("Eating") || !eatingDummy.isAlive()) {
            stopEating();
            return;
        }
        if (!eating) {
            savedMainHand = eatingDummy.getMainHandItem().copy();
            eating = true;
            eatingDummy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        }
        if (!eatingDummy.isUsingItem() || eatingDummy.getUseItemRemainingTicks() <= 1
            || !eatingDummy.getMainHandItem().is(Items.GOLDEN_APPLE)) {
            eatingDummy.endEating();
            eatingDummy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            eatingDummy.beginEating();
        }
    }

    private void stopEating() {
        if (eating && dummy instanceof EatingRemotePlayer eatingDummy) {
            eatingDummy.endEating();
            eatingDummy.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand.copy());
        }
        eating = false;
        savedMainHand = ItemStack.EMPTY;
    }

    private void removeDummy(Minecraft client) {
        stopEating();
        if (dummy == null) return;
        if (client.level != null) client.level.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
        else dummy.setRemoved(Entity.RemovalReason.DISCARDED);
        dummy = null;
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {}

    private static final class EatingRemotePlayer extends RemotePlayer {
        private EatingRemotePlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }

        private void beginEating() {
            startUsingItem(InteractionHand.MAIN_HAND);
            setLivingEntityFlag(2, false);
            setLivingEntityFlag(1, true);
        }

        private void endEating() {
            stopUsingItem();
            setLivingEntityFlag(1, false);
            setLivingEntityFlag(2, false);
        }
    }
}
