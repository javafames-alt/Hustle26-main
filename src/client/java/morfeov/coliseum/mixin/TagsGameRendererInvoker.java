package morfeov.coliseum.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface TagsGameRendererInvoker {
    @Invoker("bobHurt")
    void coliseum$tagsHurt(CameraRenderState camera, PoseStack pose);

    @Invoker("bobView")
    void coliseum$tagsBob(CameraRenderState camera, PoseStack pose);
}
