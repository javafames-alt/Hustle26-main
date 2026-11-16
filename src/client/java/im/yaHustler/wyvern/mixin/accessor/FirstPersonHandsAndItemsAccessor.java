package im.yaHustler.wyvern.mixin.accessor;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FirstPersonHandsAndItems.class)
public interface FirstPersonHandsAndItemsAccessor {
	@Accessor("mainHandHeight")
	float wyvern$getMainHandHeight();
}
