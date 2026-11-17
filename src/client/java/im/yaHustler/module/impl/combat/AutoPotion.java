package im.yaHustler.module.impl.combat;

import im.yaHustler.mixin.CombatGameModeAccessor;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.combat.rotation.CombatTagManager;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.rotation.Rotation;
import im.yaHustler.rotation.Rotations;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.util.InventoryActions;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

@ModuleInfo(id = "auto_potion", name = "AutoPotion", category = Category.COMBAT,
    description = "Бросает под ноги зелья скорости, силы и огнестойкости из хотбара")
public final class AutoPotion extends PortModule {
    private final BooleanSetting onlyPvp = add(new BooleanSetting("Только при пвп", true));
    private final BooleanSetting speed = add(new BooleanSetting("Скорость", true));
    private final BooleanSetting strength = add(new BooleanSetting("Сила", true));
    private final BooleanSetting fireResistance = add(new BooleanSetting("Огнестойкость", true));
    private int cooldown;

    @Override public void tick(Minecraft client) {
        if (cooldown > 0) cooldown--;
        if (cooldown > 0 || !InventoryActions.ready(client) || client.level == null || client.player.isUsingItem()
            || (onlyPvp.enabled() && !CombatTagManager.isInCombat())
            || !(client.player.onGround() || !client.level.noCollision(client.player,
                client.player.getBoundingBox().move(0, -.5, 0)))) return;
        var player = client.player;
        var inventory = player.getInventory();
        int previous = inventory.getSelectedSlot();
        boolean aimed = false;
        boolean used = false;
        Set<Holder<MobEffect>> thrown = new HashSet<>();
        var lock = InvActionUtil.get();
        if (!lock.tryAcquire(id(), InvActionUtil.PRIORITY_COMBAT, 150)) return;
        try {
            for (int slot = 0; slot < 9; slot++) {
                var stack = inventory.getItem(slot);
                if (!stack.is(Items.SPLASH_POTION) || player.getCooldowns().isOnCooldown(stack)) continue;
                var contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents == null) continue;
                boolean needed = false;
                for (var effect : contents.getAllEffects()) {
                    if (!thrown.contains(effect.getEffect()) && needs(client, effect.getEffect())) {
                        needed = true;
                        break;
                    }
                }
                if (!needed) continue;
                if (!aimed) {
                    aimed = Rotations.aim(this, new Rotation(player.getYRot(), 90), 360, 0, 10);
                    if (!aimed) break;
                }
                inventory.setSelectedSlot(slot);
                if (client.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()) {
                    used = true;
                    for (var effect : contents.getAllEffects()) thrown.add(effect.getEffect());
                }
            }
        } finally {
            if (inventory.getSelectedSlot() != previous) {
                inventory.setSelectedSlot(previous);
                ((CombatGameModeAccessor) client.gameMode).hustle263$syncSelectedSlot();
            }
            lock.release(id());
        }
        if (used) cooldown = 20;
    }

    private boolean needs(Minecraft client, Holder<MobEffect> effect) {
        return !client.player.hasEffect(effect) && ((speed.enabled() && effect.equals(MobEffects.SPEED))
            || (strength.enabled() && effect.equals(MobEffects.STRENGTH))
            || (fireResistance.enabled() && effect.equals(MobEffects.FIRE_RESISTANCE)));
    }

    @Override protected void onEnable() { cooldown = 0; }
    @Override protected void onDisable() { if (Rotations.owns(this)) Rotations.release(); }
    @Override public void onDisconnect(EventDisconnect event) { cooldown = 0; }
}
