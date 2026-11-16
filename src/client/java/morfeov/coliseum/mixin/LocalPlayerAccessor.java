package morfeov.coliseum.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("wasSprinting")
    boolean coliseum$wasSprinting();

    @Accessor("wasSprinting")
    void coliseum$setWasSprinting(boolean value);
}
