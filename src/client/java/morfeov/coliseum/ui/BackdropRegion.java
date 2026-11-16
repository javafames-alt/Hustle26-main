package morfeov.coliseum.ui;

/** Conservative framebuffer bounds, including rotated panels and filter support. */
public record BackdropRegion(int x, int y, int width, int height) {
    public static BackdropRegion of(int guiWidth, int guiHeight, float[][] rectangles,
                                    int targetWidth, int targetHeight, double paddingGui) {
        if (guiWidth <= 0 || guiHeight <= 0) return new BackdropRegion(0, 0, targetWidth, targetHeight);
        double left = Double.POSITIVE_INFINITY, top = left;
        double right = Double.NEGATIVE_INFINITY, bottom = right;
        for (int i = 0; i < Math.min(BackdropStyle.LIMIT, rectangles.length); i++) {
            float[] r = rectangles[i];
            double angle = r.length > 5 ? r[5] : 0;
            double c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
            double halfWidth = (c * r[2] + s * r[3]) / 2;
            double halfHeight = (s * r[2] + c * r[3]) / 2;
            double cx = r[0] + r[2] / 2d, cy = r[1] + r[3] / 2d;
            left = Math.min(left, cx - halfWidth); right = Math.max(right, cx + halfWidth);
            top = Math.min(top, cy - halfHeight); bottom = Math.max(bottom, cy + halfHeight);
        }
        if (rectangles.length == 0) return new BackdropRegion(0, 0, 0, 0);
        // roundedBox() has a half-GUI-pixel antialiasing fringe.
        double margin = .5 + Math.max(0, paddingGui);
        double sx = targetWidth / (double)guiWidth, sy = targetHeight / (double)guiHeight;
        int x0 = (int)Math.clamp(Math.floor((left - margin) * sx), 0, targetWidth);
        int y0 = (int)Math.clamp(Math.floor((top - margin) * sy), 0, targetHeight);
        int x1 = (int)Math.clamp(Math.ceil((right + margin) * sx), 0, targetWidth);
        int y1 = (int)Math.clamp(Math.ceil((bottom + margin) * sy), 0, targetHeight);
        return new BackdropRegion(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    public boolean isEmpty() { return width <= 0 || height <= 0; }

    public void apply(com.mojang.renderpearl.api.commands.RenderPass pass, int targetHeight) {
        // RenderPearl uses the same bottom-left scissor coordinates as GuiRenderer.
        pass.enableScissor(x, targetHeight - y - height, width, height);
    }
}
