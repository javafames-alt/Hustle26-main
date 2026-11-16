package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.render.Optimization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryFps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DebugEntryFps.class)
public abstract class OptimizationDebugFpsMixin {
    @Redirect(method = "display", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getFps()I"))
    private int coliseum$displayFps(Minecraft minecraft) {
        return Optimization.displayedFps(minecraft);
    }
}
