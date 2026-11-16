package im.yaHustler.mixin;

import im.yaHustler.skin.SkinEntry;
import im.yaHustler.skin.SkinLayerState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements SkinLayerState, im.yaHustler.render.coliseimvisual.EntityIdAccess {
    @Unique private int hustle263$entityId;
    public int hustle263$getEntityId(){return hustle263$entityId;}
    public void hustle263$setEntityId(int id){hustle263$entityId=id;}
    @Unique private SkinEntry hustle263$layers;
    @Unique private int hustle263$mask;

    @Override public SkinEntry hustle263$skinLayers() { return hustle263$layers; }
    @Override public int hustle263$skinLayerMask() { return hustle263$mask; }
    @Override
    public void hustle263$setSkinLayers(SkinEntry entry, int mask) {
        hustle263$layers = entry;
        hustle263$mask = mask;
    }
}
