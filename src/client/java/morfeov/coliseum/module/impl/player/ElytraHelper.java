package morfeov.coliseum.module.impl.player;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.KeySetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id = "elytra_helper", name = "ElytraHelper", description = "Смена элитры, фейерверк и автоматический взлёт", category = Category.PLAYER)
public final class ElytraHelper extends Module {
    private static ElytraHelper instance;
    public final BooleanSetting autoTakeoff = add(new BooleanSetting("Авто-взлёт", false));
    public final ModeSetting mode = add(new ModeSetting("Режим взлёта", "Легитный", "Легитный", "Рейджовый").visibleWhen(autoTakeoff::enabled));
    public final KeySetting swapKey = add(new KeySetting("Кнопка свапа элитры"));
    public final KeySetting fireworkKey = add(new KeySetting("Кнопка фейерверка"));
    public final BooleanSetting legitSwap = add(new BooleanSetting("Легитный свап", false));
    public final BooleanSetting legitUse = add(new BooleanSetting("Легитный фейерверк", false));
    public final BooleanSetting levitation = add(new BooleanSetting("Взлетать с левитацией", false).visibleWhen(autoTakeoff::enabled));
    public final BooleanSetting autoFirework = add(new BooleanSetting("Авто фейерверк", false));
    public final BooleanSetting reallyWorldBypass = add(new BooleanSetting("Обход ReallyWorld", false));
    private long lastFirework;
    private int swapPhaseTicks;
    private int cooldown;
    private int airTicks;
    private int jumpDelay;
    private int fallFlyingTicks;
    private int failedTakeoffAttempts;
    private boolean prevFallFlying;
    private boolean swapPending;
    private boolean fireworkPending;

    public ElytraHelper() {
        instance = this;
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
        airTicks = 0;
        jumpDelay = 0;
        fallFlyingTicks = 0;
        failedTakeoffAttempts = 0;
        prevFallFlying = false;
        swapPending = false;
        fireworkPending = false;
        swapPhaseTicks = 0;
        lastFirework = 0;
    }

    @Override
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            onDisable();
            return;
        }

        if (cooldown > 0) {
            cooldown--;
        }

        if (swapPhaseTicks > 0) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            player.setSprinting(false);
            if (--swapPhaseTicks == 3) swap(mc);
        }

        boolean flying = player.isFallFlying();
        if (prevFallFlying && !flying) {
            failedTakeoffAttempts = fallFlyingTicks < 10 ? failedTakeoffAttempts + 1 : 0;
        }
        fallFlyingTicks = flying ? fallFlyingTicks + 1 : 0;
        prevFallFlying = flying;

        if (swapKey.pressed(mc)) {
            swapPending = true;
        }
        if (fireworkKey.pressed(mc)) {
            fireworkPending = true;
        }

        if (swapPending && cooldown == 0 && swapPhaseTicks == 0) {
            swapPending = false;
            if (legitSwap.enabled() || reallyWorldBypass.enabled()) swapPhaseTicks = 5;
            else swap(mc);
        }

        if (fireworkPending) {
            fireworkPending = false;
            firework(mc);
        }

        if (autoTakeoff.enabled() && autoFirework.enabled() && flying && !player.isUsingItem()
                && System.currentTimeMillis() - lastFirework >= 1200) firework(mc);

        if (autoTakeoff.enabled() && mode.is("Рейджовый") && canTakeoff(mc) && cooldown == 0 && airTicks >= 1 && player.tryToStartFallFlying()) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    public static void afterInput(ClientInput input) {
        ElytraHelper helper = instance;
        Minecraft mc = Minecraft.getInstance();
        if (helper != null && helper.enabled() && helper.swapPhaseTicks > 0) {
            Input keys = input.keyPresses;
            input.keyPresses = new Input(false, false, false, false, false, keys.shift(), false);
            ((morfeov.coliseum.mixin.ClientInputAccessor) input).coliseum$setMoveVector(net.minecraft.world.phys.Vec2.ZERO);
            return;
        }
        if (helper == null || !helper.enabled() || !helper.autoTakeoff.enabled() || !helper.canTakeoff(mc) || helper.cooldown > 0) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (player.onGround()) {
            helper.airTicks = 0;
            helper.jumpDelay = 2;
            helper.failedTakeoffAttempts = 0;
            if (!input.keyPresses.jump()) {
                input.makeJump();
            }
            return;
        }

        helper.airTicks++;

        if (helper.mode.is("Рейджовый")) {
            if (player.tryToStartFallFlying()) {
                player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
            return;
        }

        if (helper.jumpDelay < 2) {
            helper.jumpDelay++;
        }

        boolean lastSentJump = player.getLastSentInput().jump();
        if (helper.canTakeoffAir(player) && helper.jumpDelay >= 2) {
            if (lastSentJump) {
                input.keyPresses = withJump(input.keyPresses, false);
            } else {
                input.makeJump();
                helper.jumpDelay = 0;
            }
        } else if (lastSentJump) {
            input.keyPresses = withJump(input.keyPresses, false);
        }
    }

    private static Input withJump(Input keys, boolean jump) {
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), jump, keys.shift(), keys.sprint());
    }

    private void swap(Minecraft mc) {
        if (!InventoryActions.ready(mc)) {
            return;
        }
        int slot;
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            slot = InventoryActions.find(mc, Items.NETHERITE_CHESTPLATE);
            if (slot < 0) {
                slot = InventoryActions.find(mc, Items.DIAMOND_CHESTPLATE);
            }
            if (slot < 0) {
                slot = InventoryActions.find(mc, stack -> {
                    var equip = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                    return !stack.is(Items.ELYTRA) && equip != null && equip.slot() == EquipmentSlot.CHEST;
                });
            }
            if (slot < 0) slot = InventoryActions.emptySlot(mc);
        } else {
            slot = InventoryActions.find(mc, Items.ELYTRA);
        }
        if (slot < 0) {
            return;
        }
        if (legitSwap.enabled()) {
            InventoryActions.swapEquipment(mc, slot, 6);
        } else {
            InventoryActions.swapEquipmentFast(mc, slot, 6);
        }
        cooldown = 5;
    }

    private void firework(Minecraft mc) {
        if (mc.player != null && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && System.currentTimeMillis() - lastFirework >= 400) {
            if (InventoryActions.use(mc, Items.FIREWORK_ROCKET, legitUse.enabled())) lastFirework = System.currentTimeMillis();
        }
    }

    public boolean isAutoTakeoffActive() {
        return enabled() && autoTakeoff.enabled() && canTakeoff(Minecraft.getInstance());
    }

    private boolean canTakeoff(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !InventoryActions.ready(mc) || player.isFallFlying() || player.isSpectator() || player.getAbilities().flying) {
            return false;
        }
        if (player.isPassenger() || player.onClimbable() || player.isInWater()) {
            return false;
        }
        if (!levitation.enabled() && player.hasEffect(MobEffects.LEVITATION)) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean canTakeoffAir(LocalPlayer player) {
        return airTicks >= 1 && failedTakeoffAttempts < 3 && !isCollidingDownwards(player) && hasSpaceBelow(player);
    }

    private boolean isCollidingDownwards(LocalPlayer player) {
        Vec3 delta = player.getDeltaMovement();
        if (delta.y >= 0.0) {
            return false;
        }
        AABB box = player.getBoundingBox().move(delta.x, delta.y, delta.z).expandTowards(0.0, -0.05, 0.0);
        return !player.level().noCollision(player, box);
    }

    private boolean hasSpaceBelow(LocalPlayer player) {
        return player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, -0.6, 0.0));
    }
}
