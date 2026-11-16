package im.yaHustler.cosmetics.extra;

import org.joml.Vector3f;

public class Keyframe {
    public final float time;
    public final Vector3f value;

    public Keyframe(float time, float x, float y, float z) {
        this.time = time;
        this.value = new Vector3f(x, y, z);
    }
}
