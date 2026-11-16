package im.yaHustler.bot.view;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import im.yaHustler.bot.world.BotWorld;
import im.yaHustler.render.hands.HandsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class BotWorldView {
    private record Face(float[] vertices, int color) {}
    private record Projected(float[] vertices, int color, float depth) {}
    private final List<Face> mesh = new ArrayList<>();
    private final RandomSource random = RandomSource.create(0);
    private BotWorld boundWorld;
    private BlockPos center;
    private long revision = -1, lastBuild;
    private RenderPipeline pipeline;

    public void extract(GuiGraphicsExtractor graphics, BotWorld world, PositionMoveRotation camera, int x, int y, int width, int height) {
        var eye = camera.position().add(0, 1.62, 0);
        var current = BlockPos.containing(eye);
        long now = System.nanoTime();
        if (boundWorld != world || now - lastBuild > 200_000_000L && (world.revision() != revision || center == null || center.distSqr(current) > 16)) {
            build(world, current);
            lastBuild = now;
        }
        double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        float focal = (float) (height / (2 * Math.tan(Math.toRadians(80) / 2)));
        var visible = new ArrayList<Projected>();
        for (var face : mesh) {
            var polygon = new ArrayList<float[]>(5);
            for (int i = 0; i < 4; i++) {
                int j = i * 5;
                double dx = face.vertices[j] - eye.x, dy = face.vertices[j + 1] - eye.y, dz = face.vertices[j + 2] - eye.z;
                double forward = -dx * sy + dz * cy;
                polygon.add(new float[]{(float) (dx * cy + dz * sy), (float) (dy * cp + forward * sp), (float) (forward * cp - dy * sp), face.vertices[j + 3], face.vertices[j + 4]});
            }
            polygon = clipNear(polygon);
            if (polygon.size() < 3) continue;
            for (int i = 1; i + 1 < polygon.size(); i++) {
                float[][] triangle = {polygon.getFirst(), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1)};
                var vertices = new float[20];
                float depth = 0;
                for (int v = 0; v < 4; v++) {
                    float[] point = triangle[v];
                    vertices[v * 5] = x + width / 2f + point[0] * focal / point[2];
                    vertices[v * 5 + 1] = y + height / 2f - point[1] * focal / point[2];
                    vertices[v * 5 + 2] = point[2];
                    vertices[v * 5 + 3] = point[3];
                    vertices[v * 5 + 4] = point[4];
                    depth += point[2];
                }
                visible.add(new Projected(vertices, face.color, depth / 4));
            }
        }
        visible.sort(Comparator.comparingDouble(Projected::depth).reversed());
        var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        graphics.guiRenderState.addGuiElement(new MeshState(pipeline(), TextureSetup.singleTexture(texture.getTextureView(), HandsFx.linear()), new ScreenRectangle(x, y, width, height), List.copyOf(visible)));
    }

    private void build(BotWorld world, BlockPos position) {
        mesh.clear();
        boundWorld = world;
        center = position;
        revision = world.revision();
        var client = Minecraft.getInstance();
        var parts = new ArrayList<BlockStateModelPart>();
        for (var block : BlockPos.betweenClosed(position.offset(-20, -14, -20), position.offset(20, 14, 20))) {
            var state = world.getBlockState(block);
            if (state.isAir()) continue;
            parts.clear();
            random.setSeed(state.getSeed(block));
            client.getModelManager().getBlockStateModelSet().get(state).collectParts(random, parts);
            for (var part : parts) {
                for (var direction : Direction.values()) {
                    if (Block.shouldRenderFace(state, world.getBlockState(block.relative(direction)), direction))
                        for (var quad : part.getQuads(direction)) add(quad, block, state);
                }
                for (var quad : part.getQuads(null)) add(quad, block, state);
            }
        }
    }
    private void add(BakedQuad quad, BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        var vertices = new float[20];
        for (int i = 0; i < 4; i++) {
            var vertex = quad.position(i);
            vertices[i * 5] = position.getX() + vertex.x();
            vertices[i * 5 + 1] = position.getY() + vertex.y();
            vertices[i * 5 + 2] = position.getZ() + vertex.z();
            vertices[i * 5 + 3] = UVPair.unpackU(quad.packedUV(i));
            vertices[i * 5 + 4] = UVPair.unpackV(quad.packedUV(i));
        }
        int color = quad.materialInfo().isTinted() ? Minecraft.getInstance().getBlockColors().getTintSource(state, quad.materialInfo().tintIndex()).color(state) : -1;
        float shade = switch (quad.direction()) { case UP -> 1; case DOWN -> .55f; case NORTH, SOUTH -> .8f; case EAST, WEST -> .65f; };
        mesh.add(new Face(vertices, ARGB.scaleRGB(ARGB.opaque(color), shade)));
    }
    private static ArrayList<float[]> clipNear(ArrayList<float[]> points) {
        var result = new ArrayList<float[]>(5);
        float[] previous = points.getLast();
        for (var point : points) {
            boolean inside = point[2] >= .05f, previousInside = previous[2] >= .05f;
            if (inside != previousInside) {
                float t = (.05f - previous[2]) / (point[2] - previous[2]);
                var intersection = new float[5];
                for (int i = 0; i < 5; i++) intersection[i] = previous[i] + (point[i] - previous[i]) * t;
                intersection[2] = .05f;
                result.add(intersection);
            }
            if (inside) result.add(point);
            previous = point;
        }
        return result;
    }
    private RenderPipeline pipeline() {
        if (pipeline != null) return pipeline;
        var base = RenderPipelines.GUI_TEXTURED;
        var builder = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("hustle263", "pipeline/bot_view"))
            .withVertexShader(Identifier.fromNamespaceAndPath("hustle263", "core/bot_view"))
            .withFragmentShader(base.getShaders().get(ShaderType.FRAGMENT)).withCull(false)
            .withPrimitiveTopology(base.getPrimitiveTopology()).withDepthStencilState(java.util.Optional.ofNullable(base.getDepthStencilState()));
        base.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        for (int i = 0; i < base.getColorTargetStates().size(); i++) builder.withColorTargetState(i, base.getColorTargetStates().get(i));
        for (int i = 0; i < base.getVertexFormatBindings().size(); i++) builder.withVertexBinding(i, base.getVertexFormatBindings().get(i));
        pipeline = builder.build();
        return pipeline;
    }
    private record MeshState(RenderPipeline pipeline, TextureSetup textureSetup, ScreenRectangle bounds, List<Projected> faces) implements GuiElementRenderState {
        @Override public ScreenRectangle scissorArea() { return bounds; }
        @Override public void buildVertices(VertexConsumer consumer) {
            for (var face : faces) for (int i = 0; i < 4; i++) {
                int offset = i * 5;
                consumer.addVertex(face.vertices[offset], face.vertices[offset + 1], face.vertices[offset + 2]).setUv(face.vertices[offset + 3], face.vertices[offset + 4]).setColor(face.color);
            }
        }
    }
}
