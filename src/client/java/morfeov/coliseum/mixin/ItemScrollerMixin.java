package morfeov.coliseum.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.BitSet;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.misc.ItemScroller;
import morfeov.coliseum.util.ActionMouseButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ItemScrollerMixin {
    @Shadow private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }
    @Shadow protected abstract void slotClicked(Slot slot, int index, int button, ContainerInput action);

    @Unique private final BitSet coliseum$visitedSlots = new BitSet();
    @Unique private double coliseum$lastX = Double.NaN;
    @Unique private double coliseum$lastY = Double.NaN;
    @Unique private long coliseum$nextTransfer;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void coliseum$scrollItems(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        var module = Coliseum.MODULES.get(ItemScroller.class);
        var screen = (AbstractContainerScreen<?>) (Object) this;
        var client = Minecraft.getInstance();
        if (module == null || !module.enabled() || client.gui.screen() != screen || client.player == null
                || !screen.getMenu().getCarried().isEmpty() || !coliseum$holdingTransfer()) {
            coliseum$clearSweep();
            return;
        }
        double startX = Double.isNaN(coliseum$lastX) ? x : coliseum$lastX;
        double startY = Double.isNaN(coliseum$lastY) ? y : coliseum$lastY;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x - startX), Math.abs(y - startY)) / 4));
        long now = System.nanoTime();
        for (int i = 0; i <= steps && now >= coliseum$nextTransfer; i++) {
            double fraction = (double) i / steps;
            Slot slot = getHoveredSlot(startX + (x - startX) * fraction, startY + (y - startY) * fraction);
            if (slot == null || !slot.isActive() || !slot.hasItem() || !slot.mayPickup(client.player)
                    || coliseum$visitedSlots.get(slot.index)) continue;
            coliseum$visitedSlots.set(slot.index);
            slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
            coliseum$nextTransfer = now + module.delay.intValue() * 1_000_000L;
        }
        coliseum$lastX = x;
        coliseum$lastY = y;
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"))
    private void coliseum$rememberVanillaShiftClick(Slot slot, int index, int button, ContainerInput action, CallbackInfo ci) {
        if (slot != null && index >= 0 && action == ContainerInput.QUICK_MOVE && coliseum$holdingTransfer()) {
            coliseum$visitedSlots.set(index);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void coliseum$releaseSweep(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) coliseum$clearSweep();
    }

    @Unique private static boolean coliseum$holdingTransfer() {
        return ActionMouseButtons.down(InputConstants.MOUSE_BUTTON_LEFT)
                && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }

    @Unique private void coliseum$clearSweep() {
        coliseum$visitedSlots.clear();
        coliseum$lastX = coliseum$lastY = Double.NaN;
        coliseum$nextTransfer = 0;
    }
}
