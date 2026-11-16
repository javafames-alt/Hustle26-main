package im.yaHustler.ui;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** 26.3 entity picture-in-picture states use absolute bounds and ignore the GUI pose matrix. */
public record GuiEntityPlacement(int left, int top, int right, int bottom, float scale) {
    public static GuiEntityPlacement from(Matrix3x2fc pose, int left, int top, int right, int bottom, float modelScale) {
        Vector2f a = pose.transformPosition(left, top, new Vector2f());
        Vector2f b = pose.transformPosition(right, bottom, new Vector2f());
        float scale = (float)Math.hypot(pose.m00(), pose.m01());
        return new GuiEntityPlacement((int)Math.floor(Math.min(a.x, b.x)), (int)Math.floor(Math.min(a.y, b.y)),
            (int)Math.ceil(Math.max(a.x, b.x)), (int)Math.ceil(Math.max(a.y, b.y)), modelScale * scale);
    }
}
