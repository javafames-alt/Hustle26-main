package im.yaHustler.module.impl.combat;

import im.yaHustler.Hustle26;
import im.yaHustler.friend.Friends;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.impl.misc.FakePlayer;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.setting.ModeSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

@ModuleInfo(id="anti_bot", name="AntiBot", category=Category.COMBAT, description="Проверяет ближайших игроков по tab-списку Hustle 26.3")
public final class AntiBot extends Module {
    private static final String TAB = "Tab-список", SERVER = "По серверу";
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    private final ModeSetting mode = add(new ModeSetting("Проверка", TAB, TAB, SERVER));
    private final BooleanSetting remove = add(new BooleanSetting("Удалять из мира", false));

    public static boolean rejects(Entity entity) {
        var module=Hustle26.MODULES.get(AntiBot.class);
        return module!=null && module.enabled() && entity instanceof Player player && module.isBot(player);
    }
    public boolean isBot(Player player) {
        var mc=Minecraft.getInstance();
        var fake = Hustle26.MODULES.get(FakePlayer.class);
        if (!enabled() || player == null || mc.player == null || player == mc.player || Friends.contains(player)
            || (fake != null && fake.dummy() == player) || mc.getConnection() == null) return false;
        if (mode.is(SERVER)) {
            var server = mc.getCurrentServer();
            if (server == null) return false;
            String address = server.ip.toLowerCase(Locale.ROOT);
            if (address.contains("reallyworld")) return reallyWorldBot(player);
            if (address.contains("lonygrief")) return lonyGriefBot(player);
            return !player.getUUID().equals(UUIDUtil.createOfflinePlayerUUID(player.getGameProfile().name()));
        }
        if (mc.player.distanceToSqr(player) >= 64) return false;
        var entry=mc.getConnection().getPlayerInfo(player.getUUID());
        return entry==null || suspiciousDisplay(entry.getTabListDisplayName()==null?null:entry.getTabListDisplayName().getString(),player.getGameProfile().name());
    }

    private static boolean reallyWorldBot(Player player) {
        var armor = ARMOR.stream().map(player::getItemBySlot).toList();
        if (armor.stream().anyMatch(item -> item.isEmpty() || !item.isEnchantable() || item.isDamaged())) return false;
        boolean commonArmor = armor.stream().anyMatch(item -> item.is(Items.LEATHER_BOOTS) || item.is(Items.LEATHER_LEGGINGS)
            || item.is(Items.LEATHER_CHESTPLATE) || item.is(Items.LEATHER_HELMET) || item.is(Items.IRON_BOOTS)
            || item.is(Items.IRON_LEGGINGS) || item.is(Items.IRON_CHESTPLATE) || item.is(Items.IRON_HELMET));
        return commonArmor && player.getOffhandItem().isEmpty() && !player.getMainHandItem().isEmpty()
            && player.getFoodData().getFoodLevel() == 20;
    }

    private static boolean lonyGriefBot(Player player) {
        var armor = ARMOR.stream().map(player::getItemBySlot).toList();
        return player.getGameProfile().name().length() == 6 && player.getArmorValue() != 0
            && armor.stream().anyMatch(item -> !item.isDamaged()) && armor.stream().noneMatch(item -> item.hasFoil());
    }

    @Override public void tick(Minecraft client) {
        if (!remove.enabled() || client.level == null) return;
        for (Player player : List.copyOf(client.level.players())) {
            if (isBot(player)) client.level.removeEntity(player.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    public static boolean suspiciousDisplay(String display,String name) { return display==null || display.equals(name); }
}
