package im.yaHustler.module.impl.combat;

import im.yaHustler.Hustle26;
import im.yaHustler.mixin.CombatGameModeAccessor;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.combat.aura.AttackAura;
import im.yaHustler.module.impl.combat.aura.CriticalUtils;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

@ModuleInfo(id = "auto_mace", name = "AutoMace", category = Category.COMBAT,
    description = "Переключает булаву перед ударом при падении")
public final class AutoMace extends Module {
    public final NumberSetting minFall = add(new NumberSetting("Высота падения", 3, 1.5, 30, 0.5));
    public final NumberSetting predictTicks = add(new NumberSetting("Прогноз приземления", 40, 5, 100, 1));
    public final NumberSetting swapRadius = add(new NumberSetting("Радиус смены", 6, 3, 20, 0.5));
    public final BooleanSetting holdAura = add(new BooleanSetting("Задерживать Aura", true));
    public final BooleanSetting swapBack = add(new BooleanSetting("Вернуть предмет", true));
    private int previousSlot = -1;
    private int maceSlot = -1;
    private boolean active;

    @Override public void tick(Minecraft mc) {
        active = false;
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

    @Override protected void onDisable() { restore(Minecraft.getInstance()); }
}
