package im.yaHustler.module.impl.combat;

import im.yaHustler.friend.Friends;
import im.yaHustler.mixin.CombatGameModeAccessor;
import im.yaHustler.mixin.LocalPlayerAccessor;
import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.combat.aura.CriticalUtils;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.port.event.EventInput;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ModeSetting;
import im.yaHustler.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(id = "trigger_bot", name = "TriggerBot", category = Category.COMBAT,
    description = "Автоматически атакует цель под прицелом")
public final class TriggerBot extends PortModule {
    private static final String OFF = "Выключены", ALWAYS = "Всегда", WITH_JUMP = "Только с пробелом";
    private final BooleanSetting players = add(new BooleanSetting("Игроки в броне", true));
    private final BooleanSetting unarmored = add(new BooleanSetting("Игроки без брони", true));
    private final BooleanSetting animals = add(new BooleanSetting("Животные", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Мобы", true));
    private final BooleanSetting villagers = add(new BooleanSetting("Жители", true));
    private final BooleanSetting invisible = add(new BooleanSetting("Невидимые цели", false));
    private final BooleanSetting bots = add(new BooleanSetting("Боты", false));
    private final BooleanSetting usingItem = add(new BooleanSetting("Пауза при использовании предмета", false));
    private final BooleanSetting usingShield = add(new BooleanSetting("Пауза со щитом", false));
    private final BooleanSetting mining = add(new BooleanSetting("Пауза при ломании блока", false));
    private final BooleanSetting emptyHand = add(new BooleanSetting("Пауза с пустой рукой", false));
    private final BooleanSetting elytra = add(new BooleanSetting("Пауза в полёте на элитрах", false));
    private final BooleanSetting container = add(new BooleanSetting("Пауза в открытом контейнере", false));
    private final ModeSetting critical = add(new ModeSetting("Критические удары", OFF, OFF, ALWAYS, WITH_JUMP));
    private final ModeSetting sprint = add(new ModeSetting("Режим сброса спринта", "Legit", "Legit", "Rage"));
    private final BooleanSetting breakShield = add(new BooleanSetting("Ломать щит", true));
    private int resetTicks;

    @Override public void tick(Minecraft client) {
        var player = client.player;
        var mode = client.gameMode;
        if (player == null || client.level == null || mode == null || player.isSpectator()
            || !(client.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)
            || target == player || !valid(target) || player.getAttackStrengthScale(.5f) <= .9f
            || (usingItem.enabled() && player.isUsingItem()) || (usingShield.enabled() && player.isBlocking())
            || (mining.enabled() && mode.isDestroying()) || (emptyHand.enabled() && player.getMainHandItem().isEmpty())
            || (elytra.enabled() && player.isFallFlying()) || (container.enabled() && client.gui.screen() != null)) return;
        if (!critical.is(OFF) && (!critical.is(WITH_JUMP) || client.options.keyJump.isDown())
            && CriticalUtils.canPerformCriticalHit(player) && !CriticalUtils.isPlayerInCriticalState(player)) return;
        if (!resetSprint(client)) return;
        if (player.isUsingItem()) mode.releaseUsingItem(player);
        if (breakShield.enabled() && target instanceof Player other && other.isBlocking()
            && other.getUseItem().is(Items.SHIELD) && tryAxe(client, target)) return;
        attack(client, target);
    }

    private boolean valid(LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target instanceof ArmorStand || Friends.contains(target)
            || (target.isInvisible() && !invisible.enabled()) || (!bots.enabled() && AntiBot.rejects(target))) return false;
        if (target instanceof Player player) {
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) return players.enabled();
            }
            return unarmored.enabled();
        }
        if (target instanceof AbstractVillager) return villagers.enabled();
        return target instanceof Animal ? animals.enabled() : target instanceof Mob && mobs.enabled();
    }

    private boolean resetSprint(Minecraft client) {
        var player = client.player;
        var accessor = (LocalPlayerAccessor) player;
        if (!accessor.hustle263$wasSprinting() || player.isUnderWater() || player.isInLava() || player.isSwimming()) return true;
        player.setSprinting(false);
        resetTicks = 2;
        if (sprint.is("Legit")) return false;
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        accessor.hustle263$setWasSprinting(false);
        return true;
    }

    private boolean tryAxe(Minecraft client, LivingEntity target) {
        if (!InventoryActions.ready(client)) return false;
        int axe = InventoryActions.find(client, stack -> stack.is(ItemTags.AXES));
        if (axe < 0) return false;
        var lock = InvActionUtil.get();
        if (!lock.tryAcquire(id(), InvActionUtil.PRIORITY_COMBAT, 150)) return false;
        var player = client.player;
        int selected = player.getInventory().getSelectedSlot();
        var sync = (CombatGameModeAccessor) client.gameMode;
        try {
            if (axe < 9) {
                player.getInventory().setSelectedSlot(axe);
                sync.hustle263$syncSelectedSlot();
            } else {
                client.gameMode.handleContainerInput(player.inventoryMenu.containerId, axe, selected, ContainerInput.SWAP, player);
            }
            if (!player.getMainHandItem().is(ItemTags.AXES)) return false;
            attack(client, target);
            return true;
        } finally {
            if (axe < 9) {
                player.getInventory().setSelectedSlot(selected);
                sync.hustle263$syncSelectedSlot();
            } else if (player.getMainHandItem().is(ItemTags.AXES)) {
                client.gameMode.handleContainerInput(player.inventoryMenu.containerId, axe, selected, ContainerInput.SWAP, player);
            }
            lock.release(id());
        }
    }

    private static void attack(Minecraft client, LivingEntity target) {
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    @Override public void onInput(EventInput event) {
        if (resetTicks == 0) return;
        resetTicks--;
        event.setSprint(false);
    }

    @Override protected void onDisable() { resetTicks = 0; }
    @Override public void onDisconnect(EventDisconnect event) { resetTicks = 0; }
}
