package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.render.coliseimvisual.CameraMotionHistory;
import im.yaHustler.render.hands.HandsFx;
import im.yaHustler.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.Optional;

@ModuleInfo(id="motion_blur", name="MotionBlur", category=Category.RENDER,
    description="Размытие движения камеры с учётом глубины сцены")
public final class MotionBlur extends Module implements AutoCloseable {
    private static final int UNIFORM_SIZE = 336;
    private final NumberSetting smoothness = add(new NumberSetting("Плавность", .42, 0, 1, .01));
    private final RenderPipeline pipeline = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("hustle263", "pipeline/motion_blur"))
        .withVertexShader(Identifier.fromNamespaceAndPath("hustle263", "core/coliseimvisual/hands_fullscreen"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("hustle263", "core/coliseimvisual/motion_blur"))
        .withBindGroupLayout(HandsFx.layout(b -> HandsFx.ubo(HandsFx.sampler(HandsFx.sampler(b,"SceneSampler"),"DepthSampler"),"MotionBlur")))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15))
        .withDepthStencilState(Optional.empty()).withCull(false).build();
    private final CameraMotionHistory history = new CameraMotionHistory();
    private final Matrix4f inverseProjection = new Matrix4f(), inverseView = new Matrix4f();
    private TextureTarget output;
    private GpuBuffer uniforms;
    private CameraType cameraType;
    private Object level;
    private boolean failed;

    @Override protected void onEnable() { failed = false; reset(); }
    @Override protected void onDisable() { close(); }
    private void reset() { history.reset(); cameraType = null; level = null; }

    /** Runs before Minecraft clears world depth for hands and the 3D HUD. */
    public void render(Matrix4fc projection, Matrix4fc view) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || failed || mc.level == null || mc.player == null || mc.gui.screen() != null
                || !projection.isFinite() || !view.isFinite()) { reset(); return; }
        RenderTarget scene = mc.gameRenderer.mainRenderTarget();
        if (scene == null || scene.getColorTexture() == null || scene.getDepthTexture() == null
                || scene.getColorTextureView() == null || scene.getDepthTextureView() == null
                || scene.width <= 1 || scene.height <= 1 || scene.getColorTexture().getFormat() != GpuFormat.RGBA8_UNORM
                || Math.abs(projection.determinant()) <= 1e-8f || Math.abs(view.determinant()) <= 1e-8f) { reset(); return; }
        if (output != null && (output.width != scene.width || output.height != scene.height)) {
            output.destroyBuffers(); output = null; reset();
        }
        if (level != mc.level || cameraType != mc.options.getCameraType()) reset();
        level = mc.level; cameraType = mc.options.getCameraType();
        var camera = mc.gameRenderer.mainCamera();
        var position = camera.position();
        if (!history.record(System.nanoTime(), projection, view, position.x, position.y, position.z, camera.yRot(), camera.xRot())) return;
        float yawDelta = Mth.wrapDegrees(camera.yRot() - history.yaw());
        float pitchDelta = camera.xRot() - history.pitch();
        float movement = Math.abs(yawDelta) * .00175f + Math.abs(pitchDelta) * .00225f
            + (float)Math.min(.12, history.position().distance(position.x,position.y,position.z) * .045);
        float amount = Math.clamp(smoothness.floatValue(), 0, 1);
        float eased = amount * amount * (3 - 2 * amount);
        float activation = .035f + (1 - eased) * .075f;
        if (movement < activation * .00004f) return;
        inverseProjection.set(projection).invert(); inverseView.set(view).invert();
        if (!inverseProjection.isFinite() || !inverseView.isFinite()) { reset(); return; }
        try {
            if (output == null) output = new TextureTarget("MotionBlur output", scene.width, scene.height, GpuFormat.RGBA8_UNORM, null);
            if (uniforms == null) uniforms = HandsFx.createUniforms("MotionBlur uniforms", UNIFORM_SIZE);
            HandsFx.writeUniforms(uniforms, UNIFORM_SIZE, b -> b
                .putMat4f(inverseProjection).putMat4f(inverseView).putMat4f(history.projection()).putMat4f(history.view())
                .putVec4((float)(position.x-history.position().x()),(float)(position.y-history.position().y()),(float)(position.z-history.position().z()),eased*.65f)
                .putVec4(.4f+eased*.65f,0,0,0)
                .putVec4(4+eased*14,.3f,0,.85f)
                .putVec4(2.85f-eased*1.35f,activation,movement,5+Math.round(eased*7))
                .putVec4(Math.clamp(yawDelta*.0062f,-.24f,.24f),Math.clamp(pitchDelta*-.0074f,-.24f,.24f),scene.width,scene.height));
            HandsFx.pass("MotionBlur", pipeline, output.getColorTextureView(), pass -> {
                pass.setUniform("SceneSampler", scene.getColorTextureView(), HandsFx.linear());
                pass.setUniform("DepthSampler", scene.getDepthTextureView(), HandsFx.nearest());
                pass.setUniform("MotionBlur", uniforms);
            });
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(output.getColorTexture(), scene.getColorTexture(),0,0,0,0,0,scene.width,scene.height);
        } catch (RuntimeException error) {
            failed = true; reset();
            im.yaHustler.Hustle26.LOGGER.error("MotionBlur failed; toggle the module to retry",error);
        }
    }
    @Override public void close() {
        reset();
        if (output != null) { output.destroyBuffers(); output = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
    }
}
