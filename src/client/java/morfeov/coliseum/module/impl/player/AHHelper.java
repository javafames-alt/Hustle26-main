package morfeov.coliseum.module.impl.player;

import java.util.Locale;
import java.util.regex.Pattern;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.event.EventPacketReceive;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.KeySetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(id="ah_helper", name="AHHelper", category=Category.PLAYER, description="Помощник продажи на аукционе")
public final class AHHelper extends PortModule {
    public final KeySetting sellKey=add(new KeySetting("Продать предмет"));
    public final ModeSetting priceMode=add(new ModeSetting("Поиск цены", "Автопродажа", "Автопродажа", "Поиск"));
    public final NumberSetting discount=add(new NumberSetting("Множитель цены", 0.95, 0.1, 1, 0.01));
    public final BooleanSetting autoConfirm=add(new BooleanSetting("Автоподтверждение", true));
    private static final Pattern PRICE=Pattern.compile("(?:Цена\\s*:|\\$)\\s*([0-9][0-9 ,\\u00a0\\u2009]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private enum State { IDLE, SEARCH, SELL, CONFIRM }
    private State state=State.IDLE;
    private ItemStack selling=ItemStack.EMPTY;
    private long started, price;

    @Override public void tick(Minecraft mc) {
        if (mc.player==null || mc.getConnection()==null) { reset(); return; }
        if (sellKey.pressed(mc) && state==State.IDLE && !mc.player.getMainHandItem().isEmpty()) {
            selling=mc.player.getMainHandItem().copy(); started=System.currentTimeMillis();
            if (priceMode.is("Автопродажа")) { mc.getConnection().sendCommand("ah sell auto"); state=State.CONFIRM; }
            else { mc.getConnection().sendCommand("ah search"); state=State.SEARCH; }
        }
        if (state==State.IDLE) return;
        if (System.currentTimeMillis()-started>5000) { reset(); return; }
        if (state==State.SEARCH && mc.gui.screen()!=null && auctionMenu()) {
            long best=Long.MAX_VALUE;
            for (int i=0;i<Math.min(45,mc.player.containerMenu.slots.size());i++) {
                ItemStack stack=mc.player.containerMenu.getSlot(i).getItem();
                if (stack.isEmpty()) continue;
                String name=stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains("продано") || name.contains("не актуален")) continue;
                var lore=stack.get(DataComponents.LORE);
                if (lore==null) continue;
                if (lore.styledLines().stream().map(line -> line.getString().toLowerCase(Locale.ROOT)).anyMatch(line -> line.contains("только полностью") || line.contains("продано"))) continue;
                for (var line:lore.styledLines()) {
                    String text=line.getString();
                    var match=PRICE.matcher(text);
                    if (match.find()) try { long value=Long.parseLong(match.group(1).replaceAll("[^0-9]", "")); best=Math.min(best,value/Math.max(1,stack.getCount())); } catch (NumberFormatException ignored) { }
                }
            }
            if (best!=Long.MAX_VALUE) {
                price=Math.max(1,Math.round(best*(double)selling.getCount()*discount.get()));
                mc.player.closeContainer(); state=State.SELL; started=System.currentTimeMillis();
            }
        } else if (state==State.SELL && mc.gui.screen()==null) {
            if (ItemStack.isSameItemSameComponents(selling,mc.player.getMainHandItem()) && selling.getCount()==mc.player.getMainHandItem().getCount()) mc.getConnection().sendCommand("ah sell "+price);
            reset();
        }
    }
    private boolean auctionMenu() {
        var slots=mc.player.containerMenu.slots;
        if (slots.size()<=49) return false;
        var refresh=slots.get(45).getItem();
        var star=slots.get(49).getItem();
        return star.is(Items.NETHER_STAR) || refresh.is(Items.PAPER) && refresh.getHoverName().getString().toLowerCase(Locale.ROOT).contains("обновить");
    }
    @Override public void onPacketReceive(EventPacketReceive event) {
        if (mc.getConnection()!=null && event.getPacket() instanceof ClientboundSystemChatPacket chat
                && (autoConfirm.enabled() || state==State.CONFIRM)
                && chat.content().getString().startsWith("▶ Введите /ah sell auto confirm,")) {
            mc.getConnection().sendCommand("ah sell auto confirm"); reset();
        }
    }
    private void reset() { state=State.IDLE; selling=ItemStack.EMPTY; price=0; }
    @Override protected void onDisable() { reset(); }
}
