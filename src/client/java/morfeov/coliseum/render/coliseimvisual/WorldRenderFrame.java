package morfeov.coliseum.render.coliseimvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class WorldRenderFrame {
    private final DeltaTracker deltaTracker;
    private final PoseStack poseStack;
    private final Object renderContext;

    private final Vec3 cameraPosition;
    private final float partialTick;
    private final Matrix4f projectionMatrix;
    private final Matrix4f viewMatrix;

    public float partialTick() {
        return this.partialTick;
    }

    public DeltaTracker getDeltaTracker() {
        return this.deltaTracker;
    }

    public PoseStack getPoseStack() {
        return this.poseStack;
    }

    public Matrix4f viewMatrix() {
        return this.viewMatrix;
    }

    public WorldRenderFrame(DeltaTracker deltaTracker, PoseStack poseStack, Object renderContext, Vec3 cameraPosition, float partialTick, Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        this.deltaTracker = deltaTracker;
        this.poseStack = poseStack;
        this.renderContext = renderContext;
        this.cameraPosition = cameraPosition;
        this.partialTick = partialTick;
        this.projectionMatrix = projectionMatrix;
        this.viewMatrix = viewMatrix;
    }

    public Object getObject() {
        return this.renderContext;
    }

    public Matrix4f projectionMatrix() {
        return this.projectionMatrix;
    }

    public Vec3 cameraPosition() {
        return this.cameraPosition;
    }
}
