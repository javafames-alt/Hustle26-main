package morfeov.coliseum.ui;

import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.render.CustomCrystal;
import morfeov.coliseum.render.coliseimvisual.CustomCrystalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

public final class CustomCrystalTest {
    @TempDir Path directory;

    @Test void appearanceOptionsSurviveProfileReload() throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        CustomCrystal module = registry.register(new CustomCrystal());
        assertFalse(module.enabled());
        assertEquals(List.of("Шейдер", "Заливка"), module.mode.modes());
        module.mode.set("Заливка"); module.size.set(1.75); module.outline.set(false); module.opacity.set(43.0);
        Path config = directory.resolve("crystal.json");
        ClientConfig.saveProfile(config, registry);
        module.mode.set("Шейдер"); module.size.set(1.0); module.outline.set(true); module.opacity.set(85.0);
        assertTrue(new ClientConfig(config, registry).load());
        assertEquals("Заливка", module.mode.get());
        assertEquals(1.75, module.size.get());
        assertFalse(module.outline.enabled());
        assertEquals(43.0, module.opacity.get());
        assertFalse(module.outlineWidth.visible());
    }

    @Test void maskHasTwoGlassShellsAndRestoresModelParts() {
        EndCrystalModel model = new EndCrystalModel(EndCrystalModel.createBodyLayer().bakeRoot());
        model.outerGlass.y = 17;
        model.outerGlass.xRot = .21f;
        var before = snapshot(model);
        EndCrystalRenderState state = new EndCrystalRenderState();
        state.ageInTicks = 57.25f; state.showsBottom = true;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(16384)) {
            BufferBuilder vertices = new BufferBuilder(storage, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            CustomCrystalModel.renderMask(model, state, new PoseStack(), vertices, 0, 0);
            try (MeshData mesh = vertices.build()) {
                assertNotNull(mesh);
                assertEquals(48, mesh.drawState().vertexCount());
            }
        }
        assertSnapshots(before, snapshot(model));
    }

    @Test void throwingVertexConsumerStillRestoresSharedModel() {
        EndCrystalModel model = new EndCrystalModel(EndCrystalModel.createBodyLayer().bakeRoot());
        model.base.visible = false;
        model.innerGlass.zRot = .7f;
        var before = snapshot(model);
        IllegalStateException error = new IllegalStateException("Interrupted model draw");
        VertexConsumer consumer = (VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> { throw error; });
        assertSame(error, assertThrows(IllegalStateException.class, () ->
            CustomCrystalModel.renderMask(model, new EndCrystalRenderState(), new PoseStack(), consumer, 0, 0)));
        assertSnapshots(before, snapshot(model));
    }

    @Test void scalingKeepsEntityAnchorAndDoesNotScaleWorldPosition() {
        Matrix4f submitted = new Matrix4f().translate(120, 64, -90).scale(2).translate(0, -.5f, 0);
        Vector3f anchor = submitted.transformPosition(new Vector3f(0, .5f, 0));
        Vector3f point = submitted.transformPosition(new Vector3f(.6f, 1.2f, -.3f));
        for (float size : new float[]{.2f, 1, 3}) {
            Matrix4f scaled = CustomCrystalModel.scaledPose(submitted, size);
            Vector3f scaledAnchor = scaled.transformPosition(new Vector3f(0, .5f, 0));
            assertEquals(0, scaledAnchor.distance(anchor), .0001);
            Vector3f scaledPoint = scaled.transformPosition(new Vector3f(.6f, 1.2f, -.3f));
            assertEquals(point.distance(anchor) * size, scaledPoint.distance(anchor), .0001);
        }
    }

    @Test void mixinTargetsTheActualMinecraftCrystalModelSubmission() throws Exception {
        String submit = "(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
        String modelCall = "(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V";
        ClassNode node = new ClassNode();
        try (var stream = EndCrystalRenderer.class.getResourceAsStream("EndCrystalRenderer.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("submit") && m.desc.equals(submit)).findFirst().orElseThrow();
        int matches = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/client/renderer/SubmitNodeCollector")
                && call.name.equals("submitModel") && call.desc.equals(modelCall)) matches++;
        assertEquals(1, matches);
        try (var stream = getClass().getResourceAsStream("/coliseum.mixins.json")) {
            assertNotNull(stream);
            assertTrue(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("CustomCrystalRendererMixin"));
        }
    }

    private static List<float[]> snapshot(EndCrystalModel model) {
        return model.allParts().stream().map(CustomCrystalTest::snapshot).toList();
    }
    private static float[] snapshot(ModelPart p) {
        return new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale, p.visible ? 1 : 0};
    }
    private static void assertSnapshots(List<float[]> expected, List<float[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertArrayEquals(expected.get(i), actual.get(i), .00001f);
    }

    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass(CustomCrystalTest.class)).build());
        var summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out, true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getTestsSucceededCount() != 5 || summary.getTotalFailureCount() != 0)
            throw new AssertionError("CustomCrystal verification failed");
    }
}
