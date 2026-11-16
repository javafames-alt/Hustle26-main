package morfeov.coliseum.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntitySharedFlagsAccessor {
    @Accessor("DATA_SHARED_FLAGS_ID")
    static EntityDataAccessor<Byte> coliseum$sharedFlags() { throw new AssertionError(); }
}
