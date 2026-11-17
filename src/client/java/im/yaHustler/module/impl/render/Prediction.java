package im.yaHustler.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import im.yaHustler.mixin.TagsGameRendererInvoker;
import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.render.weather.WeatherPass;
import im.yaHustler.render.weather.WeatherPipelines;
import im.yaHustler.setting.BooleanSetting;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.GuiVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@ModuleInfo(id = "prediction", name = "Prediction", category = Category.RENDER,
    description = "Показывает траекторию снарядов и время до приземления")
public final class Prediction extends Module implements AutoCloseable {
    private final BooleanSetting pearls = add(new BooleanSetting("Эндер пёрлы", true));
    private final BooleanSetting arrows = add(new BooleanSetting("Стрелы", true));
    private final BooleanSetting tridents = add(new BooleanSetting("Трезубцы", true));
    private final BooleanSetting items = add(new BooleanSetting("Предметы", true));
    private final WeatherPass pass = new WeatherPass();
    private ClientLevel world;
    private List<Path> paths = List.of();

    private record Path(List<Vec3> points, ItemStack item, int color, int impactTick) {}

    @Override public void tick(Minecraft client) {
        world = client.level;
        if (world == null || client.player == null) {
            paths = List.of();
            return;
        }
        List<Path> next = new ArrayList<>();
        for (Entity entity : world.getEntities(client.player, client.player.getBoundingBox().inflate(64))) {
            if (entity.isNoGravity() || entity.onGround() || entity.isInWater() || entity.getDeltaMovement().lengthSqr() < 1e-6) continue;
            if (entity instanceof ThrownTrident) {
                if (tridents.enabled()) next.add(simulate(entity, new ItemStack(Items.TRIDENT), 0x5ab4ff, .05, .99, .99));
            } else if (entity instanceof AbstractArrow) {
                if (arrows.enabled()) next.add(simulate(entity, new ItemStack(Items.ARROW), 0xffdc78, .05, .99, .6));
            } else if (entity instanceof ThrownEnderpearl pearl) {
                if (pearls.enabled()) next.add(simulate(entity, pearl.getItem(), 0x8cffc8, .03, .99, .8));
            } else if (entity instanceof ItemEntity item && items.enabled()) {
                next.add(simulate(entity, item.getItem(), 0xbe96ff, .04, .98, .8));
            }
        }
        paths = next;
    }

    private Path simulate(Entity entity, ItemStack item, int color, double gravity, double airDrag, double waterDrag) {
        Vec3 point = entity.position();
        Vec3 velocity = entity.getDeltaMovement();
        List<Vec3> points = new ArrayList<>();
        points.add(point);
        for (int ticks = 1; ticks <= 320; ticks++) {
            Vec3 next = point.add(velocity);
            if (!world.hasChunkAt(BlockPos.containing(next))) break;
            var block = world.clip(new ClipContext(point, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            boolean hit = block.getType() != HitResult.Type.MISS;
            if (hit) next = block.getLocation();
            if (!(entity instanceof ItemEntity)) {
                var entityHit = ProjectileUtil.getEntityHitResult(world, entity, point, next, new AABB(point, next).inflate(1),
                    other -> other.isPickable() && !other.isSpectator()
                        && !(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner() == other), .3f);
                if (entityHit != null) {
                    next = entityHit.getLocation();
                    hit = true;
                }
            }
            if (!hit && next.y < world.getMinY() && point.y >= world.getMinY()) {
                next = point.lerp(next, (point.y - world.getMinY()) / (point.y - next.y));
                hit = true;
            }
            points.add(next);
            if (hit) return new Path(points, item, color, ticks);
            if (next.y < world.getMinY()) break;
            point = next;
            double drag = world.getFluidState(BlockPos.containing(point)).is(FluidTags.WATER) ? waterDrag : airDrag;
            velocity = velocity.scale(drag).add(0, -gravity, 0);
        }
        return new Path(points, item, color, 0);
    }

    public void render(Matrix4f projection, Matrix4f view, Vec3 camera) {
        if (!enabled() || world != Minecraft.getInstance().level || paths.isEmpty() || camera == null) return;
        var vertices = pass.begin(false);
        for (Path path : paths) {
            for (int i = 1; i < path.points.size(); i++) {
                Vec3 a = path.points.get(i - 1).subtract(camera), b = path.points.get(i).subtract(camera);
                Vec3 width = b.subtract(a).cross(a.add(b)).normalize().scale(.012);
                int startColor = path.color | (8 + 220 * (i - 1) / path.points.size()) << 24;
                int endColor = path.color | (8 + 220 * i / path.points.size()) << 24;
                vertex(vertices, a.add(width), startColor);
                vertex(vertices, a.subtract(width), startColor);
                vertex(vertices, b.subtract(width), endColor);
                vertex(vertices, b.add(width), endColor);
            }
        }
        pass.draw(vertices, WeatherPipelines.WORLD_RIBBON, new Matrix4f(projection).mul(view), null);
    }

    private static void vertex(com.mojang.blaze3d.vertex.BufferBuilder vertices, Vec3 point, int color) {
        vertices.addVertex((float) point.x, (float) point.y, (float) point.z).setColor(color);
    }

    public void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (!enabled() || world != mc.level || paths.isEmpty() || mc.gui.hud.isHidden() || GuiVisibility.hidesHud()) return;
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera.pos == null || camera.projectionMatrix == null) return;
        var bob = new PoseStack();
        var renderer = (TagsGameRendererInvoker) mc.gameRenderer;
        renderer.hustle263$tagsHurt(camera, bob);
        if (mc.gameRenderer.gameRenderState().optionsRenderState.bobView) renderer.hustle263$tagsBob(camera, bob);
        var matrix = new Matrix4f(camera.projectionMatrix).mul(bob.last().pose()).mul(camera.viewRotationMatrix);
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        for (Path path : paths) {
            if (path.impactTick < 2) continue;
            Vec3 position = path.points.getLast().subtract(camera.pos);
            var point = matrix.transform(new Vector4f((float) position.x, (float) position.y, (float) position.z, 1));
            if (!point.isFinite() || point.w <= 0 || Math.abs(point.z) > point.w) continue;
            int x = Math.round((1 + point.x / point.w) * width / 2);
            int y = Math.round((1 - point.y / point.w) * height / 2);
            if (x < 12 || x >= width - 12 || y < 12 || y >= height - 12) continue;
            String time = String.format(Locale.ROOT, "%.1f", (path.impactTick - delta.getGameTimeDeltaPartialTick(false)) / 20);
            Draw.roundedF(graphics, x - 10, y - 10, 20, 20, 8, 0xcc141414);
            graphics.item(path.item, x - 8, y - 8);
            graphics.text(mc.font, time, x - mc.font.width(time) / 2, y + 11, 0xffffffff, true);
        }
    }

    @Override protected void onDisable() { close(); }
    @Override public void close() {
        paths = List.of();
        world = null;
        pass.close();
    }
}
