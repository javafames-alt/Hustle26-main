package morfeov.coliseum.ui;

/** Shared bounds for wardrobe drawing, clipping and pointer events. */
public final class CosmeticsLayout {
    public static final int CATEGORY_COUNT = 8, COLUMNS = 3, CARD_HEIGHT = 72, GAP = 5;
    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public float centerX() { return x + w / 2f; }
        public float centerY() { return y + h / 2f; }
        public boolean contains(double px, double py) {
            return w > 0 && h > 0 && px >= x && py >= y && px < right() && py < bottom();
        }
        public Rect intersect(Rect other) {
            int left = Math.max(x, other.x), top = Math.max(y, other.y);
            return new Rect(left, top, Math.max(0, Math.min(right(), other.right()) - left),
                Math.max(0, Math.min(bottom(), other.bottom()) - top));
        }
    }
    public final Rect bounds, catalog, preview, grid, clear, equipped, autoRotate, resetView,
        layers, zoomOut, zoomIn, zoomTrack, model, scrollbar;
    public CosmeticsLayout(int x, int y, int w, int h) {
        bounds = new Rect(x, y, w, h);
        int previewWidth = Math.clamp(w / 3, 110, 126), catalogWidth = w - previewWidth - 10;
        catalog = new Rect(x, y, catalogWidth, h);
        preview = new Rect(x + catalogWidth + 10, y, previewWidth, h);
        grid = new Rect(x, y + 73, catalogWidth - 7, h - 101);
        scrollbar = new Rect(grid.right() + 2, grid.y(), 3, grid.h());
        int half = (catalogWidth - 5) / 2;
        clear = new Rect(x, y + h - 20, half, 18);
        equipped = new Rect(x + half + 5, y + h - 20, catalogWidth - half - 5, 18);
        autoRotate = new Rect(preview.x() + 7, y + h - 23, 62, 17);
        resetView = new Rect(autoRotate.right() + 4, y + h - 23, previewWidth - 80, 17);
        layers = new Rect(preview.x() + 7, y + h - 66, previewWidth - 14, 17);
        zoomOut = new Rect(preview.x() + 7, y + h - 44, 15, 15);
        zoomIn = new Rect(preview.right() - 22, y + h - 44, 15, 15);
        zoomTrack = new Rect(zoomOut.right() + 5, zoomOut.y(), zoomIn.x() - zoomOut.right() - 10, 15);
        model = new Rect(preview.x() + 3, y + 30, previewWidth - 6, h - 112);
    }
    public Rect category(int index) {
        if (index < 0 || index >= CATEGORY_COUNT) throw new IllegalArgumentException("Category: " + index);
        int gap = 4, width = (catalog.w() - gap * 3) / 4;
        int col = index % 4;
        return new Rect(catalog.x() + col * (width + gap), catalog.y() + 25 + index / 4 * 22,
            col == 3 ? catalog.right() - (catalog.x() + col * (width + gap)) : width, 18);
    }
    public Rect card(int index, int scroll) {
        int width = (grid.w() - GAP * (COLUMNS - 1)) / COLUMNS;
        return new Rect(grid.x() + index % COLUMNS * (width + GAP),
            grid.y() + index / COLUMNS * (CARD_HEIGHT + GAP) - scroll, width, CARD_HEIGHT);
    }
    public Rect skinAction(int index) {
        int width = (catalog.w() - 8) / 3;
        int left = catalog.x() + index * (width + 4);
        return new Rect(left, clear.y(), index == 2 ? catalog.right() - left : width, clear.h());
    }
    public int maxScroll(int entries) {
        int rows = (entries + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows * (CARD_HEIGHT + GAP) - (rows == 0 ? 0 : GAP) - grid.h());
    }
}
