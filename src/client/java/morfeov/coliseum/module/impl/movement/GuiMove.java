package morfeov.coliseum.module.impl.movement;
import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.mixin.KeyMappingAccessor;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.event.EventInput;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import java.util.ArrayDeque;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

@ModuleInfo(id="gui_walk", name="GuiMove", category=Category.MOVEMENT, description="Движение при открытых меню")
public final class GuiMove extends PortModule {
    private final ModeSetting mode = add(new ModeSetting("Режим", "Обычный", "Обычный", "RW"));
    private final BooleanSetting slowClicks = add(new BooleanSetting("Замедлять", false).visibleWhen(() -> !mode.is("RW")));
    private final BooleanSetting extraTick = add(new BooleanSetting("Дополнительный тик", false)
            .visibleWhen(() -> !mode.is("RW") && slowClicks.enabled()));
    private final ArrayDeque<Click> clicks = new ArrayDeque<>();
    private AbstractContainerMenu pendingMenu;
    private int waitTicks;
    private boolean replaying;

    private record Click(int slot, int button, ContainerInput action) {}

    private boolean down(KeyMapping key) {
        var binding = ((KeyMappingAccessor) key).coliseum$key();
        return binding.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(binding.getValue());
    }
    @Override public void onInput(EventInput event) {
        var screen = mc.gui.screen();
        if (screen == null || screen.isPauseScreen() || screen instanceof ChatScreen || screen instanceof SignEditScreen
                || screen.getFocused() instanceof EditBox field && field.canConsumeInput() || mc.player == null) return;
        if (!clicks.isEmpty()) {
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            event.setSprint(false);
            return;
        }
        event.setForward((down(mc.options.keyUp) ? 1 : 0) - (down(mc.options.keyDown) ? 1 : 0));
        event.setStrafe((down(mc.options.keyLeft) ? 1 : 0) - (down(mc.options.keyRight) ? 1 : 0));
        event.setJump(down(mc.options.keyJump));
    }

    public boolean deferClick(int id, int slot, int button, ContainerInput action, Player player) {
        if (replaying || !delaysClicks() || player != mc.player || !(mc.gui.screen() instanceof InventoryScreen)
                || player.containerMenu.containerId != id || (action != ContainerInput.PICKUP
                && action != ContainerInput.QUICK_MOVE && action != ContainerInput.SWAP)) return false;
        if (clicks.isEmpty()) {
            if (player.getDeltaMovement().horizontalDistanceSqr() < .0001
                    && !down(mc.options.keyUp) && !down(mc.options.keyDown)
                    && !down(mc.options.keyLeft) && !down(mc.options.keyRight)) return false;
            pendingMenu = player.containerMenu;
            // Replay runs before input, so the third callback follows two complete stopped ticks.
            waitTicks = (mode.is("RW") || extraTick.enabled()) ? 3 : 2;
        }
        if (clicks.size() < 32) clicks.addLast(new Click(slot, button, action));
        return true;
    }

    @Override public void tick(Minecraft client) {
        if (clicks.isEmpty()) return;
        if (client.player == null || client.gameMode == null || client.player.containerMenu != pendingMenu
                || !(client.gui.screen() instanceof InventoryScreen) || !delaysClicks()) {
            clearClicks();
            return;
        }
        if (--waitTicks > 0) return;
        replaying = true;
        try {
            while (!clicks.isEmpty() && client.player.containerMenu == pendingMenu) {
                Click click = clicks.removeFirst();
                client.gameMode.handleContainerInput(pendingMenu.containerId, click.slot(), click.button(), click.action(), client.player);
            }
        } finally {
            replaying = false;
            clearClicks();
        }
    }

    private boolean delaysClicks() {
        return mode.is("RW") || slowClicks.enabled();
    }

    @Override protected void onDisable() {
        clearClicks();
    }

    private void clearClicks() {
        clicks.clear();
        pendingMenu = null;
    }
}
