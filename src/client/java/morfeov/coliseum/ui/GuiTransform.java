package morfeov.coliseum.ui;

/** The same transform is used by rendering, hit tests and backdrop masks. */
public record GuiTransform(float scale, float offsetX, float offsetY) {
    public static GuiTransform centered(float requested, int viewportW, int viewportH, int windowW, int windowH) {
        float fit = Math.min((viewportW - 12f) / windowW, (viewportH - 12f) / windowH);
        float scale = Math.max(.1f, Math.min(requested, fit));
        return new GuiTransform(scale, viewportW * (1 - scale) / 2, viewportH * (1 - scale) / 2);
    }
    public float screenX(double x) { return offsetX + (float) x * scale; }
    public float screenY(double y) { return offsetY + (float) y * scale; }
    public GuiTransform animated(float motionScale,float offsetY,float centerX,float centerY) {
        if(motionScale==1&&offsetY==0)return this;
        float nextScale=scale*motionScale;
        return new GuiTransform(nextScale,screenX(centerX)-centerX*nextScale,
            screenY(centerY)-centerY*nextScale+offsetY*scale);
    }
    public double uiX(double x) { return (x - offsetX) / scale; }
    public double uiY(double y) { return (y - offsetY) / scale; }
}
