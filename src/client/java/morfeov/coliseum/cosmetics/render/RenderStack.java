package morfeov.coliseum.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public class RenderStack {
    private PoseStack stack;

    public void update(PoseStack stack) {
        this.stack = stack;
    }

    public void push() {
        this.stack.pushPose();
    }

    public void pop() {
        this.stack.popPose();
    }

    public PoseStack get() {
        return this.stack;
    }

    public void translate(float x, float y, float z) {
        this.stack.translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        this.stack.scale(x, y, z);
    }

    public void rotateX(float degrees) {
        this.stack.rotate(Axis.XP.rotationDegrees(degrees));
    }

    public void rotateY(float degrees) {
        this.stack.rotate(Axis.YP.rotationDegrees(degrees));
    }

    public void rotateZ(float degrees) {
        this.stack.rotate(Axis.ZP.rotationDegrees(degrees));
    }

    public void rotate(float x, float y, float z) {
        if (z != 0.0F) {
            this.rotateZ(z);
        }
        if (y != 0.0F) {
            this.rotateY(y);
        }
        if (x != 0.0F) {
            this.rotateX(x);
        }
    }

    public void rotateDegrees(float x, float y, float z) {
        this.rotate(x, y, z);
    }

    public void rotateXDegrees(float degrees) {
        this.rotateX(degrees);
    }

    public void rotateYDegrees(float degrees) {
        this.rotateY(degrees);
    }

    public void rotateZDegrees(float degrees) {
        this.rotateZ(degrees);
    }
}
