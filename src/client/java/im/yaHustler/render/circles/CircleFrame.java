package im.yaHustler.render.circles;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Camera-relative world geometry plus the actual projection used by the current frame. */
public record CircleFrame(DeltaTracker deltaTracker, PoseStack pose, Vec3 cameraPosition,
                          Matrix4f projection, Matrix4f view) {}
