package morfeov.coliseum.module.impl.player;


import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.port.event.EventKeyPress;
import morfeov.coliseum.port.event.EventPostMotion;

import morfeov.coliseum.mixin.CombatGameModeAccessor;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.RotationHandler;
import morfeov.coliseum.port.SilentComponent;
import morfeov.coliseum.port.setting.KeySetting;
import morfeov.coliseum.port.setting.SelectSetting;
import morfeov.coliseum.port.InvActionUtil;

import java.util.List;

/** Readable port of Quick's ClickPearl; its unavailable Type743 item-use controller is implemented here. */
@ModuleInfo(id = "click_pearl", name = "ClickPearl", description = "Бросает эндер-жемчуг по отдельному бинду", category = Category.PLAYER)
public final class ClickPearl extends PortModule {
    private static final String INVENTORY_OWNER = "ClickPearl";
    private static final String LEGIT_MODE = "Легитно";
    private static final String RAGE_MODE = "Рейджово";
    private static ClickPearl instance;

    public final SelectSetting mode = add(new SelectSetting("Режим", "Обычный", List.of("Обычный", "Легитный", LEGIT_MODE, RAGE_MODE)));
    private final KeySetting throwBind = add(new KeySetting("Кнопка перла",
            -102));

    public final KeySetting throwKey=throwBind;

    private enum ThrowStage { IDLE, PREPARE_ITEM, AIM, USE_ITEM, RESTORE }
    private ThrowStage stage = ThrowStage.IDLE;
    private boolean throwRequested;
    private boolean rageThrow;
    private boolean ownsRotation;
    private float throwYaw;
    private float throwPitch;
    private InteractionHand throwHand = InteractionHand.MAIN_HAND;
    private LocalPlayer sequencePlayer;
    private int originalSlot = -1;
    private int exchangedInventorySlot = -1;
    private int exchangeHotbarSlot = -1;
    private int restoreAfterTick;
    private int useAfterTick;
    private long deadline;

    public ClickPearl() { instance = this; }
    public static ClickPearl getInstance() { return instance; }
    public boolean isThrowing() { return enabled() && (throwRequested || stage != ThrowStage.IDLE); }
    public void restoreWhileDisabled() { if (!enabled() && stage == ThrowStage.RESTORE) finishThrow(); }

    
    public void onKeyPress(EventKeyPress event) {
        if ((event.getAction() == 1 || event.getAction() == 2) && throwBind.getKeyCode() != 0
                && throwBind.getKeyCode() != -1 && event.getKey() == throwBind.getKeyCode()
                && mc.gui.screen() == null) triggerThrow();
    }

    public void triggerThrow() {
        if (stage == ThrowStage.IDLE) throwRequested = true;
    }

    /** Queue a pearl at the supplied camera angles. Pitch uses Quick's recovered [-89, 89] limits. */
    public boolean tryThrow(float yaw, float pitch) {
        if (!enabled() || stage != ThrowStage.IDLE || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || mc.player == null || mc.level == null || mc.gameMode == null
                || mc.gui.screen() != null || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.isUsingItem()) return false;
        int source = findPearlSlot();
        if (source == -1 || mc.player.getCooldowns().isOnCooldown(pearlStack(source))) return false;
        sequencePlayer = mc.player;
        throwYaw = yaw;
        throwPitch = Mth.clamp(pitch, -89.0f, 89.0f);
        rageThrow = !LEGIT_MODE.equals(mode.getValue()) && !"Легитный".equals(mode.getValue());
        stage = ThrowStage.PREPARE_ITEM;
        deadline = System.currentTimeMillis() + 1500L;
        return true;
    }

    
    public void tick(net.minecraft.client.Minecraft ignored) {
        if (throwBind.pressed(mc)) triggerThrow();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            if (mc.player == sequencePlayer) releaseRotation();
            resetState();
            return;
        }
        if (sequencePlayer != null && sequencePlayer != mc.player) { resetState(); return; }
        if (throwRequested) {
            throwRequested = false;
            float yaw = SilentComponent.isActive() ? SilentComponent.getFreeYaw() : mc.player.getYRot();
            float pitch = SilentComponent.isActive() ? SilentComponent.getFreePitch() : mc.player.getXRot();
            tryThrow(yaw, pitch);
        }
        if (stage == ThrowStage.IDLE) return;
        if (stage == ThrowStage.RESTORE) {
            if (mc.player.tickCount >= restoreAfterTick) finishThrow();
            return;
        }
        if (mc.gui.screen() != null || !mc.player.isAlive() || mc.player.isUsingItem()
                || System.currentTimeMillis() > deadline) { finishThrow(); return; }
        if (!acquireInventory()) return;
        if (stage == ThrowStage.PREPARE_ITEM) {
            if (!prepareItem()) return;
            stage = ThrowStage.AIM;
            useAfterTick = mc.player.tickCount + (rageThrow ? 0 : 2);
            // Legit mode exposes the selected pearl for one full update before use.
            if (!rageThrow) return;
        }
        if (stage == ThrowStage.AIM || stage == ThrowStage.USE_ITEM) {
            if (mc.player.tickCount < useAfterTick) return;
            if (rageThrow) {
                RotationHandler.applyExact(Mth.wrapDegrees(throwYaw - mc.player.getYRot()), throwPitch - mc.player.getXRot());
                ownsRotation = true;
            } else {
                RotationHandler.applyClient(Mth.wrapDegrees(throwYaw - mc.player.getYRot()), throwPitch - mc.player.getXRot());
            }
            stage = ThrowStage.USE_ITEM;
        }
    }

    
    public void onPostMotion(EventPostMotion event) {
        if (stage != ThrowStage.USE_ITEM || mc.player != sequencePlayer || mc.player == null || mc.gameMode == null) return;
        if (!InvActionUtil.get().owns(INVENTORY_OWNER)) return;
        if (Math.abs(Mth.wrapDegrees(throwYaw - mc.player.getYRot())) > 0.01f
                || Math.abs(throwPitch - mc.player.getXRot()) > 0.01f) return;
        ItemStack pearl = throwHand == InteractionHand.OFF_HAND ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
        if (mc.gui.screen() != null || !pearl.is(Items.ENDER_PEARL)
                || mc.player.getCooldowns().isOnCooldown(pearl)) { finishThrow(); return; }
        if (mc.gameMode.useItem(mc.player, throwHand).consumesAction()) mc.player.swing(throwHand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        stage = ThrowStage.RESTORE;
        restoreAfterTick = mc.player.tickCount + (rageThrow ? 0 : 2);
        releaseRotation();
        if (rageThrow) finishThrow();
    }

    private boolean prepareItem() {
        int source = findPearlSlot();
        if (source == -1 || mc.player.getCooldowns().isOnCooldown(pearlStack(source))) {
            finishThrow();
            return false;
        }
        if (source == 40) { throwHand = InteractionHand.OFF_HAND; return true; }
        throwHand = InteractionHand.MAIN_HAND;
        if (source == mc.player.getInventory().getSelectedSlot()) return true;
        if (originalSlot == -1) originalSlot = mc.player.getInventory().getSelectedSlot();
        if (source < 9) return selectSlot(source);
        if (mc.player.containerMenu != mc.player.inventoryMenu) { finishThrow(); return false; }
        exchangeHotbarSlot = originalSlot;
        if (!exchangeSlot(source)) return false;
        // clickSlot can be cancelled by LockSlot; only remember exchanges that actually happened.
        if (!mc.player.getInventory().getItem(exchangeHotbarSlot).is(Items.ENDER_PEARL)) { finishThrow(); return false; }
        exchangedInventorySlot = source;
        return selectSlot(exchangeHotbarSlot);
    }

    private int findPearlSlot() {
        if (mc.player.getOffhandItem().is(Items.ENDER_PEARL)) return 40;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getItem(selected).is(Items.ENDER_PEARL)) return selected;
        for (int slot = 0; slot < 36; slot++)
            if (mc.player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) return slot;
        return -1;
    }

    private ItemStack pearlStack(int slot) {
        return slot == 40 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(slot);
    }

    private boolean acquireInventory() {
        return InvActionUtil.get().tryAcquire(INVENTORY_OWNER, InvActionUtil.PRIORITY_SURVIVAL, 250L);
    }

    private boolean selectSlot(int slot) {
        if (!acquireInventory()) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        ((CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
        return true;
    }

    private boolean exchangeSlot(int inventorySlot) {
        if (mc.player.containerMenu != mc.player.inventoryMenu) return false;
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.container == mc.player.getInventory() && slot.getContainerSlot() == inventorySlot)
                return InvActionUtil.get().clickSlot(INVENTORY_OWNER, InvActionUtil.PRIORITY_SURVIVAL,
                        mc.player.inventoryMenu.containerId, slot.index, exchangeHotbarSlot, ContainerInput.SWAP, 250L);
        }
        return false;
    }

    private void releaseRotation() {
        if (ownsRotation) SilentComponent.clear();
        ownsRotation = false;
    }

    private void finishThrow() {
        releaseRotation();
        stage = ThrowStage.RESTORE;
        if (mc.player == sequencePlayer && mc.player != null && mc.gameMode != null) {
            if (!acquireInventory()) return;
            if (exchangedInventorySlot != -1) {
                if (!exchangeSlot(exchangedInventorySlot)) return;
                exchangedInventorySlot = -1;
            }
            if (originalSlot != -1 && !selectSlot(originalSlot)) return;
        }
        resetState();
    }

    private void resetState() {
        InvActionUtil.get().release(INVENTORY_OWNER);
        stage = ThrowStage.IDLE;
        throwRequested = ownsRotation = false;
        originalSlot = exchangedInventorySlot = exchangeHotbarSlot = -1;
        sequencePlayer = null;
        restoreAfterTick = 0;
    }

    @Override public void onDisable() { throwRequested = false; finishThrow(); super.onDisable(); }
     public void onDisconnect(EventDisconnect event) { releaseRotation(); resetState(); }
}
