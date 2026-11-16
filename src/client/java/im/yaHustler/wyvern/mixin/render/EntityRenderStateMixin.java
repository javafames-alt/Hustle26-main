package im.yaHustler.wyvern.mixin.render;

import im.yaHustler.wyvern.duck.RenderedEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.WeakReference;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements RenderedEntity {
	@Unique private WeakReference<Entity> wyvern$entity;

	@Override
	public Entity wyvern$getEntity() {
		return wyvern$entity == null ? null : wyvern$entity.get();
	}

	@Override
	public void wyvern$setEntity(Entity entity) {
		wyvern$entity = entity == null ? null : new WeakReference<>(entity);
	}
}
