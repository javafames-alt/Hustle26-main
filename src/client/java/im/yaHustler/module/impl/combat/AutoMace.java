package im.yaHustler.module.impl.combat;

import im.yaHustler.Hustle26;
import im.yaHustler.mixin.CombatGameModeAccessor;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.combat.aura.AttackAura;
import im.yaHustler.module.impl.combat.aura.CriticalUtils;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.setting.NumberSetting;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;

@ModuleInfo(id = "auto_mace", name = "AutoMace", category = Category.COMBAT,
    description = "Переключает булаву перед ударом при падении")
public final class AutoMace extends Module {
    private static final String AURA = "Прогноз для Aura", PIERCING = "Пробитие", SWAP = "Свап";
    private final ModeSetting mode = add(new ModeSetting("Режим", AURA, AURA, PIERCING, SWAP));
    private final NumberSetting holdTicks = add(new NumberSetting("Тики удержания булавы", 2, 1, 5, 1)
        .visibleWhen(() -> mode.is(PIERCING)));
    public final NumberSetting minFall = add(new NumberSetting("Высота падения", 3, 1.5, 30, 0.5));
    public final NumberSetting predictTicks = add(new NumberSetting("Прогноз приземления", 40, 5, 100, 1));
    public final NumberSetting swapRadius = add(new NumberSetting("Радиус смены", 6, 3, 20, 0.5));
    public final BooleanSetting holdAura = add(new BooleanSetting("Задерживать Aura", true));
    public final BooleanSetting swapBack = add(new BooleanSetting("Вернуть предмет", true));
    private int previousSlot = -1;
    private int maceSlot = -1;
    private boolean active;
    private LocalPlayer attackPlayer;
    private int storedSlot = -1, attackSlot = -1, returnAt;
    private ItemStack displaced = ItemStack.EMPTY;

    @Override public void tick(Minecraft mc) {
        active = false;
        restoreAttackSwap(mc, mode.is(AURA));
        if (!mode.is(AURA)) { restore(mc); return; }
        if (mc.player == null || mc.level == null || mc.gameMode == null) { previousSlot = maceSlot = -1; return; }
        var player = mc.player;
        var aura = Hustle26.MODULES.get(AttackAura.class);
        var target = aura == null ? null : aura.getCurrentTarget();
        if (aura == null || !aura.enabled() || target == null || !target.isAlive()
                || player.onGround() || player.isFallFlying() || player.isInWater() || player.onClimbable() || CriticalUtils.isInCobweb(player)
                || player.isPassenger() || player.getAbilities().flying || player.isUsingItem()
                || player.getDeltaMovement().y >= 0 || player.distanceToSqr(target) > swapRadius.get() * swapRadius.get()
                || !CriticalUtils.willLandWithinTicks(player, predictTicks.intValue())) {
            restore(mc);
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).is(Items.MACE)) { slot = i; break; }
        if (slot < 0 || player.fallDistance < minFall.get()) { restore(mc); return; }
        if (previousSlot < 0) previousSlot = player.getInventory().getSelectedSlot();
        maceSlot = slot;
        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            ((CombatGameModeAccessor) mc.gameMode).hustle263$syncSelectedSlot();
        }
        active = true;
    }

    public boolean cancelAttack(Minecraft mc, Entity entity) {
        if (mode.is(AURA) || !(entity instanceof LivingEntity) || !InventoryActions.ready(mc)) return false;
        var player = mc.player;
        if (player.getMainHandItem().is(Items.MACE)) return player.fallDistance < 1.5;
        if (attackPlayer != null || !MaceItem.canSmashAttack(player)) return false;
        int mace = InventoryActions.find(mc, Items.MACE);
        if (mace < 0 || !InvActionUtil.get().tryAcquire(id(), InvActionUtil.PRIORITY_COMBAT, 150)) return false;
        try {
            int selected = player.getInventory().getSelectedSlot();
            ItemStack previous = player.getMainHandItem().copy();
            if (mace < 9) {
                player.getInventory().setSelectedSlot(mace);
                ((CombatGameModeAccessor) mc.gameMode).hustle263$syncSelectedSlot();
            } else {
                mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, mace, selected, ContainerInput.SWAP, player);
            }
            if (player.getMainHandItem().is(Items.MACE) && (swapBack.enabled() || mace >= 9)) {
                attackPlayer = player;
                attackSlot = selected;
                storedSlot = mace;
                displaced = previous;
                returnAt = player.tickCount + (mode.is(PIERCING) ? holdTicks.intValue() : 1);
            }
        } finally {
            InvActionUtil.get().release(id());
        }
        return false;
    }

    public void restoreAttackSwap(Minecraft mc, boolean immediate) {
        if (attackPlayer == null) return;
        if (attackPlayer == mc.player && mc.gameMode != null && attackPlayer.isAlive()) {
            if (!immediate && attackPlayer.tickCount < returnAt) return;
            var inventory = attackPlayer.getInventory();
            if (storedSlot < 9) {
                if (inventory.getSelectedSlot() == storedSlot) {
                    inventory.setSelectedSlot(attackSlot);
                    ((CombatGameModeAccessor) mc.gameMode).hustle263$syncSelectedSlot();
                }
            } else if (inventory.getItem(attackSlot).is(Items.MACE)
                && ItemStack.isSameItemSameComponents(displaced, inventory.getItem(storedSlot))) {
                if (!InventoryActions.ready(mc) || InvActionUtil.get().busy()) return;
                mc.gameMode.handleContainerInput(attackPlayer.inventoryMenu.containerId,
                    storedSlot, attackSlot, ContainerInput.SWAP, attackPlayer);
                if (inventory.getItem(attackSlot).is(Items.MACE)) return;
            }
        }
        attackPlayer = null;
        storedSlot = attackSlot = -1;
        displaced = ItemStack.EMPTY;
    }

    public boolean shouldHoldAttack() {
        var player = Minecraft.getInstance().player;
        return enabled() && active && holdAura.enabled() && player != null
            && !CriticalUtils.isInCobweb(player)
            && !CriticalUtils.willLandWithinTicks(player, 1);
    }

    private void restore(Minecraft mc) {
        if (previousSlot >= 0 && mc.player != null && mc.gameMode != null && swapBack.enabled()
                && mc.player.getInventory().getSelectedSlot() == maceSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            ((CombatGameModeAccessor) mc.gameMode).hustle263$syncSelectedSlot();
        }
        previousSlot = maceSlot = -1;
        active = false;
    }

    @Override protected void onDisable() {
        var client = Minecraft.getInstance();
        restore(client);
        restoreAttackSwap(client, true);
    }
}
