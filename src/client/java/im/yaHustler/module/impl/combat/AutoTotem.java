package im.yaHustler.module.impl.combat;

import im.yaHustler.module.Category;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.InvActionUtil;
import im.yaHustler.port.event.EventPacketReceive;
import im.yaHustler.port.event.EventDisconnect;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageTypes;

@ModuleInfo(id = "auto_totem", name = "AutoTotem", category = Category.COMBAT, description = "Тотем при низком здоровье и угрозе взрыва, с возвратом предмета")
public final class AutoTotem extends PortModule {
    private final NumberSetting health = add(new NumberSetting("Уровень здоровья", 4, 1, 20, .5));
    private final NumberSetting elytraHealth = add(new NumberSetting("Здоровье на элитре", 10, 1, 20, .5));
    private final BooleanSetting absorption = add(new BooleanSetting("Золотые сердца", true));
    private final BooleanSetting crystals = add(new BooleanSetting("Кристаллы", true));
    private final BooleanSetting anchors = add(new BooleanSetting("Якорь", true));
    private final BooleanSetting obsidian = add(new BooleanSetting("Обсидиан", false));
    private final BooleanSetting falling = add(new BooleanSetting("Падение", false));
    private final BooleanSetting tnt = add(new BooleanSetting("Обычный тнт", false));
    private final BooleanSetting minecart = add(new BooleanSetting("Вагонетка с тнт", false));
    private final BooleanSetting tridents = add(new BooleanSetting("Трезубцы", true));
    private final BooleanSetting maces = add(new BooleanSetting("Булавы", true));
    private final BooleanSetting restore = add(new BooleanSetting("Возвращать предмет", false));
    private final NumberSetting returnSpeed = add(new NumberSetting("Скорость обратного свапа", 6, 1, 10, 1));
    private final BooleanSetting preserveHead = add(new BooleanSetting("Не менять шар", false));
    private final NumberSetting headHealth = add(new NumberSetting("Здоровье с шаром", 12, 1, 20, .5));
    private final BooleanSetting stopUsing = add(new BooleanSetting("Отменять использование предмета", false));
    private final BooleanSetting preserveEnchanted = add(new BooleanSetting("Сохранять зачарованный", false));
    private final NumberSetting crystalRange = add(new NumberSetting("Дистанция от кристалла", 8, 1, 8, .5));
    private final NumberSetting anchorRange = add(new NumberSetting("Дистанция до якоря", 6, 1, 10, .5));
    private final NumberSetting cartRange = add(new NumberSetting("Дистанция до вагонетки", 6, 1, 10, .5));
    private final NumberSetting tntRange = add(new NumberSetting("Дистанция до тнт", 10, 3, 50, 1));
    private final NumberSetting tridentRange = add(new NumberSetting("Дистанция до трезубца", 10, 1, 20, 1).visibleWhen(tridents::enabled));
    private final NumberSetting maceRange = add(new NumberSetting("Дистанция до булавы", 10, 1, 20, 1).visibleWhen(maces::enabled));
    private ItemStack previous = ItemStack.EMPTY;
    private int originalSlot = -1;
    private int safeTicks;
    private int explosionTicks;
    private float serverHealth = Float.MAX_VALUE;
    private boolean healthPending;
    private boolean incomingThreat;
    private final BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();

    public boolean reservesOffhand() {
        return enabled() && safeTicks == 0;
    }

    @Override public void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || !player.isAlive()) { reset(); return; }
        float effectiveHealth = healthPending ? Math.min(player.getHealth(), serverHealth) : player.getHealth();
        healthPending = false;
        if (absorption.enabled()) effectiveHealth += player.getAbsorptionAmount();
        boolean protectHead = preserveHead.enabled() && player.getOffhandItem().is(Items.PLAYER_HEAD)
            && effectiveHealth > headHealth.floatValue() && !(falling.enabled() && player.fallDistance > 5);
        boolean danger = effectiveHealth <= (player.isFallFlying() ? elytraHealth.floatValue() : health.floatValue())
            || !protectHead && (incomingThreat || explosionTicks > 0 || surroundingsDanger(client));
        incomingThreat = false;
        if (explosionTicks > 0) explosionTicks--;
        if (danger) safeTicks = 0; else safeTicks++;
        if (!player.containerMenu.getCarried().isEmpty()) return;
        if (danger) {
            if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
            int slot = findTotem(client);
            if (slot < 0 || !InvActionUtil.get().tryAcquire(id(), InvActionUtil.PRIORITY_SURVIVAL, 100)) return;
            if (stopUsing.enabled() && player.isUsingItem()) client.gameMode.releaseUsingItem(player);
            ItemStack displaced = player.getOffhandItem().copy();
            client.gameMode.handleContainerInput(player.containerMenu.containerId, slot, 40, ContainerInput.SWAP, player);
            if (restore.enabled() && !displaced.isEmpty()) {
                previous = displaced;
                originalSlot = player.containerMenu.getSlot(slot).getContainerSlot();
            }
        } else if (!previous.isEmpty()) {
            if (!restore.enabled() || ItemStack.isSameItemSameComponents(previous, player.getOffhandItem())) { forgetReturn(); return; }
            if (safeTicks < 20 + 10 - returnSpeed.intValue() || client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) return;
            if (!player.getOffhandItem().isEmpty() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) { forgetReturn(); return; }
            int slot = findPrevious(client);
            if (slot >= 0 && InvActionUtil.get().tryAcquire(id(), InvActionUtil.PRIORITY_SURVIVAL, 100)) {
                client.gameMode.handleContainerInput(player.containerMenu.containerId, slot, 40, ContainerInput.SWAP, player);
                if (ItemStack.isSameItemSameComponents(previous, player.getOffhandItem())) forgetReturn();
            }
        }
    }
    private int findTotem(Minecraft client) {
        int enchanted = -1;
        for (var slot : client.player.containerMenu.slots) {
            if (slot.container != client.player.getInventory() || slot.getContainerSlot() == 40 || !slot.mayPickup(client.player) || !slot.getItem().is(Items.TOTEM_OF_UNDYING)) continue;
            if (!preserveEnchanted.enabled() || !slot.getItem().isEnchanted()) return slot.index;
            enchanted = slot.index;
        }
        return enchanted;
    }
    private int findPrevious(Minecraft client) {
        int fallback = -1;
        for (var slot : client.player.containerMenu.slots) {
            if (slot.container != client.player.getInventory() || slot.getContainerSlot() == 40 || !slot.mayPickup(client.player)) continue;
            if (!ItemStack.isSameItemSameComponents(previous, slot.getItem())) continue;
            if (slot.getContainerSlot() == originalSlot) return slot.index;
            if (slot.getContainerSlot() != client.player.getInventory().getSelectedSlot()) fallback = slot.index;
        }
        return fallback;
    }
    private boolean surroundingsDanger(Minecraft client) {
        var player = client.player;
        if (falling.enabled() && !player.isInWater() && player.fallDistance > 10) return true;
        double range = Math.max(crystalRange.floatValue(), Math.max(cartRange.floatValue(), tntRange.floatValue()));
        if (tridents.enabled()) range = Math.max(range, tridentRange.get());
        if (maces.enabled()) range = Math.max(range, maceRange.get());
        for (var entity : client.level.getEntities(player, player.getBoundingBox().inflate(range))) {
            double radius = entity instanceof EndCrystal && crystals.enabled() ? crystalRange.floatValue()
                : entity instanceof PrimedTnt && tnt.enabled() ? tntRange.floatValue()
                : entity instanceof MinecartTNT cart && cart.isPrimed() && minecart.enabled() ? cartRange.floatValue()
                : entity instanceof ThrownTrident trident && tridents.enabled() && trident.getOwner() != player
                    && !trident.isNoPhysics() && trident.getDeltaMovement().lengthSqr() > 1e-4 ? tridentRange.get()
                : entity instanceof Player other && maces.enabled() && other.isAlive() && !other.isSpectator()
                    && !im.yaHustler.friend.Friends.contains(other) && other.getY() > player.getY()
                    && other.getMainHandItem().is(Items.MACE) ? maceRange.get() : 0;
            if (radius > 0 && player.distanceToSqr(entity) <= radius * radius) return true;
        }
        if (obsidian.enabled()) {
            var box = player.getBoundingBox();
            int bottom = (int) Math.floor(box.minY - .03);
            for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
                for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++)
                    for (int y = bottom; y > bottom - 2; y--)
                        if (client.level.getBlockState(block.set(x, y, z)).is(Blocks.OBSIDIAN)) return true;
        }
        if (!anchors.enabled()) return false;
        int radius = (int) Math.ceil(anchorRange.floatValue());
        var center = player.blockPosition();
        var eye = player.getEyePosition();
        double rangeSquared = anchorRange.get() * anchorRange.get();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            block.set(center.getX() + x, center.getY() + y, center.getZ() + z);
            if (eye.distanceToSqr(block.getX() + .5, block.getY() + .5, block.getZ() + .5) > rangeSquared) continue;
            var state = client.level.getBlockState(block);
            if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                && !client.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, block)) return true;
        }
        return false;
    }
    @Override public void onPacket(EventPacketReceive event) {
        var client = Minecraft.getInstance();
        if (client.player != null && event.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
            double radius = packet.getType() == net.minecraft.world.entity.EntityTypes.END_CRYSTAL && crystals.enabled() ? crystalRange.floatValue()
                : packet.getType() == net.minecraft.world.entity.EntityTypes.TNT && tnt.enabled() ? tntRange.floatValue()
                : packet.getType() == net.minecraft.world.entity.EntityTypes.TRIDENT && tridents.enabled()
                    && packet.getData() != client.player.getId() && packet.getMovement().lengthSqr() > 1e-4 ? tridentRange.get() : 0;
            if (radius > 0 && client.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) <= radius * radius) incomingThreat = true;
        }
        if (client.player != null && client.level != null && anchors.enabled()
            && event.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet
            && packet.getBlockState().is(Blocks.RESPAWN_ANCHOR) && packet.getBlockState().getValue(RespawnAnchorBlock.CHARGE) > 0
            && !client.level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, packet.getPos())
            && client.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(packet.getPos())) <= anchorRange.floatValue() * anchorRange.floatValue()) incomingThreat = true;
        if (event.getPacket() instanceof ClientboundSetHealthPacket packet) { serverHealth = packet.getHealth(); healthPending = true; }
        if (event.getPacket() instanceof ClientboundDamageEventPacket packet && client.player != null && packet.entityId() == client.player.getId()
            && (packet.sourceType().is(DamageTypes.EXPLOSION) || packet.sourceType().is(DamageTypes.PLAYER_EXPLOSION) || packet.sourceType().is(DamageTypes.BAD_RESPAWN_POINT))) explosionTicks = 100;
    }
    private void forgetReturn() { previous = ItemStack.EMPTY; originalSlot = -1; }
    private void reset() { forgetReturn(); safeTicks = 0; explosionTicks = 0; serverHealth = Float.MAX_VALUE; healthPending = false; incomingThreat = false; InvActionUtil.get().release(id()); }
    @Override protected void onDisable() { reset(); }
    @Override public void onDisconnect(EventDisconnect event) { reset(); }
}
