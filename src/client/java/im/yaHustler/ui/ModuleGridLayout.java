package im.yaHustler.ui;

/** Packs each column independently so an open card does not shift its neighbors sideways. */
public final class ModuleGridLayout {
    public record Placement(int column, int top, int height) {}
    private ModuleGridLayout() {}
    public static Placement[] pack(int[] heights, int gap) {
        return pack(heights, gap, 2);
    }
    public static Placement[] pack(int[] heights, int gap, int columns) {
        Placement[] result = new Placement[heights.length];
        int[] top = new int[columns];
        for (int i = 0; i < heights.length; i++) {
            int column = i % columns, height = Math.max(0, heights[i]);
            result[i] = new Placement(column, top[column], height);
            top[column] += height + gap;
        }
        return result;
    }
    public static int contentHeight(Placement[] cards) {
        int bottom = 0;
        for (Placement card : cards) bottom = Math.max(bottom, card.top + card.height);
        return bottom;
    }
}
