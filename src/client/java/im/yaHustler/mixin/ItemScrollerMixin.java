package im.yaHustler.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.BitSet;
import im.yaHustler.Hustle26;
import im.yaHustler.module.impl.misc.ItemScroller;
import im.yaHustler.util.ActionMouseButtons;
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

    @Unique private final BitSet hustle263$visitedSlots = new BitSet();
    @Unique private double hustle263$lastX = Double.NaN;
    @Unique private double hustle263$lastY = Double.NaN;
    @Unique private long hustle263$nextTransfer;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hustle263$scrollItems(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        var module = Hustle26.MODULES.get(ItemScroller.class);
        var screen = (AbstractContainerScreen<?>) (Object) this;
        var client = Minecraft.getInstance();
        if (module == null || !module.enabled() || client.gui.screen() != screen || client.player == null
                || !screen.getMenu().getCarried().isEmpty() || !hustle263$holdingTransfer()) {
            hustle263$clearSweep();
            return;
        }
        double startX = Double.isNaN(hustle263$lastX) ? x : hustle263$lastX;
        double startY = Double.isNaN(hustle263$lastY) ? y : hustle263$lastY;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x - startX), Math.abs(y - startY)) / 4));
        long now = System.nanoTime();
        for (int i = 0; i <= steps && now >= hustle263$nextTransfer; i++) {
            double fraction = (double) i / steps;
            Slot slot = getHoveredSlot(startX + (x - startX) * fraction, startY + (y - startY) * fraction);
            if (slot == null || !slot.isActive() || !slot.hasItem() || !slot.mayPickup(client.player)
                    || hustle263$visitedSlots.get(slot.index)) continue;
            hustle263$visitedSlots.set(slot.index);
            slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
            hustle263$nextTransfer = now + module.delay.intValue() * 1_000_000L;
        }
        hustle263$lastX = x;
        hustle263$lastY = y;
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"))
    private void hustle263$rememberVanillaShiftClick(Slot slot, int index, int button, ContainerInput action, CallbackInfo ci) {
        if (slot != null && index >= 0 && action == ContainerInput.QUICK_MOVE && hustle263$holdingTransfer()) {
            hustle263$visitedSlots.set(index);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void hustle263$releaseSweep(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) hustle263$clearSweep();
    }

    @Unique private static boolean hustle263$holdingTransfer() {
        return ActionMouseButtons.down(InputConstants.MOUSE_BUTTON_LEFT)
                && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }

    @Unique private void hustle263$clearSweep() {
        hustle263$visitedSlots.clear();
        hustle263$lastX = hustle263$lastY = Double.NaN;
        hustle263$nextTransfer = 0;
    }
}
