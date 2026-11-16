package morfeov.coliseum.module.impl.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.render.coliseimvisual.CustomCrystalModel;
import morfeov.coliseum.render.coliseimvisual.TransientGpuBuffers;
import morfeov.coliseum.render.coliseimvisual.WorldRenderMatrices;
import morfeov.coliseum.render.hands.HandsFx;
import morfeov.coliseum.setting.BooleanSetting;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Readable adaptation of the original implementation's CustomCrystal for the 26.3 RenderPearl renderer. */
@ModuleInfo(id = "custom_crystal", name = "CustomCrystal", category = Category.RENDER,
    description = "Шейдер, заливка, размер и свечение кристаллов цветом темы")
public final class CustomCrystal extends Module implements AutoCloseable {
    private static final Identifier CRYSTAL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    public final ModeSetting mode = add(new ModeSetting("Режим", "Шейдер", "Шейдер", "Заливка"));
    public final NumberSetting size = add(new NumberSetting("Размер", 1, .2, 3, .05));
    public final BooleanSetting outline = add(new BooleanSetting("Обводка темы", true));
    public final NumberSetting opacity = add(new NumberSetting("Прозрачность", 85, 10, 100, 1));
    public final NumberSetting outlineWidth = add(new NumberSetting("Ширина оутлайна", 2.5, .5, 8, .25).visibleWhen(outline::enabled));
    public final NumberSetting outlineOpacity = add(new NumberSetting("Прозрачность оутлайна", 65, 5, 100, 1).visibleWhen(outline::enabled));
    public final NumberSetting glow = add(new NumberSetting("Сила свечения", .7, .2, 2, .05).visibleWhen(outline::enabled));

    private final RenderPipeline maskPipeline = RenderPipeline.builder()
        .withLocation(id("pipeline/custom_crystal_mask"))
        .withVertexShader(id("core/coliseimvisual/custom_crystal_mask"))
        .withFragmentShader(id("core/coliseimvisual/custom_crystal_mask"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(HandsFx.layout(b -> HandsFx.sampler(b, "CrystalSampler")))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15))
        .withDepthStencilState(Optional.empty()).withCull(false).build();
    private final RenderPipeline compositePipeline = RenderPipeline.builder()
        .withLocation(id("pipeline/custom_crystal_composite"))
        .withVertexShader(id("core/coliseimvisual/hands_fullscreen"))
        .withFragmentShader(id("core/coliseimvisual/custom_crystal_composite"))
        .withBindGroupLayout(HandsFx.layout(b -> {
            HandsFx.sampler(b, "MaskSampler");
            HandsFx.ubo(b, "CustomCrystalComposite");
        }))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.empty()).withCull(false).build();

    private record Draw(EndCrystalModel model, float age, boolean showsBottom, Matrix4f pose, int light, int overlay) {}
    private final List<Draw> queued = new ArrayList<>();
    private TextureTarget mask;
    private ByteBufferBuilder storage;
    private GpuBuffer uniforms;
    private boolean failed;

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("coliseum", path); }
    public boolean active() { return enabled() && !failed; }
    public void beginFrame() { queued.clear(); }

    public void queue(EndCrystalModel model, EndCrystalRenderState state, PoseStack pose, int light, int overlay) {
        // Snapshot the state: Minecraft reuses model/state instances between deferred submissions.
        queued.add(new Draw(model, state.ageInTicks, state.showsBottom,
            CustomCrystalModel.scaledPose(pose.last().pose(), size.floatValue()), light, overlay));
    }

    @Override protected void onEnable() { failed = false; beginFrame(); }
    @Override protected void onDisable() { close(); }

    public void render() {
        if (!active() || queued.isEmpty()) { beginFrame(); return; }
        RenderTarget scene = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (scene == null || scene.getColorTextureView() == null || scene.width < 1 || scene.height < 1) { beginFrame(); return; }
        try {
            if (mask != null && (mask.width != scene.width || mask.height != scene.height)) {
                mask.destroyBuffers(); mask = null;
            }
            if (mask == null) mask = new TextureTarget("CustomCrystal mask", scene.width, scene.height, GpuFormat.RGBA8_UNORM, null);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorTexture(), new Vector4f(0));
            drawMask();
            if (uniforms == null) uniforms = HandsFx.createUniforms("CustomCrystal composite", 64);
            int accent = Theme.accent();
            HandsFx.writeUniforms(uniforms, 64, b -> b
                .putVec4((accent >> 16 & 255) / 255f, (accent >> 8 & 255) / 255f, (accent & 255) / 255f, opacity.floatValue() / 100)
                .putVec4(HandsFx.time(), outline.enabled() ? 1 : 0, mode.is("Шейдер") ? 1 : 0, 0)
                .putVec4(outlineWidth.floatValue(), outlineOpacity.floatValue() / 100, glow.floatValue(), 0)
                .putVec4(scene.width, scene.height, 0, 0));
            HandsFx.pass("CustomCrystal composite", compositePipeline, scene.getColorTextureView(), pass -> {
                pass.setUniform("MaskSampler", mask.getColorTextureView(), HandsFx.linear());
                pass.setUniform("CustomCrystalComposite", uniforms);
            });
        } catch (RuntimeException error) {
            failed = true;
            Coliseum.LOGGER.error("CustomCrystal rendering failed; vanilla rendering will resume", error);
        } finally { beginFrame(); }
    }

    private void drawMask() {
        if (storage == null) storage = new ByteBufferBuilder(65536);
        BufferBuilder vertices = new BufferBuilder(storage, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
        for (Draw command : queued) {
            EndCrystalRenderState state = new EndCrystalRenderState();
            state.ageInTicks = command.age;
            state.showsBottom = command.showsBottom;
            PoseStack pose = new PoseStack();
            pose.mulPose(new Matrix4f(WorldRenderMatrices.viewMatrix()).mul(command.pose));
            CustomCrystalModel.renderMask(command.model, state, pose, vertices, command.light, command.overlay);
        }
        try (MeshData mesh = vertices.build()) {
            if (mesh == null) return;
            var draw = mesh.drawState();
            var indices = RenderSystem.getSequentialBuffer(draw.primitiveTopology());
            var indexBuffer = indices.getBuffer(draw.indexCount());
            var vertexBuffer = TransientGpuBuffers.uploadVertices(mesh.vertexBuffer(), draw.format().getVertexSize());
            var texture = Minecraft.getInstance().getTextureManager().getTexture(CRYSTAL_TEXTURE);
            try (var pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "CustomCrystal isolated mask", mask.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(maskPipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("CrystalSampler", texture.getTextureView(), texture.getSampler());
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(draw.indexCount(), 1, 0, 0, 0);
            }
        }
    }

    @Override public void close() {
        beginFrame();
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (storage != null) { storage.close(); storage = null; }
    }
}
