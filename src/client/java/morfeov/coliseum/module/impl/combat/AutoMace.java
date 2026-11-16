package morfeov.coliseum.module.impl.combat;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.mixin.CombatGameModeAccessor;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.combat.aura.AttackAura;
import morfeov.coliseum.module.impl.combat.aura.CriticalUtils;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.NumberSetting;
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
        var aura = Coliseum.MODULES.get(AttackAura.class);
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
            ((CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
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
            ((CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
        }
        previousSlot = maceSlot = -1;
        active = false;
    }

    @Override protected void onDisable() { restore(Minecraft.getInstance()); }
}
