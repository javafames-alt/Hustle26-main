package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.movement.NoWeb;
import morfeov.coliseum.movement.WebContact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WebBlock.class)
public abstract class MovementWebBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void coliseum$webContact(BlockState state, Level level, BlockPos position, Entity entity,
                                     InsideBlockEffectApplier effects, boolean wasInside, CallbackInfo ci) {
        if (entity instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            ((WebContact) entity).coliseum$setInWeb(true);
            if (NoWeb.onWebContact(player, position)) ci.cancel();
        }
    }
}
