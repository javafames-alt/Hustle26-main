package morfeov.coliseum.cosmetics.extra;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public class BoneTimeline {
    public final List<Keyframe> rotationKeyframes = new ArrayList<>();
    public final List<Keyframe> positionKeyframes = new ArrayList<>();

    public void evaluateRotation(float t, Vector3f out) {
        this.interpolate(this.rotationKeyframes, t, out);
    }

    public void evaluatePosition(float t, Vector3f out) {
        this.interpolate(this.positionKeyframes, t, out);
    }

    void interpolate(List<Keyframe> frames, float t, Vector3f out) {
        if (!frames.isEmpty()) {
            if (frames.size() != 1 && !(t <= frames.get(0).time)) {
                if (t >= frames.get(frames.size() - 1).time) {
                    out.set(frames.get(frames.size() - 1).value);
                } else {
                    for (int i = 0; i < frames.size() - 1; i++) {
                        Keyframe k1 = frames.get(i);
                        Keyframe k2 = frames.get(i + 1);
                        if (t >= k1.time && t <= k2.time) {
                            float factor = (t - k1.time) / (k2.time - k1.time);
                            out.x = k1.value.x + (k2.value.x - k1.value.x) * factor;
                            out.y = k1.value.y + (k2.value.y - k1.value.y) * factor;
                            out.z = k1.value.z + (k2.value.z - k1.value.z) * factor;
                            return;
                        }
                    }
                }
            } else {
                out.set(frames.get(0).value);
            }
        }
    }
}
