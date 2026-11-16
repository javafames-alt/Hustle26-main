package morfeov.coliseum.ui;

/** Independent even/odd columns used by the donor's ModuleGridSection. */
public final class ModuleGridLayout {
    public record Placement(int column, int top, int height) {}
    private ModuleGridLayout() {}
    public static Placement[] pack(int[] heights, int gap) {
        Placement[] result = new Placement[heights.length];
        int[] top = new int[2];
        for (int i = 0; i < heights.length; i++) {
            int column = i % 2, height = Math.max(0, heights[i]);
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
