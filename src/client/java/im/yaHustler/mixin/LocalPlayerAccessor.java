package im.yaHustler.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("wasSprinting")
    boolean hustle263$wasSprinting();

    @Accessor("wasSprinting")
    void hustle263$setWasSprinting(boolean value);
}
