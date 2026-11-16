package im.yaHustler.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key") InputConstants.Key hustle263$key();
}
