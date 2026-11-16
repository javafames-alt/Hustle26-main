package morfeov.coliseum.render.chams;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

public final class ChamsDepth {
   public static GpuTexture depthCopy;
   public static GpuTextureView depthCopyView;
   public static int texW = -1;
   public static int texH = -1;
   public static long copiedFrame = -1L;
   public static long frame;

   public static void beginFrame() {
      frame++;
   }

   public static GpuTextureView view() {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.gameRenderer.mainRenderTarget();
      if (main != null && main.getDepthTexture() != null) {
         int w = main.width;
         int h = main.height;
         if (w > 0 && h > 0) {
            ensureTexture(w, h);
            if (copiedFrame != frame) {
               RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getDepthTexture(), depthCopy, 0, 0, 0, 0, 0, w, h);
               copiedFrame = frame;
            }

            return depthCopyView;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void ensureTexture(int w, int h) {
      if (depthCopy == null || depthCopy.isClosed() || texW != w || texH != h) {
         clear();
         GpuDevice device = RenderSystem.getDevice();
         depthCopy = device.createTexture(() -> "Coliseum scene depth copy", 5, GpuFormat.D32_FLOAT, w, h, 1, 1);
         depthCopyView = device.createTextureView(depthCopy);
         texW = w;
         texH = h;
      }
   }

   public static void clearIfUnused() {
      if (depthCopy != null && copiedFrame != frame) {
         clear();
      }
   }

   public static void clear() {
      if (depthCopyView != null) {
         depthCopyView.close();
         depthCopyView = null;
      }

      if (depthCopy != null) {
         depthCopy.close();
         depthCopy = null;
      }

      texH = -1;
      texW = -1;
      copiedFrame = -1L;
   }
}
