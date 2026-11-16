package morfeov.coliseum.mixin;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.impl.render.BetterMinecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LevelRenderer.class)
public abstract class BetterChunkMixin {
 @Redirect(method="extractSectionDrawGroups",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getRenderOrigin()Lnet/minecraft/core/BlockPos;"))
 private BlockPos coliseum$rise(SectionRenderDispatcher.RenderSection section){BlockPos p=section.getRenderOrigin();var m=Coliseum.MODULES.get(BetterMinecraft.class);return m==null?p:m.chunkOrigin(p);}
}
