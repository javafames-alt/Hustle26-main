package morfeov.coliseum.mixin;

import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.Optimization;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class OptimizationEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void coliseum$limitDistantItems(Entity entity, Frustum frustum, double cameraX, double cameraY,
                                           double cameraZ, float partialTick, CallbackInfoReturnable<Boolean> result) {
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) return;
        if (entity.isCurrentlyGlowing()) return;
        Optimization optimization = Coliseum.MODULES.get(Optimization.class);
        if (optimization != null && optimization.cullItem(entity.distanceToSqr(cameraX, cameraY, cameraZ))) result.setReturnValue(false);
    }
}
