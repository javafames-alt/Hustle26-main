package im.yaHustler.mixin;

import im.yaHustler.rotation.HandSway;
import im.yaHustler.rotation.Look;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void hustle263$fixHandSway(LocalPlayer player, float partialTicks,
                                     FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (!Look.active()) {
            HandSway.reset();
            return;
        }
        HandSway.update();
        state.viewXRot = Look.pitch();
        state.viewYRot = Look.yaw();
        state.xBob = Look.pitch() - HandSway.pitchOffset(partialTicks);
        state.yBob = Look.yaw() - HandSway.yawOffset(partialTicks);
    }
}
