package im.yaHustler.command;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.mixin.TagsGameRendererInvoker;
import im.yaHustler.module.impl.themes.Theme;
import im.yaHustler.ui.Draw;
import im.yaHustler.ui.GuiVisibility;
import im.yaHustler.ui.InterfaceAssets;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class GpsMarker {
    private static boolean enabled;
    private static double targetX, targetZ, anchorY = Double.NaN;
    private static long updatedAt;
    private record PlayerTarget(String name, double x, double z) {}
    private static final Map<String, PlayerTarget> playerTargets = new LinkedHashMap<>();
    private GpsMarker() {}
    public static void set(double x, double z) { targetX = x; targetZ = z; enabled = true; anchorY = Double.NaN; }
    public static void clear() { enabled = false; anchorY = Double.NaN; }
    public static void setPlayerTarget(String name, double x, double z) {
        playerTargets.clear();
        playerTargets.put(name.toLowerCase(Locale.ROOT), new PlayerTarget(name, x, z));
    }
    public static boolean removePlayerTarget(String name) { return playerTargets.remove(name.toLowerCase(Locale.ROOT)) != null; }

    public static void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if ((!enabled && playerTargets.isEmpty()) || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || GuiVisibility.hidesHud()) return;
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera.pos == null) return;
        long now = System.nanoTime();
        double eyeY = mc.player.getEyePosition(delta.getGameTimeDeltaPartialTick(false)).y;
        if (Double.isNaN(anchorY)) anchorY = eyeY;
        else anchorY += (eyeY - anchorY) * (1 - Math.exp(-2.5 * Math.clamp((now - updatedAt) / 1e9, 0, .1)));
        updatedAt = now;
        if (enabled) drawMarker(g, mc, targetX, targetZ, null, now);
        for (PlayerTarget target : playerTargets.values()) drawMarker(g, mc, target.x(), target.z(), target.name(), now);
    }

    private static void drawMarker(GuiGraphicsExtractor g, Minecraft mc, double targetX, double targetZ, String name, long now) {
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        var bob = new PoseStack();
        var renderer = (TagsGameRendererInvoker) mc.gameRenderer;
        renderer.hustle263$tagsHurt(camera, bob);
        if (mc.gameRenderer.gameRenderState().optionsRenderState.bobView) renderer.hustle263$tagsBob(camera, bob);
        var matrix = new Matrix4f(camera.projectionMatrix).mul(bob.last().pose()).mul(camera.viewRotationMatrix);
        var point = matrix.transform(new Vector4f((float)(targetX - camera.pos.x), (float)(anchorY - camera.pos.y), (float)(targetZ - camera.pos.z), 1));
        float width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        if (width < 40 || height < 40) return;
        float x = 0, y = 0;
        boolean visible = point.isFinite() && point.w > 0 && point.z >= -point.w && point.z <= point.w;
        if (visible) {
            x = (point.x / point.w + 1) * width / 2;
            y = (1 - point.y / point.w) * height / 2;
            visible = x >= 20 && x <= width - 20 && y >= 20 && y <= height - 20;
        }
        double dx = targetX - mc.player.getX(), dz = targetZ - mc.player.getZ();
        String distance = (long)Math.sqrt(dx * dx + dz * dz) + "m";
        int color = name == null ? Theme.accent() : 0xffff4753;
        if (!visible) {
            float angle = Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(dz, dx)) - 90 - mc.gameRenderer.mainCamera().yRot());
            float directionX = (float)Math.sin(Math.toRadians(angle)), directionY = (float)-Math.cos(Math.toRadians(angle));
            float scaleX = Math.abs(directionX) < .0001f ? Float.MAX_VALUE : (width / 2 - 20) / Math.abs(directionX);
            float scaleY = Math.abs(directionY) < .0001f ? Float.MAX_VALUE : (height / 2 - 20) / Math.abs(directionY);
            float edge = Math.min(scaleX, scaleY);
            x = width / 2 + directionX * edge; y = height / 2 + directionY * edge;
            g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().rotate((float)Math.toRadians(angle));
            InterfaceAssets.icon(g, "gps_arrow", -5, -5, 10, color); g.pose().popMatrix();
        } else {
            float bobY = (float)Math.sin(now / 286_000_000d) * 2;
            InterfaceAssets.icon(g, "gps_pin", x - 6, y - 12 + bobY, 12, color);
        }
        float textWidth = FigmaFont.mediumWidth(distance, .55f), panelWidth = textWidth + 10;
        float labelX = Math.clamp(x - panelWidth / 2, 2, Math.max(2, width - panelWidth - 2));
        float labelY = Math.clamp(y + 8, 2, height - 14);
        Draw.roundedF(g, labelX, labelY, panelWidth, 12, 4, 0xcc171816);
        FigmaFont.drawMedium(g, distance, labelX + 5, labelY + 2, 0xfff1f3ef, .55f);
        if (name != null) {
            float nameWidth = FigmaFont.mediumWidth(name, .55f) + 10;
            float nameX = Math.clamp(x - nameWidth / 2, 2, Math.max(2, width - nameWidth - 2));
            float nameY = Math.min(height - 13, labelY + 14);
            Draw.roundedF(g, nameX, nameY, nameWidth, 12, 4, 0xcc381217);
            FigmaFont.drawMedium(g, name, nameX + 5, nameY + 2, color, .55f);
        }
    }
}
