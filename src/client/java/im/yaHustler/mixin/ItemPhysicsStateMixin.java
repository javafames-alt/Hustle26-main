package im.yaHustler.mixin;

import im.yaHustler.module.impl.render.ItemPhysics;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemPhysicsStateMixin implements ItemPhysics.RenderPose {
    @Unique private float hustle263$tilt = Float.NaN;

    @Override public void hustle263$setItemTilt(float tilt) { hustle263$tilt = tilt; }
    @Override public float hustle263$itemTilt() { return hustle263$tilt; }
}
