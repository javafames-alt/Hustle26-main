package morfeov.coliseum.mixin;

import morfeov.coliseum.module.impl.render.ItemPhysics;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemPhysicsStateMixin implements ItemPhysics.RenderPose {
    @Unique private float coliseum$tilt = Float.NaN;

    @Override public void coliseum$setItemTilt(float tilt) { coliseum$tilt = tilt; }
    @Override public float coliseum$itemTilt() { return coliseum$tilt; }
}
