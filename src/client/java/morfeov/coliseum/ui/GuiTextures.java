package morfeov.coliseum.ui;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import morfeov.coliseum.mixin.GuiGraphicsExtractorInvoker;
import morfeov.coliseum.render.hands.HandsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** GUI-only linear sampling: never changes a texture's world-render sampler. */
public final class GuiTextures {
    private GuiTextures() {}
    public static void blit(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                            int x, int y, float u, float v, int width, int height,
                            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight, int color) {
        if ((color >>> 24) == 0 || width <= 0 || height <= 0) return;
        blit(g, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture), x, y, u, v,
            width, height, sourceWidth, sourceHeight, textureWidth, textureHeight, color);
    }
    public static void blit(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                            int x, int y, float u, float v, int width, int height,
                            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        blit(g, pipeline, texture, x, y, u, v, width, height, sourceWidth, sourceHeight, textureWidth, textureHeight, -1);
    }
    public static void blit(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                            int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
        blit(g, pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }
    public static void blit(GuiGraphicsExtractor g, RenderPipeline pipeline, AbstractTexture texture,
                            int x, int y, float u, float v, int width, int height,
                            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight, int color) {
        if ((color >>> 24) == 0 || width <= 0 || height <= 0) return;
        ((GuiGraphicsExtractorInvoker)g).coliseum$filteredBlit(pipeline, texture.getTextureView(), HandsFx.linear(),
            x, y, x + width, y + height, u / textureWidth, (u + sourceWidth) / textureWidth,
            v / textureHeight, (v + sourceHeight) / textureHeight, color);
    }
}
