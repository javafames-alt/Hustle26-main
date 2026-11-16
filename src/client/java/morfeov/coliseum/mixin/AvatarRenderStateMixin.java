package morfeov.coliseum.mixin;

import morfeov.coliseum.skin.SkinEntry;
import morfeov.coliseum.skin.SkinLayerState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements SkinLayerState, morfeov.coliseum.render.coliseimvisual.EntityIdAccess {
    @Unique private int coliseum$entityId;
    public int coliseum$getEntityId(){return coliseum$entityId;}
    public void coliseum$setEntityId(int id){coliseum$entityId=id;}
    @Unique private SkinEntry coliseum$layers;
    @Unique private int coliseum$mask;

    @Override public SkinEntry coliseum$skinLayers() { return coliseum$layers; }
    @Override public int coliseum$skinLayerMask() { return coliseum$mask; }
    @Override
    public void coliseum$setSkinLayers(SkinEntry entry, int mask) {
        coliseum$layers = entry;
        coliseum$mask = mask;
    }
}
