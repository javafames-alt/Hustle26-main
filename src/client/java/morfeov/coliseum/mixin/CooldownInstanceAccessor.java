package morfeov.coliseum.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
    @Accessor("startTime") int coliseum$startTime();
    @Accessor("endTime") int coliseum$endTime();
}
