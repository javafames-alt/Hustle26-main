package morfeov.coliseum.mixin;

import morfeov.coliseum.skin.SkinEntry;
import morfeov.coliseum.skin.SkinLayer3D;
import morfeov.coliseum.skin.SkinLayerState;
import morfeov.coliseum.skin.SkinManager;
import morfeov.coliseum.skin.SkinVoxels;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coliseum$addSkinLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).coliseum$addLayer(
            new SkinLayer3D((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this));
        ((LivingEntityRendererInvoker)this).coliseum$addLayer(new morfeov.coliseum.cosmetics.render.CosmeticLayer(
            (RenderLayerParent<AvatarRenderState,PlayerModel>)(Object)this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("RETURN"))
    private void coliseum$skinLayers(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ((morfeov.coliseum.render.coliseimvisual.EntityIdAccess)state).coliseum$setEntityId(avatar.getId());
        var mc=net.minecraft.client.Minecraft.getInstance();
        var tags = morfeov.coliseum.Coliseum.MODULES.get(morfeov.coliseum.module.impl.render.Tags.class);
        if (tags != null && tags.replacesName(avatar)) state.nameTag = null;
        var cape=morfeov.coliseum.cosmetics.LocalCosmetics.selectedCapeTexture();
        if(avatar==mc.player && cape!=null && state.skin!=null) {
            var skin=state.skin;
            var texture=new net.minecraft.core.ClientAsset.ResourceTexture(cape,cape);
            state.skin=new net.minecraft.world.entity.player.PlayerSkin(skin.body(),texture,skin.elytra(),skin.model(),skin.secure());
            state.showCape=true;
        }
        SkinLayerState layers = (SkinLayerState) state;
        if (!SkinManager.isListening()) {
            layers.coliseum$setSkinLayers(null, 0);
            return;
        }
        SkinEntry entry = avatar instanceof AbstractClientPlayer player ? SkinManager.layersFor(player) : null;
        if (entry == null) {
            layers.coliseum$setSkinLayers(null, 0);
            return;
        }
        int mask = 0;
        if (state.showHat) mask |= 1 << SkinVoxels.HAT;
        if (state.showJacket) mask |= 1 << SkinVoxels.JACKET;
        if (state.showLeftSleeve) mask |= 1 << SkinVoxels.LEFT_SLEEVE;
        if (state.showRightSleeve) mask |= 1 << SkinVoxels.RIGHT_SLEEVE;
        if (state.showLeftPants) mask |= 1 << SkinVoxels.LEFT_PANTS;
        if (state.showRightPants) mask |= 1 << SkinVoxels.RIGHT_PANTS;
        state.showHat = state.showJacket = state.showLeftSleeve = state.showRightSleeve = false;
        state.showLeftPants = state.showRightPants = false;
        layers.coliseum$setSkinLayers(entry, mask);
    }
}
