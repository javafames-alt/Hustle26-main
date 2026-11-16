package im.yaHustler.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import im.yaHustler.module.impl.combat.AutoExplosion;

@Mixin(BlockItem.class)
public abstract class BlockPlacementMixin {
    @Inject(method="place",at=@At("RETURN"))
    private void hustle263$placed(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> result){
        if(context.getLevel().isClientSide()&&result.getReturnValue().consumesAction()
                &&context.getPlayer()==net.minecraft.client.Minecraft.getInstance().player)
            AutoExplosion.blockPlaced(context.getClickedPos());
    }
}
