package morfeov.coliseum.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import morfeov.coliseum.cosmetics.WeaponCosmetics;

@Mixin(ItemModelResolver.class)
public abstract class WeaponCosmeticsMixin {
    @ModifyVariable(method="appendItemLayers",at=@At("HEAD"),argsOnly=true)
    private ItemStack coliseum$weaponSkin(ItemStack stack,@Local(argsOnly=true) ItemOwner owner) {
        return WeaponCosmetics.renderStack(stack,owner);
    }
}
