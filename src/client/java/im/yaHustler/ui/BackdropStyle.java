package im.yaHustler.ui;

/** std140 contract shared with figma_backdrop.fsh. All slots are fully initialized. */
public final class BackdropStyle {
    public static final int LIMIT = 32;
    public static final int BYTES = 16 * (2 + LIMIT + LIMIT / 4 * 2);
    private BackdropStyle() {}
    public static float[] pack(int width, int height, float[][] rectangles, float radius, int accent, float tint) {
        float[] data = new float[BYTES / Float.BYTES];
        int count = Math.min(LIMIT, rectangles.length);
        data[0] = width; data[1] = height; data[2] = radius; data[3] = count;
        data[4] = (accent >> 16 & 255) / 255f; data[5] = (accent >> 8 & 255) / 255f;
        data[6] = (accent & 255) / 255f; data[7] = tint;
        for (int i = 0; i < count; i++) {
            System.arraycopy(rectangles[i], 0, data, 8 + i * 4, 4);
            data[8 + LIMIT * 4 + i] = rectangles[i].length > 4 ? rectangles[i][4] : radius;
            data[8 + LIMIT * 5 + i] = rectangles[i].length > 5 ? rectangles[i][5] : 0;
        }
        return data;
    }
    public static float[] transformedSurface(float x, float y, float w, float h, float radius, org.joml.Matrix3x2fc pose) {
        float cx = pose.m00() * (x + w / 2) + pose.m10() * (y + h / 2) + pose.m20();
        float cy = pose.m01() * (x + w / 2) + pose.m11() * (y + h / 2) + pose.m21();
        float scale = (float)Math.hypot(pose.m00(), pose.m01());
        return new float[]{cx - w * scale / 2, cy - h * scale / 2, w * scale, h * scale,
            radius * scale, (float)Math.atan2(pose.m01(), pose.m00())};
    }
}
