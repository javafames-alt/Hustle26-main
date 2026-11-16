package morfeov.coliseum.render.coliseimvisual;

import net.minecraft.client.Camera;

public class FogSettingsEvent {
    private boolean changed;
    public void setChanged(boolean changed) { this.changed = changed; }
    public boolean changed() { return changed; }
    private final Camera camera;
    private final float originalDistance;
    private float startDistance;
    private float endDistance;
    private int color;
    private boolean distanceOverride;
    private boolean colorOverride;

    public boolean hasDistanceOverride() {
        return this.distanceOverride;
    }

    public float endDistance() {
        return this.endDistance;
    }

    public boolean hasColorOverride() {
        return this.colorOverride;
    }

    public float originalDistance() {
        return this.originalDistance;
    }

    public int color() {
        return this.color;
    }

    public void setDistances(float start, float end) {
        this.startDistance = start;
        this.endDistance = end;
        this.distanceOverride = true;
        this.setChanged(true);
    }

    public FogSettingsEvent(Camera camera, float distance) {
        this.camera = camera;
        this.originalDistance = distance;
        this.endDistance = distance;
    }

    public void setColor(int color) {
        this.color = color;
        this.colorOverride = true;
        this.setChanged(true);
    }

    public Camera getCamera() {
        return this.camera;
    }

    public float startDistance() {
        return this.startDistance;
    }
}
